package xyz.dowob.stockweb.Service.Crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import xyz.dowob.stockweb.Component.Event.Asset.AssetHistoryDataFetchCompleteEvent;
import xyz.dowob.stockweb.Component.Event.Crypto.WebSocketConnectionStatusEvent;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Model.Common.Task;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.TaskRepository;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Service.Common.DynamicThreadPoolManager;
import xyz.dowob.stockweb.Service.Common.FileService;
import xyz.dowob.stockweb.Service.Common.ProgressTracker;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author yuan
 */
@Service
public class CryptoService {

    Logger logger = LoggerFactory.getLogger(CryptoService.class);
    private volatile boolean isRunning = false;
    private boolean isNeedToCheckConnection = false;
    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    private final TaskRepository taskRepository;
    private final CryptoInfluxService cryptoInfluxService;
    private WebSocketConnectionManager connectionManager;
    private final CryptoRepository cryptoRepository;
    private final ObjectMapper objectMapper;
    private final FileService fileService;
    private final ProgressTracker progressTracker;
    private final DynamicThreadPoolManager dynamicThreadPoolManager;
    private final ApplicationEventPublisher applicationEventPublisher;


    @Value("${db.influxdb.bucket.crypto_history.detail}")
    private String frequency;
    @Value("${db.influxdb.bucket.crypto_history.dateline}")
    private String dateline;
    private final String dataUrl = "https://data.binance.vision/data/spot/";
    RateLimiter rateLimiter = RateLimiter.create(1.0);


    @Autowired
    public CryptoService(CryptoWebSocketHandler webSocketHandler, TaskRepository taskRepository, CryptoInfluxService cryptoInfluxService, WebSocketConnectionManager connectionManager, CryptoRepository cryptoRepository, ObjectMapper objectMapper, FileService fileService, ProgressTracker progressTracker, DynamicThreadPoolManager dynamicThreadPoolManager, ApplicationEventPublisher applicationEventPublisher) {
        this.cryptoWebSocketHandler = webSocketHandler;
        this.taskRepository = taskRepository;
        this.cryptoInfluxService = cryptoInfluxService;
        this.connectionManager = connectionManager;
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = objectMapper;
        this.fileService = fileService;
        this.progressTracker = progressTracker;
        this.dynamicThreadPoolManager = dynamicThreadPoolManager;
        this.applicationEventPublisher = applicationEventPublisher;
    }


    @EventListener
    public void handleWebSocketConnectionStatusEvent(WebSocketConnectionStatusEvent event) {
        isRunning = event.isConnected();
    }

    public boolean isConnectionOpen() {
        return isRunning;
    }

    public void openConnection() throws IllegalStateException {
        if (isRunning) {
            throw new IllegalStateException("已經開啟連線");
        }
        isNeedToCheckConnection = true;
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        connectionManager = new WebSocketConnectionManager(webSocketClient,
                                                           cryptoWebSocketHandler,
                                                           "wss://stream.binance.com:9443/stream?streams=");
        connectionManager.setAutoStartup(true);
        connectionManager.start();
    }

    public void closeConnection() throws IllegalStateException {
        if (!isRunning) {
            logger.warn("目前沒有開啟的連線");
        } else {
            isNeedToCheckConnection = false;
            connectionManager.stop();
        }
    }


    public void unsubscribeTradingPair(String symbol, String channel, User user) throws Exception {
        if (!this.isConnectionOpen()) {
            logger.warn("目前沒有啟動連線");
        }
        if (!channel.contains("@")) {
            logger.warn("channel格式錯誤");
            throw new IllegalStateException("channel格式錯誤");
        }
        cryptoWebSocketHandler.unsubscribeTradingPair(symbol, channel, user);
    }

    public void subscribeTradingPair(String symbol, String channel, User user) throws Exception {
        if (!this.isConnectionOpen()) {
            logger.warn("目前沒有啟動連線");
        }
        if (!channel.contains("@")) {
            logger.warn("channel格式錯誤");
            throw new IllegalStateException("channel格式錯誤");
        }
        cryptoWebSocketHandler.subscribeTradingPair(symbol, channel, user);
    }

