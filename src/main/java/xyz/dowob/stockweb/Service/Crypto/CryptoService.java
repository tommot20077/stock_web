package xyz.dowob.stockweb.Service.Crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
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
import xyz.dowob.stockweb.Component.Event.Asset.ImmediateDataUpdateEvent;
import xyz.dowob.stockweb.Component.Event.Crypto.WebSocketConnectionStatusEvent;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Exception.RepositoryExceptions;
import xyz.dowob.stockweb.Model.Common.Task;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.TaskRepository;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Service.Common.DynamicThreadPoolService;
import xyz.dowob.stockweb.Service.Common.FileService;
import xyz.dowob.stockweb.Service.Common.ProgressTrackerService;
import xyz.dowob.stockweb.Service.Common.RedisService;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author yuan
 * 虛擬貨幣相關業務邏輯
 */
@Log4j2
@Service
public class CryptoService {
    private volatile boolean isRunning = false;

    private boolean isNeedToCheckConnection = false;

    private final CryptoWebSocketHandler cryptoWebSocketHandler;

    private final TaskRepository taskRepository;

    private final CryptoInfluxService cryptoInfluxService;

    private WebSocketConnectionManager connectionManager;

    private final CryptoRepository cryptoRepository;

    private final ObjectMapper objectMapper;

    private final FileService fileService;

    private final RedisService redisService;

    private final ProgressTrackerService progressTrackerService;

    private final DynamicThreadPoolService dynamicThreadPoolService;

    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${db.influxdb.bucket.crypto_history.detail:1d}")
    private String frequency;

    @Value("${db.influxdb.bucket.crypto_history.dateline:20180101}")
    private String dateline;

    @Value("${crypto.enable_auto_start}")
    private boolean enableAutoStart;

    @Value("${kafka.enable:false}")
    private boolean isKafkaEnable;

    private final String dataUrl = "https://data.binance.vision/?prefix=data/spot/";

    @SuppressWarnings("UnstableApiUsage")
    RateLimiter rateLimiter = RateLimiter.create(1.0);

