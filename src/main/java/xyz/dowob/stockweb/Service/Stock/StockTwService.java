package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Component.Event.Asset.AssetHistoryDataFetchCompleteEvent;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Model.Common.Task;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.TaskRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author yuan
 */
@Service
public class StockTwService {
    private final StockTwRepository stockTwRepository;
    private final SubscribeRepository subscribeRepository;
    private final StockTwInfluxDBService stockTwInfluxDBService;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final String stockListUrl = "https://api.finmindtrade.com/api/v4/data?";
    private final String stockCurrentPriceUrl = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=";
    RateLimiter rateLimiter = RateLimiter.create(0.5);

    @Value(value = "${stock_tw.finmind.token}")
    private String finMindToken;

    @Value(value = "${db.influxdb.bucket.stock_tw_history.dateline}")
    private String stockTwHistoryDateline;

    private final Logger logger = LoggerFactory.getLogger(StockTwService.class);

    @Autowired
    public StockTwService(StockTwRepository stockTwRepository, SubscribeRepository subscribeRepository, StockTwInfluxDBService stockTwInfluxService, TaskRepository taskRepository, ObjectMapper objectMapper, ApplicationEventPublisher applicationEventPublisher) {
        this.stockTwRepository = stockTwRepository;
        this.subscribeRepository = subscribeRepository;
        this.stockTwInfluxDBService = stockTwInfluxService;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void addStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼"));
        Long assetId = stock.getId();

        if (subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).isPresent()) {
            throw new RuntimeException("已經訂閱過此股票");
        }