    @Async
    public void updateSymbolList() {
        CryptoTradingPair cryptoTradingPair;
        String cryptoListUrl = "https://api.binance.com/api/v3/exchangeInfo";
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(cryptoListUrl, String.class);
        logger.debug("更新幣種交易對列表: " + response.getBody());
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("symbols");
                logger.debug("共有交易對數量: " + dataArray.size());
                if (dataArray.isArray()) {
                    logger.debug("陣列資料: " + dataArray);
                    for (JsonNode cryptoNode : dataArray) {
                        if ("TRADING".equals(cryptoNode.get("status").asText())) {
                            cryptoTradingPair = cryptoRepository.findByTradingPair(cryptoNode.get("symbol").asText())
                                                                .orElse(new CryptoTradingPair());
                            logger.debug("交易對象幣種: " + cryptoNode.get("baseAsset").asText());
                            cryptoTradingPair.setBaseAsset(cryptoNode.get("baseAsset").asText());
                            logger.debug("交易基準幣種: " + cryptoNode.get("quoteAsset").asText());
                            cryptoTradingPair.setQuoteAsset(cryptoNode.get("quoteAsset").asText());
                            logger.debug("更新交易對: " + cryptoNode.get("symbol").asText());
                            cryptoTradingPair.setTradingPair(cryptoNode.get("symbol").asText());
                            cryptoTradingPair.setAssetType(AssetType.CRYPTO);
                            cryptoRepository.save(cryptoTradingPair);
                        }
                    }
                    logger.info("更新幣種交易對列表成功");
                } else {
                    throw new RuntimeException("無法解析幣種列表");
                }
            } catch (Exception e) {
                throw new RuntimeException("更新幣種列表失敗: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("更新幣種列表失敗");
        }
    }

    public void tooManyRequest(HttpServletResponse response) throws InterruptedException {
        if (response.getStatus() == 429) {
            logger.warn("WebSocket連線過多");
            String retryAfter = response.getHeaders("Retry-After").toString();
            if (retryAfter != null) {
                logger.warn("請求過於頻繁，請在" + retryAfter + "秒後再試");
                int waitSeconds = Integer.parseInt(retryAfter);
                Thread.sleep(waitSeconds * 1000L);
                throw new InterruptedException("請求過多，請在" + retryAfter + "秒後再試");
            }
        }
    }

    public List<String> getAllTradingPairs() {
        List<CryptoTradingPair> tradingPairs = cryptoRepository.findAll();
        if (tradingPairs.isEmpty()) {
            return null;
        }
        return tradingPairs.stream().map(CryptoTradingPair::getTradingPair).toList();
    }

    public String getServerTradingPairs() throws JsonProcessingException {
        List<CryptoTradingPair> tradingPairs = cryptoRepository.findAll();
        if (tradingPairs.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> tradingPairList = tradingPairs.stream().map(tp -> {
            Map<String, Object> map = new HashMap<>();
            map.put("subscribeNumber", cryptoRepository.countCryptoSubscribersNumber(tp));
            map.put("tradingPair", tp.getTradingPair());
            return map;
        }).toList();

        return objectMapper.writeValueAsString(tradingPairList);
    }

    public void checkAndReconnectWebSocket() {
        if (!isRunning) {
            try {
                logger.info("開啟自動重連WebSocket: " + isNeedToCheckConnection + " ，正在重新連線...");
                openConnection();
                logger.info("重新連線成功");
            } catch (Exception e) {
                logger.error("重新連線失敗: " + e.getMessage());
            }
        }
    }

    public boolean isNeedToCheckConnection() {
        return isNeedToCheckConnection;
    }

    @Async
    public CompletableFuture<String> trackCryptoHistoryPrices(CryptoTradingPair cryptoTradingPair) {
        dynamicThreadPoolManager.adjustThreadPoolBasedOnLoad();
        ExecutorService executorService = dynamicThreadPoolManager.getExecutorService();
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        int currentCorePoolSize = dynamicThreadPoolManager.getCurrentCorePoolSize();
        logger.debug("任務線程數: " + currentCorePoolSize + "個線程");

        final LocalDate[] endDate = {LocalDate.parse(dateline, DateTimeFormatter.BASIC_ISO_DATE)};
        LocalDate todayDate = LocalDate.now(ZoneId.of("UTC"));
        final LocalDate[] getMonthlyDate = {todayDate.minusMonths(2)};
        final LocalDate[] getDailyDate = {todayDate.minusMonths(1).withDayOfMonth(1)};
        DateTimeFormatter monthlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");


        int trackDaysZip = getDateBetween(getDailyDate[0], todayDate, false);
        int trackMonthsZip = getDateBetween(endDate[0].withDayOfMonth(1), getMonthlyDate[0].withDayOfMonth(1), true);

        logger.debug("總計需取得: " + (trackDaysZip + trackMonthsZip) + "筆資料");
        String taskId = progressTracker.createAndTrackNewTask(trackDaysZip + trackMonthsZip, cryptoTradingPair.getTradingPair());
        logger.debug("本次任務Id: " + taskId);
        Task task = new Task(taskId, "獲取: " + cryptoTradingPair.getTradingPair() + " 的歷史價格", trackDaysZip + trackMonthsZip);
        taskRepository.save(task);
        try {
            while ((getMonthlyDate[0].isAfter(endDate[0]) || getMonthlyDate[0].isEqual(endDate[0]))) {
                String formatGetMonthlyDate = getMonthlyDate[0].format(monthlyFormatter);
                String fileName = String.format("%s-%s-%s.zip", cryptoTradingPair.getTradingPair(), frequency, formatGetMonthlyDate);
                String monthlyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s",
                                                  "monthly",
                                                  cryptoTradingPair.getTradingPair(),
                                                  frequency,
                                                  fileName);
                logger.debug("要追蹤歷史價格的交易對: " + cryptoTradingPair.getTradingPair());
                logger.debug("歷史價格資料網址: " + monthlyUrl);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    dynamicThreadPoolManager.onTaskStart();
                    rateLimiter.acquire();
                    List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(monthlyUrl, fileName);
                    if (csvData == null) {
                        logger.debug("資料讀取失敗");
                        endDate[0] = getMonthlyDate[0];
                    } else {
                        logger.debug("資料讀取完成");
                        cryptoInfluxService.writeCryptoHistoryToInflux(csvData, cryptoTradingPair.getTradingPair());
                        logger.debug("抓取" + fileName + "的資料完成");
                    }
                    progressTracker.incrementProgress(taskId);
                }, executorService).handle((result, throwable) -> {
                    dynamicThreadPoolManager.onTaskComplete();
                    if (throwable != null) {
                        logger.error("線程執行失敗，執行檔名: " + fileName + ", 錯誤訊息: " + throwable.getMessage());
                    }
                    return null;
                });
                futureList.add(future);
                getMonthlyDate[0] = getMonthlyDate[0].minusMonths(1);
            }