    /**
     * CryptoService構造函數
     *
     * @param webSocketHandler          虛擬貨幣WebSocket處理器
     * @param taskRepository            任務數據庫
     * @param cryptoInfluxService       加密貨幣InfluxDB服務
     * @param connectionManager         WebSocket連接管理器
     * @param cryptoRepository          加密貨幣數據庫
     * @param objectMapper              JSON對象映射
     * @param fileService               文件服務
     * @param progressTrackerService    進度追踪器服務
     * @param dynamicThreadPoolService  動態線程池服務
     * @param applicationEventPublisher 應用事件發布者
     */
    public CryptoService(CryptoWebSocketHandler webSocketHandler, TaskRepository taskRepository, CryptoInfluxService cryptoInfluxService, WebSocketConnectionManager connectionManager, CryptoRepository cryptoRepository, ObjectMapper objectMapper, FileService fileService, RedisService redisService, ProgressTrackerService progressTrackerService, DynamicThreadPoolService dynamicThreadPoolService, ApplicationEventPublisher applicationEventPublisher) {
        this.cryptoWebSocketHandler = webSocketHandler;
        this.taskRepository = taskRepository;
        this.cryptoInfluxService = cryptoInfluxService;
        this.connectionManager = connectionManager;
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = objectMapper;
        this.fileService = fileService;
        this.redisService = redisService;
        this.progressTrackerService = progressTrackerService;
        this.dynamicThreadPoolService = dynamicThreadPoolService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 虛擬貨幣WebSocket連接狀態事件處理
     *
     * @param event WebSocket連接狀態事件
     */
    @EventListener
    public void handleWebSocketConnectionStatusEvent(WebSocketConnectionStatusEvent event) {
        isRunning = event.isConnected();
    }

    /**
     * 初始化是否啟動自動連線
     */
    @PostConstruct
    public void init() {
        try {
            if (enableAutoStart || isKafkaEnable) {
                openConnection();
                isRunning = true;
            }
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }

    /**
     * 檢查WebSocket連線是否開啟
     *
     * @return 是否開啟
     */
    public boolean isConnectionOpen() {
        return isRunning;
    }

    /**
     * 開啟WebSocket連線
     *
     * @throws IllegalStateException 當連線已經開啟時拋出
     */
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
        applicationEventPublisher.publishEvent(new ImmediateDataUpdateEvent(this, true, AssetType.CRYPTO));
    }

    /**
     * 關閉WebSocket連線
     *
     * @throws IllegalStateException 當連線已經關閉時拋出
     */
    public void closeConnection() throws IllegalStateException {
        if (isRunning) {
            isNeedToCheckConnection = false;
            connectionManager.stop();
        }
        applicationEventPublisher.publishEvent(new ImmediateDataUpdateEvent(this, false, AssetType.CRYPTO));
    }

    /**
     * 取消訂閱Websocket的交易對
     *
     * @param symbol  交易對
     * @param channel 補充訊息
     * @param user    使用者
     *
     * @throws Exception 當連線未開啟時拋出
     */
    public void unsubscribeTradingPair(String symbol, String channel, User user) throws Exception {
        if (!channel.contains("@")) {
            throw new IllegalStateException("channel格式錯誤");
        }
        cryptoWebSocketHandler.unsubscribeTradingPair(symbol, channel, user);
    }

    /**
     * 訂閱Websocket的交易對
     *
     * @param symbol  交易對
     * @param channel 補充訊息
     * @param user    使用者
     *
     * @throws Exception 當連線未開啟時拋出
     */
    public void subscribeTradingPair(String symbol, String channel, User user) throws Exception {
        if (!channel.contains("@")) {
            throw new IllegalStateException("channel格式錯誤");
        }
        cryptoWebSocketHandler.subscribeTradingPair(symbol, channel, user);
    }

    /**
     * 更新幣種交易對列表,並存入數據庫
     *
     * @throws RuntimeException 當更新失敗時拋出
     */
    @Async
    public void updateSymbolList() {
        CryptoTradingPair cryptoTradingPair;
        String cryptoListUrl = "https://api.binance.com/api/v3/exchangeInfo";
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(cryptoListUrl, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("symbols");
                if (dataArray.isArray()) {
                    for (JsonNode cryptoNode : dataArray) {
                        if ("TRADING".equals(cryptoNode.get("status").asText())) {
                            cryptoTradingPair = cryptoRepository.findByTradingPair(cryptoNode.get("symbol").asText())
                                                                .orElse(new CryptoTradingPair());
                            cryptoTradingPair.setBaseAsset(cryptoNode.get("baseAsset").asText());
                            cryptoTradingPair.setQuoteAsset(cryptoNode.get("quoteAsset").asText());
                            cryptoTradingPair.setTradingPair(cryptoNode.get("symbol").asText());
                            cryptoTradingPair.setAssetType(AssetType.CRYPTO);
                            cryptoRepository.save(cryptoTradingPair);
                        }
                    }
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

    /**
     * 檢查是否有太多請求
     * 當太多請求時，等待一段時間後再重新發送請求
     *
     * @param response HTTP響應
     *
     * @throws InterruptedException 當請求過多時拋出
     */
    public void tooManyRequest(HttpServletResponse response) throws InterruptedException {
        if (response.getStatus() == 429) {
            String retryAfter = response.getHeaders("Retry-After").toString();
            if (retryAfter != null) {
                int waitSeconds = Integer.parseInt(retryAfter);
                Thread.sleep(waitSeconds * 1000L);
                throw new InterruptedException("請求過多，請在" + retryAfter + "秒後再試");
            }
        }
    }

    /**
     * 取得所有交易對
     *
     * @return 交易對列表
     */
    public List<String> getAllTradingPairs() {
        List<CryptoTradingPair> tradingPairs = cryptoRepository.findAll();
        if (tradingPairs.isEmpty()) {
            return null;
        }
        return tradingPairs.stream().map(CryptoTradingPair::getTradingPair).toList();
    }

    /**
     * 取得伺服器交易對與訂閱數量
     *
     * @return 伺服器交易對列表
     *
     * @throws JsonProcessingException 當序列化失敗時拋出
     */
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

    /**
     * 檢查並重新連接WebSocket
     */
    public void checkAndReconnectWebSocket() {
        if (!isRunning) {
            openConnection();
        }
    }

    /**
     * 檢查是否需要檢查連線
     *
     * @return 是否需要檢查連線
     */
    public boolean isNeedToCheckConnection() {
        return isNeedToCheckConnection;
    }

    /**
     * 訂閱虛擬貨幣的歷史價格，並寫入InfluxDB
     * 利用CompletableFuture實現非阻塞式IO
     * 將任務Id返回給用戶用於查詢進度
     * 當任務完成時，發送事件通知
     *
     * @param cryptoTradingPair 虛擬貨幣交易對
     *
     * @return 任務ID
     *
     * @throws RuntimeException 當更新失敗時拋出
     */
    @Async
    public CompletableFuture<String> trackCryptoHistoryPrices(CryptoTradingPair cryptoTradingPair) {
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        dynamicThreadPoolService.adjustThreadPoolBasedOnLoad();
        ExecutorService executorService = dynamicThreadPoolService.getExecutorService();
        LocalDate todayDate = LocalDate.now(ZoneId.of("UTC"));
        final LocalDate[] endDate = {LocalDate.parse(dateline, DateTimeFormatter.BASIC_ISO_DATE)};
        final LocalDate[] getMonthlyDate = {todayDate.minusMonths(1)};
        final LocalDate[] getDailyDate = {todayDate.withDayOfMonth(1)};
        DateTimeFormatter monthlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter dailyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int trackDaysZip = getDateBetween(getDailyDate[0], todayDate, false);
        int trackMonthsZip = getDateBetween(endDate[0].withDayOfMonth(1), getMonthlyDate[0].withDayOfMonth(1), true);
        String taskId = progressTrackerService.createAndTrackNewTask(trackDaysZip + trackMonthsZip, cryptoTradingPair.getTradingPair());
        Task task = new Task(taskId, "獲取: " + cryptoTradingPair.getTradingPair() + " 的歷史價格", trackDaysZip + trackMonthsZip);
        taskRepository.save(task);
        try {
            processDate(getMonthlyDate,
                        endDate,
                        monthlyFormatter,
                        cryptoTradingPair.getTradingPair(),
                        "monthly",
                        taskId,
                        executorService,
                        futureList);
            endDate[0] = todayDate.plusDays(1);
            processDate(getDailyDate,
                        endDate,
                        dailyFormatter,
                        cryptoTradingPair.getTradingPair(),
                        "daily",
                        taskId,
                        executorService,
                        futureList);
            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).whenComplete((result, ex) -> {
                if (ex != null) {
                    task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + ex.getMessage());
                } else {
                    task.completeTask(TaskStatusType.SUCCESS, "歷史價格資料成功抓取，總計用時: " + task.getTaskUsageTime());
                }
                cryptoTradingPair.setHasAnySubscribed(true);
                cryptoRepository.save(cryptoTradingPair);
                taskRepository.save(task);
                progressTrackerService.deleteProgress(taskId);
                applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, true, cryptoTradingPair));
            });
            return CompletableFuture.completedFuture(taskId);
        } catch (Exception e) {
            task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + e.getMessage());
            taskRepository.save(task);
            progressTrackerService.deleteProgress(taskId);
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, false, cryptoTradingPair));
            return CompletableFuture.completedFuture(taskId);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    public void processDate(LocalDate[] startDate, LocalDate[] endDate, DateTimeFormatter dateTimeFormatter, String cryptoTradingPair, String period, String taskId, ExecutorService executorService, List<CompletableFuture<Void>> futureList) {
        while ((startDate[0].isAfter(endDate[0]) || startDate[0].isEqual(endDate[0]))) {
            String formatGetDate = startDate[0].format(dateTimeFormatter);
            String fileName = String.format("%s-%s-%s.zip", cryptoTradingPair, frequency, formatGetDate);
            String dataFormatUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", period, cryptoTradingPair, frequency, fileName);
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                dynamicThreadPoolService.onTaskStart();
                rateLimiter.acquire();
                List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(dataFormatUrl, fileName);
                if (csvData == null) {
                    if ("monthly".equals(period)) {
                        endDate[0] = startDate[0];
                    }
                } else {
                    cryptoInfluxService.writeCryptoHistoryToInflux(csvData, cryptoTradingPair);
                }
                progressTrackerService.incrementProgress(taskId);
            }, executorService).handle((result, throwable) -> {
                dynamicThreadPoolService.onTaskComplete();
                if (throwable != null) {
                    throw new RuntimeException(throwable);
                }
                return null;
            });
            futureList.add(future);
            if ("monthly".equals(period)) {
                startDate[0] = startDate[0].minusMonths(1);
            } else {
                startDate[0] = startDate[0].plusDays(1);
            }
        }
    }

    /**
     * 每天追蹤虛擬貨幣的歷史價格
     * 查詢influxdb最後更新日期，如果不是昨天，則需要更新
     * 並且檢查是否有缺失的歷史價格資料，如果有則補齊
     * 使用CompletableFuture實現非阻塞式IO
     *
     * @throws RuntimeException 當更新失敗時拋出
     */
    @Async
    @SuppressWarnings("UnstableApiUsage")
    public void trackCryptoHistoryPricesWithUpdateDaily() throws RuntimeException {
        Set<CryptoTradingPair> needToUpdateTradingPairs = cryptoRepository.findAllTradingPairBySubscribers();
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        Map<CryptoTradingPair, LocalDate> hadTrackHistoryData = new HashMap<>();
        if (needToUpdateTradingPairs.isEmpty()) {
            return;
        }
        LocalDate endDate = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        String formatEndDate = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalDate expectedLastUpdateDate = endDate.minusDays(1);
        ExecutorService executorService = dynamicThreadPoolService.getExecutorService();
        String taskId = progressTrackerService.createAndTrackNewTask(needToUpdateTradingPairs.size(), "dailyUpdateCryptoHistoryPrices");
        Task task = new Task(taskId, "更新每日虛擬貨幣的最新價格", needToUpdateTradingPairs.size());
        taskRepository.save(task);
        try {
            needToUpdateTradingPairs.forEach(tradingPair -> {
                LocalDate lastUpdateDate = cryptoInfluxService.getLastDateByTradingPair(tradingPair.getTradingPair());
                if (!lastUpdateDate.equals(expectedLastUpdateDate)) {
                    hadTrackHistoryData.put(tradingPair, lastUpdateDate);
                }
                String fileName = String.format("%s-%s-%s.zip", tradingPair.getTradingPair(), frequency, formatEndDate);
                String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", "daily", tradingPair.getTradingPair(), frequency, fileName);
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    dynamicThreadPoolService.onTaskStart();
                    rateLimiter.acquire();
                    List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(dailyUrl, fileName);
                    if (csvData != null) {
                        cryptoInfluxService.writeCryptoHistoryToInflux(csvData, tradingPair.getTradingPair());
                    }
                    progressTrackerService.incrementProgress(taskId);
                }, executorService).handle((result, throwable) -> {
                    dynamicThreadPoolService.onTaskComplete();
                    if (throwable != null) {
                        throw new RuntimeException(throwable);
                    }
                    return null;
                });
                futureList.add(future);
            });
            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).whenCompleteAsync((result, ex) -> {
                if (ex != null) {
                    task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + ex.getMessage());
                } else {
                    task.completeTask(TaskStatusType.SUCCESS, "歷史價格資料成功抓取，總計用時: " + task.getTaskUsageTime());
                }
                taskRepository.save(task);
                progressTrackerService.deleteProgress(taskId);
                if (!hadTrackHistoryData.isEmpty()) {
                    int totalBackFillCount = 0;
                    for (Map.Entry<CryptoTradingPair, LocalDate> entry : hadTrackHistoryData.entrySet()) {
                        LocalDate lastUpdateDate = entry.getValue();
                        LocalDate trackStartDate = lastUpdateDate.plusDays(1);
                        totalBackFillCount += Period.between(trackStartDate, endDate).getDays();
                    }
                    String trackBackTaskId = progressTrackerService.createAndTrackNewTask(totalBackFillCount, "trackMissHistoryPriceData");
                    Task trackBackTask = new Task(taskId, "追蹤缺失的虛擬貨幣的價格資料", totalBackFillCount);
                    taskRepository.save(trackBackTask);
                    try {
                        hadTrackHistoryData.forEach((tradingPair, lastUpdateDate) -> {
                            LocalDate trackStartDate = lastUpdateDate.plusDays(1);
                            while (trackStartDate.isBefore(expectedLastUpdateDate) || trackStartDate.isEqual(expectedLastUpdateDate)) {
                                String formatTrackStartDate = trackStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                String fileName = String.format("%s-%s-%s.zip",
                                                                tradingPair.getTradingPair(),
                                                                frequency,
                                                                formatTrackStartDate);
                                String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s",
                                                                "daily",
                                                                tradingPair.getTradingPair(),
                                                                frequency,
                                                                fileName);
                                dynamicThreadPoolService.onTaskStart();
                                rateLimiter.acquire();
                                try {
                                    List<String[]> csvData = fileService.downloadFileAndUnzipAndRead(dailyUrl, fileName);
                                    if (csvData != null) {
                                        cryptoInfluxService.writeCryptoHistoryToInflux(csvData, tradingPair.getTradingPair());
                                    }
                                } finally {
                                    progressTrackerService.incrementProgress(trackBackTaskId);
                                }
                                dynamicThreadPoolService.onTaskComplete();
                                trackStartDate = trackStartDate.plusDays(1);
                            }
                            trackBackTask.completeTask(TaskStatusType.SUCCESS, "歷史價格資料成功抓取，總計用時: " + task.getTaskUsageTime());
                            taskRepository.save(trackBackTask);
                            progressTrackerService.deleteProgress(trackBackTaskId);
                            String deletePattern = String.format("kline_history_%s:data", tradingPair.getId());
                            try {
                                redisService.deleteByPattern(deletePattern);
                            } catch (RepositoryExceptions e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (Exception e) {
                        trackBackTask.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + e.getMessage());
                        taskRepository.save(trackBackTask);
                        progressTrackerService.deleteProgress(trackBackTaskId);
                    }
                }
            });
        } catch (Exception e) {
            task.completeTask(TaskStatusType.FAILED, "歷史價格資料抓取失敗: " + e.getMessage());
            taskRepository.save(task);
            progressTrackerService.deleteProgress(taskId);
        }
    }

    /**
     * 刪除虛擬貨幣的歷史價格
     *
     * @param tradingPair 交易對
     *
     * @throws RuntimeException 當刪除失敗時拋出
     */
    public void removeCryptoPricesDataByTradingPair(String tradingPair) throws RuntimeException {
        try {
            cryptoInfluxService.deleteDataByTradingPair(tradingPair);
        } catch (Exception e) {
            throw new RuntimeException("刪除歷史價格的交易對失敗: " + tradingPair, e);
        }
    }

    /**
     * 取得交易對實體
     *
     * @param tradingPair 交易對
     *
     * @return 交易對實體
     */
    public CryptoTradingPair getCryptoTradingPair(String tradingPair) {
        return cryptoRepository.findByTradingPair(tradingPair).orElseThrow(() -> new RuntimeException("找不到交易對: " + tradingPair));
    }

    /**
     * 取得日期區間內的日期間隔
     *
     * @param start 開始日期
     * @param end   結束日期
     * @param month 是否以月為單位
     *
     * @return 日期數量
     */
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