        if (!stock.checkUserIsSubscriber(user)) {
            stock.getSubscribers().add(user.getId());
            logger.debug("訂閱成功");
        }
        stock.setAssetType(AssetType.STOCK_TW);
        stockTwRepository.save(stock);
        logger.debug("用戶主動訂閱，此訂閱設定可刪除");
        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(stock);
        subscribe.setUserSubscribed(true);
        subscribe.setRemoveAble(true);
        subscribeRepository.save(subscribe);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId)
                                         .orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼: " + stockId));
        Long assetId = stock.getId();
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId)
                                                 .orElseThrow(() -> new RuntimeException("沒有找到指定的訂閱"));
        if (subscribe.isUserSubscribed()) {
            if (subscribe.isRemoveAble()) {
                if (stock.checkUserIsSubscriber(user)) {
                    stock.getSubscribers().remove(user.getId());
                    logger.debug("取消訂閱成功");
                }
            } else {
                logger.warn("此訂閱: " + stock.getStockCode() + " 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
                throw new RuntimeException("此訂閱: " + stock.getStockCode() + " 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
            }
            stockTwRepository.save(stock);
            subscribeRepository.delete(subscribe);
        }
    }

    @Transactional
    @Async
    public void updateStockList() {
        StockTw stock;
        String url = stockListUrl + "dataset=TaiwanStockInfo&stock_id=&token=" + finMindToken;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        logger.debug("更新股票列表: " + response.getBody());
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("data");
                logger.debug("股票數量: " + dataArray.size());
                if (dataArray.isArray()) {
                    logger.debug("陣列資料: " + dataArray);
                    for (JsonNode stockNode : dataArray) {
                        stock = stockTwRepository.findByStockCode(stockNode.get("stock_id").asText()).orElse(new StockTw());
                        logger.debug("更新股票: " + stockNode.get("stock_id").asText());
                        stock.setStockCode(stockNode.get("stock_id").asText());
                        stock.setStockName(stockNode.get("stock_name").asText());
                        stock.setIndustryCategory(stockNode.get("industry_category").asText());
                        stock.setStockType(stockNode.get("type").asText());
                        stock.setUpdateTime(formatStringToDate(stockNode.get("date").asText()));
                        stock.setAssetType(AssetType.STOCK_TW);
                        stockTwRepository.save(stock);
                    }
                } else {
                    throw new RuntimeException("無法解析股票列表");
                }
            } catch (Exception e) {
                throw new RuntimeException("更新股票列表失敗");
            }
        } else {
            throw new RuntimeException("更新股票列表失敗");
        }
    }

    public Map<String, List<String>> checkSubscriptionValidity() {
        Set<Object[]> subscribeList = stockTwRepository.findAllStockCodeAndTypeBySubscribers();
        List<String> checkSuccessList = new ArrayList<>();
        List<String> checkFailList = new ArrayList<>();
        List<String> inquiryList = new ArrayList<>();
        subscribeList.forEach(subscribe -> {
            String url;
            String inquiry;
            String stockCode = subscribe[0].toString();
            String stockType = subscribe[1].toString();
            if ("twse".equals(stockType)) {
                inquiry = "tse_" + stockCode + ".tw";
                url = stockCurrentPriceUrl + inquiry;
            } else if ("otc".equals(stockType)) {
                inquiry = "otc_" + stockCode + ".tw";
                url = stockCurrentPriceUrl + inquiry;
            } else {
                logger.warn("暫時不支援此類型: " + stockCode + "-" + stockType);
                return;
            }
            logger.debug("查詢股票: " + stockCode + " " + stockType);
            logger.debug("查詢股票網址: " + url);

            JsonNode rootNode = getJsonNodeByUrl(url);
            JsonNode msgArray = rootNode.path("msgArray");
            if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
                logger.debug("回應訊息成功");
                checkSuccessList.add((String) subscribe[0]);
                inquiryList.add(inquiry);
            } else {
                logger.debug("回應訊息失敗");
                checkFailList.add((String) subscribe[0]);
            }
            rateLimiter.acquire();
        });


        Map<String, List<String>> result = new HashMap<>();
        result.put("success", checkSuccessList);
        result.put("fail", checkFailList);
        result.put("inquiry", inquiryList);

        return result;
    }

    @Async
    public void trackStockNowPrices(List<String> stockInquiryList) {
        final StringBuilder inquireUrl = new StringBuilder(stockCurrentPriceUrl);
        logger.debug("要追蹤價格的股票: " + stockInquiryList);
        stockInquiryList.forEach(stockInquiry -> inquireUrl.append(stockInquiry).append("|"));
        logger.debug("拼接查詢網址: " + inquireUrl);
        JsonNode rootNode = getJsonNodeByUrl(inquireUrl.toString());
        JsonNode msgArray = rootNode.path("msgArray");
        if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
            logger.debug("開始寫入到Influxdb");
            stockTwInfluxDBService.writeStockTwToInflux(msgArray);
        }
    }

    @Async
    public void trackStockTwHistoryPrices(StockTw stockTw) {
        boolean rollbackChance = true;
        boolean hasData = true;
        boolean neverHasData = true;

        LocalDate endDate = LocalDate.parse(stockTwHistoryDateline, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Task task = new Task(UUID.randomUUID().toString(), "獲取" + stockTw.getStockCode() + "歷史資料", 1);
        taskRepository.save(task);
        try {
            while (hasData && (date.isAfter(endDate) || date.isEqual(endDate))) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM01");
                String formattedDate = date.format(formatter);
                String url = String.format("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=%s&stockNo=%s",
                                           formattedDate,
                                           stockTw.getStockCode());
                logger.debug("要追蹤價格的股票: " + stockTw.getStockCode());
                logger.debug("查詢網址: " + url);

                rateLimiter.acquire();
                JsonNode rootNode = getJsonNodeByUrl(url);
                int total = rootNode.path("total").asInt();
                if (total > 0) {
                    logger.debug("股票:" + stockTw.getStockCode() + " 在時間: " + rootNode.path("date") + " 有資料，開始處理資料");
                    ArrayNode dataArray = (ArrayNode) rootNode.path("data");
                    stockTwInfluxDBService.writeStockTwHistoryToInflux(dataArray, stockTw.getStockCode());
                    date = date.minusMonths(1);
                    neverHasData = false;
                } else {
                    logger.debug("股票:" + stockTw.getStockCode() + " 在時間: " + rootNode.path("date") + " 沒有資料");
                    if (neverHasData && !rollbackChance) {
                        throw new Exception("股票:" + stockTw.getStockCode() + " 沒有資料");
                    } else if (rollbackChance) {
                        rollbackChance = false;
                        date = date.minusMonths(1);
                    } else {
                        hasData = false;
                    }
                }
            }
            logger.debug("完成獲取歷史股價: " + stockTw.getStockCode());
            task.completeTask(TaskStatusType.SUCCESS, "完成獲取歷史股價: " + stockTw.getStockCode());
            stockTw.setHasAnySubscribed(true);
            stockTwRepository.save(stockTw);
            logger.debug("任務用時: " + task.getTaskUsageTime());
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, true, stockTw));
        } catch (Exception e) {
            logger.error("獲取歷史股價時發生錯誤: " + stockTw.getStockCode() + e);
            task.completeTask(TaskStatusType.FAILED, "獲取歷史股價時發生錯誤: " + stockTw.getStockCode() + e);
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, false, stockTw));
        }
        taskRepository.save(task);

    }

    @Async
    public void trackStockHistoryPricesWithUpdateDaily() {
        Set<String> needToUpdateStockCodes = stockTwRepository.findAllStockCodeBySubscribers();
        logger.debug("要更新每日最新價格的股票: " + needToUpdateStockCodes);
        if (needToUpdateStockCodes.isEmpty()) {
            logger.debug("沒有要更新的股票");
            return;
        }
        Task task = new Task(UUID.randomUUID().toString(), "更新每日最新價格", 1);
        taskRepository.save(task);
        LocalDateTime dateTime = LocalDate.now(ZoneId.of("Asia/Taipei")).atTime(16, 30);
        Instant instant = dateTime.atZone(ZoneId.of("Asia/Taipei")).toInstant();
        long tLongForToday = instant.toEpochMilli();
        logger.debug("更新時間設定為每日16:30，本次設定時間: " + dateTime);

        try {
            String url = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
            JsonNode rootNode = getJsonNodeByUrl(url);
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    String stockCode = node.path("Code").asText();
                    if (!needToUpdateStockCodes.contains(stockCode)) {
                        logger.debug("沒有要更新的股票: " + stockCode + " , 跳過");
                        continue;
                    }
                    logger.debug("查詢到需要更新的股票代號: " + stockCode);
                    stockTwInfluxDBService.writeUpdateDailyStockTwHistoryToInflux(node, tLongForToday);
                }
                logger.debug("更新每日最新價格的股票完成");
                task.completeTask(TaskStatusType.SUCCESS, "更新每日最新價格的股票完成");
            }
        } catch (Exception e) {
            logger.error("更新每日最新價格時發生錯誤", e);
            task.completeTask(TaskStatusType.FAILED, "更新每日最新價格時發生錯誤");
        }
        taskRepository.save(task);
    }

    public void removeStockTwPricesDataByStockCode(String stockCode) throws RuntimeException {
        try {
            logger.debug("要刪除的股票資料代號: " + stockCode);
            stockTwInfluxDBService.deleteDataByStockCode(stockCode);
            logger.debug("刪除股票資料完成");
        } catch (Exception e) {
            logger.error("刪除股票資料時發生錯誤", e);
            throw new RuntimeException("刪除股票資料時發生錯誤", e);
        }
    }

    public List<Object[]> getAllStockData() {
        return stockTwRepository.findDistinctStockCodeAndName();
    }

    private LocalDate formatStringToDate(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(date, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public StockTw getStockTwByStockCode(String stockCode) {
        return stockTwRepository.findByStockCode(stockCode).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼: " + stockCode));
    }

    private JsonNode getJsonNodeByUrl(String url) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            logger.debug("查詢結果: " + response);
            return objectMapper.readTree(response);
        } catch (JsonProcessingException e) {
            logger.error("Json轉換失敗", e);
            throw new RuntimeException("Json轉換失敗", e);
        }

    }
}