            endDate[0] = todayDate.minusDays(1);
            while (getDailyDate[0].isBefore(endDate[0]) || getDailyDate[0].isEqual(endDate[0])) {
                String formatGetDailyDate = getDailyDate[0].format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String fileName = String.format("%s-%s-%s.zip", cryptoTradingPair.getTradingPair(), frequency, formatGetDailyDate);
                String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s",
                                                "daily",
                                                cryptoTradingPair.getTradingPair(),
                                                frequency,
                                                fileName);
                logger.debug("要追蹤歷史價格的交易對: " + cryptoTradingPair.getTradingPair());
                logger.debug("歷史價格資料網址: " + dailyUrl);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    dynamicThreadPoolManager.onTaskStart();
                    rateLimiter.acquire();
                    List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(dailyUrl, fileName);
                    if (csvData == null) {
                        logger.debug("資料讀取失敗: " + fileName);
                    } else {
                        logger.debug("資料讀取完成");
                        cryptoInfluxService.writeCryptoHistoryToInflux(csvData, cryptoTradingPair.getTradingPair());
                        getDailyDate[0] = getDailyDate[0].plusDays(1);
                        logger.debug("抓取" + fileName + "的資料完成");
                    }
                    progressTracker.incrementProgress(taskId);
                }, executorService).handle((result, throwable) -> {
                    dynamicThreadPoolManager.onTaskComplete();
                    if (throwable != null) {
                        logger.error("線程執行失敗，執行檔名: " + fileName + ", 錯誤訊息: " + throwable.getMessage());
                    }
                    return null;
                });
                futureList.add(future);
                getDailyDate[0] = getDailyDate[0].plusDays(1);
            }
            logger.debug("歷史價格資料抓取完成");

            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("歷史價格資料抓取失敗: " + ex.getMessage());
                    task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + ex.getMessage());
                } else {
                    logger.debug("歷史價格資料抓取完成");
                    task.completeTask(TaskStatusType.SUCCESS, "歷史價格資料成功抓取，總計用時: " + task.getTaskUsageTime());
                }
                cryptoTradingPair.setHasAnySubscribed(true);
                cryptoRepository.save(cryptoTradingPair);
                taskRepository.save(task);
                progressTracker.deleteProgress(taskId);
                logger.debug("任務用時: " + task.getTaskUsageTime());

                if (dynamicThreadPoolManager.getActiveTasks().get() <= 0) {
                    dynamicThreadPoolManager.shutdown(5, TimeUnit.SECONDS);
                }
                applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, true, cryptoTradingPair));
            });
            return CompletableFuture.completedFuture(taskId);
        } catch (Exception e) {
            logger.error("歷史價格資料抓取失敗: " + e.getMessage());
            task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + e.getMessage());
            taskRepository.save(task);
            progressTracker.deleteProgress(taskId);
            if (dynamicThreadPoolManager.getActiveTasks().get() <= 0) {
                dynamicThreadPoolManager.shutdown(5, TimeUnit.SECONDS);
            }
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, false, cryptoTradingPair));
            return CompletableFuture.completedFuture(taskId);
        }
    }

    @Async
    public void trackCryptoHistoryPricesWithUpdateDaily() {
        Set<String> needToUpdateTradingPairs = cryptoRepository.findAllTradingPairBySubscribers();
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        Map<String, LocalDate> hadTrackHistoryData = new HashMap<>();
        logger.debug("要更新每日最新價格的交易對: " + needToUpdateTradingPairs);
        if (needToUpdateTradingPairs.isEmpty()) {
            logger.debug("沒有要更新的交易對");
            return;
        }
        LocalDate startDate = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        LocalDate expectedLastUpdateDate = startDate.minusDays(1);
        String formatGetDailyDate = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        ExecutorService executorService = dynamicThreadPoolManager.getExecutorService();
        logger.debug("總計需取得: " + needToUpdateTradingPairs.size() + "筆資料");
        String taskId = progressTracker.createAndTrackNewTask(needToUpdateTradingPairs.size(), "dailyUpdateCryptoHistoryPrices");
        logger.debug("本次任務Id: " + taskId);
        Task task = new Task(taskId, "更新每日虛擬貨幣的最新價格", needToUpdateTradingPairs.size());
        taskRepository.save(task);
        try {
            needToUpdateTradingPairs.forEach(tradingPair -> {
                LocalDate lastUpdateDate = cryptoInfluxService.getLastDateByTradingPair(tradingPair);
                if (!lastUpdateDate.equals(expectedLastUpdateDate)) {
                    logger.warn("歷史價格資料交易對: " + tradingPair + ", 資料有缺失，最後更新日期: " + lastUpdateDate + " 加入到待處理列表");
                    hadTrackHistoryData.put(tradingPair, lastUpdateDate);
                }

                String fileName = String.format("%s-%s-%s.zip", tradingPair, frequency, formatGetDailyDate);
                String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", "daily", tradingPair, frequency, fileName);
                logger.debug("要追蹤歷史價格的交易對: " + tradingPair);
                logger.debug("歷史價格資料網址: " + dailyUrl);


                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    dynamicThreadPoolManager.onTaskStart();
                    rateLimiter.acquire();
                    try {
                        List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(dailyUrl, fileName);
                        if (csvData != null) {
                            logger.debug("歷史價格資料讀取完成");
                            cryptoInfluxService.writeCryptoHistoryToInflux(csvData, tradingPair);
                            logger.debug("抓取" + fileName + "的資料完成");
                        }
                    } finally {
                        progressTracker.incrementProgress(taskId);
                    }
                }, executorService).handle((result, throwable) -> {
                    dynamicThreadPoolManager.onTaskComplete();
                    if (throwable != null) {
                        logger.error("線程執行失敗，執行檔名: " + fileName + ", 錯誤訊息: " + throwable.getMessage());
                    }
                    return null;
                });
                futureList.add(future);
            });

            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).whenCompleteAsync((result, ex) -> {
                if (ex != null) {
                    logger.error("歷史價格資料抓取失敗: " + ex.getMessage());
                    task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + ex.getMessage());
                } else {
                    logger.debug("歷史價格資料抓取完成");
                    task.completeTask(TaskStatusType.SUCCESS, "歷史價格資料成功抓取，總計用時: " + task.getTaskUsageTime());
                }
                taskRepository.save(task);
                progressTracker.deleteProgress(taskId);
                logger.debug("任務用時: " + task.getTaskUsageTime());

                if (!hadTrackHistoryData.isEmpty()) {
                    logger.warn("歷史價格資料有缺失，開始處理");
                    int totalBackFillCount = 0;
                    for (Map.Entry<String, LocalDate> entry : hadTrackHistoryData.entrySet()) {
                        LocalDate lastUpdateDate = entry.getValue();
                        LocalDate trackStartDate = lastUpdateDate.plusDays(1);
                        totalBackFillCount += Period.between(trackStartDate, startDate).getDays();
                    }
                    logger.debug("總計需取得: " + totalBackFillCount + "筆資料");
                    String trackBackTaskId = progressTracker.createAndTrackNewTask(totalBackFillCount, "trackMissHistoryPriceData");
                    Task trackBackTask = new Task(taskId, "追蹤缺失的虛擬貨幣的價格資料", totalBackFillCount);
                    taskRepository.save(trackBackTask);

                    try {
                        hadTrackHistoryData.forEach((tradingPair, lastUpdateDate) -> {
                            logger.warn("歷史價格資料交易對: " + tradingPair + ", 資料有缺失，最後更新日期: " + lastUpdateDate);
                            LocalDate trackStartDate = lastUpdateDate.plusDays(1);

                            while (trackStartDate.isBefore(expectedLastUpdateDate) || trackStartDate.isEqual(expectedLastUpdateDate)) {
                                String formatTrackStartDate = trackStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                String fileName = String.format("%s-%s-%s.zip", tradingPair, frequency, formatTrackStartDate);
                                String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", "daily", tradingPair, frequency, fileName);
                                logger.debug("要追蹤歷史價格的交易對: " + tradingPair);
                                logger.debug("歷史價格資料網址: " + dailyUrl);
                                dynamicThreadPoolManager.onTaskStart();
                                rateLimiter.acquire();
                                try {
                                    List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(dailyUrl, fileName);
                                    if (csvData != null) {
                                        logger.debug("歷史價格資料讀取完成");
                                        cryptoInfluxService.writeCryptoHistoryToInflux(csvData, tradingPair);
                                        logger.debug("抓取" + fileName + "的資料完成");
                                    }
                                } catch (Exception e) {
                                    logger.error("抓取" + fileName + "的資料失敗: " + e.getMessage());
                                } finally {
                                    progressTracker.incrementProgress(trackBackTaskId);
                                }
                                dynamicThreadPoolManager.onTaskComplete();
                                trackStartDate = trackStartDate.plusDays(1);
                            }
                            logger.debug("歷史價格資料抓取完成");
                            trackBackTask.completeTask(TaskStatusType.SUCCESS, "歷史價格資料成功抓取，總計用時: " + task.getTaskUsageTime());
                            taskRepository.save(trackBackTask);
                            progressTracker.deleteProgress(trackBackTaskId);
                            logger.debug("任務用時: " + task.getTaskUsageTime());
                        });
                    } catch (Exception e) {
                        logger.error("歷史價格資料抓取失敗: " + e.getMessage());
                        trackBackTask.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + e.getMessage());
                        taskRepository.save(trackBackTask);
                        progressTracker.deleteProgress(trackBackTaskId);
                        logger.debug("任務用時: " + task.getTaskUsageTime());
                    }
                }
            });
        } catch (Exception e) {
            logger.error("歷史價格資料抓取失敗: " + e.getMessage());
            task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + e.getMessage());
            taskRepository.save(task);
            progressTracker.deleteProgress(taskId);
            logger.debug("任務用時: " + task.getTaskUsageTime());
        } finally {
            if (dynamicThreadPoolManager.getActiveTasks().get() <= 0) {
                dynamicThreadPoolManager.shutdown(5, TimeUnit.SECONDS);
            }
        }
    }


    public void removeCryptoPricesDataByTradingPair(String tradingPair) throws RuntimeException {
        try {
            logger.debug("要刪除歷史價格的交易對: " + tradingPair);
            cryptoInfluxService.deleteDataByTradingPair(tradingPair);
            logger.debug("刪除歷史價格的交易對成功: " + tradingPair);
        } catch (Exception e) {
            logger.error("刪除歷史價格的交易對失敗: " + tradingPair, e);
            throw new RuntimeException("刪除歷史價格的交易對失敗: " + tradingPair, e);
        }
    }

    public CryptoTradingPair getCryptoTradingPair(String tradingPair) {
        return cryptoRepository.findByTradingPair(tradingPair).orElseThrow(() -> new RuntimeException("找不到交易對: " + tradingPair));
    }

    public int getDateBetween(LocalDate start, LocalDate end, boolean month) {
        List<LocalDate> result = new ArrayList<>();
        while (start.isBefore(end) || start.isEqual(end)) {
            result.add(start);
            if (month) {
                start = start.plusMonths(1);
            } else {
                start = start.plusDays(1);
            }
        }
        return result.size();
    }
}
