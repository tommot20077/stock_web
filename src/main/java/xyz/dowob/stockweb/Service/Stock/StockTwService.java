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
import xyz.dowob.stockweb.Component.Method.Kafka.KafkaProducerMethod;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Model.Common.Task;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.TaskRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author yuan
 * 台灣股票相關服務
 */
@Service
public class StockTwService {
    private final StockTwRepository stockTwRepository;

    private final SubscribeRepository subscribeRepository;

    private final StockTwInfluxService stockTwInfluxService;

    private final CurrencyRepository currencyRepository;

    private final TaskRepository taskRepository;

    private final Optional<KafkaProducerMethod> kafkaProducerMethod;

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final SubscribeMethod subscribeMethod;

    private final String stockCurrentPriceUrl = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=";

    RateLimiter rateLimiter = RateLimiter.create(0.5);

    @Value(value = "${stock_tw.finmind.token}")
    private String finMindToken;

    @Value(value = "${db.influxdb.bucket.stock_tw_history.dateline:20110101}")
    private String stockTwHistoryDateline;

    private final Logger logger = LoggerFactory.getLogger(StockTwService.class);

    /**
     * StockTwService構造函數
     *
     * @param stockTwRepository         股票資料庫
     * @param subscribeRepository       訂閱資料庫
     * @param stockTwInfluxService      股票Influx服務
     * @param taskRepository            任務資料庫
     * @param objectMapper              Json轉換
     * @param applicationEventPublisher 事件發布
     * @param subscribeMethod           訂閱方法
     * @param kafkaProducerMethod       Kafka生產者方法
     */
    @Autowired
    public StockTwService(StockTwRepository stockTwRepository, SubscribeRepository subscribeRepository, StockTwInfluxService stockTwInfluxService, CurrencyRepository currencyRepository, TaskRepository taskRepository, Optional<KafkaProducerMethod> kafkaProducerMethod, ObjectMapper objectMapper, ApplicationEventPublisher applicationEventPublisher, SubscribeMethod subscribeMethod) {
        this.stockTwRepository = stockTwRepository;
        this.subscribeRepository = subscribeRepository;
        this.stockTwInfluxService = stockTwInfluxService;
        this.currencyRepository = currencyRepository;
        this.taskRepository = taskRepository;
        this.kafkaProducerMethod = kafkaProducerMethod;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.subscribeMethod = subscribeMethod;
    }

    /**
     * 新增用戶台灣股票訂閱
     *
     * @param stockId 股票代碼
     * @param user    用戶
     */
    @Transactional(rollbackFor = Exception.class)
    public void addStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼"));
        Long assetId = stock.getId();

        if (subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).isPresent()) {
            throw new RuntimeException("已經訂閱過此股票");
        }

        if (!stock.checkUserIsSubscriber(user)) {
            subscribeMethod.addSubscriberToStockTw(stock, user.getId());
        }
        logger.debug("用戶主動訂閱，此訂閱設定可刪除");
        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(stock);
        subscribe.setUserSubscribed(true);
        subscribe.setRemoveAble(true);
        subscribeRepository.save(subscribe);
    }

    /**
     * 取消用戶台灣股票訂閱
     *
     * @param stockId 股票代碼
     * @param user    用戶
     */
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
                    subscribeMethod.removeSubscriberFromStockTw(stock, user.getId());
                }
            } else {
                logger.warn("此訂閱: {} 為用戶: {}現在所持有的資產，不可刪除訂閱", stock.getStockCode(), user.getUsername());
                throw new RuntimeException("此訂閱: " + stock.getStockCode() + " 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
            }
            subscribeRepository.delete(subscribe);
        }
    }

    /**
     * 更新伺服器股票列表
     */
    @Transactional
    @Async
    public void updateStockList() {
        StockTw stock;
        String stockListUrl = "https://api.finmindtrade.com/api/v4/data?";
        String url = stockListUrl + "dataset=TaiwanStockInfo&stock_id=&token=" + finMindToken;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        logger.debug("更新股票列表: {}", response.getBody());
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("data");
                logger.debug("股票數量: {}", dataArray.size());
                if (dataArray.isArray()) {
                    logger.debug("陣列資料: {}", dataArray);
                    for (JsonNode stockNode : dataArray) {
                        stock = stockTwRepository.findByStockCode(stockNode.get("stock_id").asText()).orElse(new StockTw());
                        logger.debug("更新股票: {}", stockNode.get("stock_id").asText());
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

    /**
     * 檢查獲取股票資料網址是否有效
     *
     * @return Map<String, List < String>> 回傳成功與失敗的股票代碼
     */
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
                logger.warn("暫時不支援此類型: {}-{}", stockCode, stockType);
                return;
            }
            logger.debug("查詢股票: {} {}", stockCode, stockType);
            logger.debug("查詢股票網址: {}", url);

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

    /**
     * 追蹤股票即時五秒搓合交易價格
     *
     * @param stockInquiryList 股票代碼列表
     */
    @Async
    public void trackStockNowPrices(List<String> stockInquiryList) {
        final StringBuilder inquireUrl = new StringBuilder(stockCurrentPriceUrl);
        logger.debug("要追蹤價格的股票: {}", stockInquiryList);
        stockInquiryList.forEach(stockInquiry -> inquireUrl.append(stockInquiry).append("|"));
        logger.debug("拼接查詢網址: {}", inquireUrl);
        JsonNode rootNode = getJsonNodeByUrl(inquireUrl.toString());
        JsonNode msgArray = rootNode.path("msgArray");
        if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
            Map<String, Map<String, String>> klineData = formatStockTwDataToKline(msgArray);
            if (kafkaProducerMethod.isPresent()) {
                kafkaProducerMethod.get().sendMessage("stock_tw_kline", klineData);
                logger.debug("Kafka發布成功");
            } else {
                stockTwInfluxService.writeToInflux(klineData);
                logger.debug("寫入InfluxDB成功");
            }
        }
    }

    /**
     * 追蹤股票完整歷史價格,並處理任務狀態
     */

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
                logger.debug("要追蹤價格的股票: {}", stockTw.getStockCode());
                logger.debug("查詢網址: {}", url);

                rateLimiter.acquire();
                JsonNode rootNode = getJsonNodeByUrl(url);
                int total = rootNode.path("total").asInt();
                if (total > 0) {
                    logger.debug("股票:{} 在時間: {} 有資料，開始處理資料", stockTw.getStockCode(), rootNode.path("date"));
                    ArrayNode dataArray = (ArrayNode) rootNode.path("data");
                    stockTwInfluxService.writeStockTwHistoryToInflux(dataArray, stockTw.getStockCode());
                    date = date.minusMonths(1);
                    neverHasData = false;
                } else {
                    logger.debug("股票:{} 在時間: {} 沒有資料", stockTw.getStockCode(), rootNode.path("date"));
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
            logger.debug("完成獲取歷史股價: {}", stockTw.getStockCode());
            task.completeTask(TaskStatusType.SUCCESS, "完成獲取歷史股價: " + stockTw.getStockCode());
            stockTw.setHasAnySubscribed(true);
            stockTwRepository.save(stockTw);
            logger.debug("任務用時: {}", task.getTaskUsageTime());
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, true, stockTw));
        } catch (Exception e) {
            logger.error("獲取{}歷史股價時發生錯誤: {}", stockTw.getStockCode(), e.getMessage());
            task.completeTask(TaskStatusType.FAILED, "獲取歷史股價時發生錯誤: " + stockTw.getStockCode() + e);
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, false, stockTw));
        }
        taskRepository.save(task);

    }

    /**
     * 追蹤每日股票歷史價格,並處理任務狀態 (台灣時間週一至週五16:30執行)
     */
    @Async
    public void trackStockHistoryPricesWithUpdateDaily() {
        Set<String> needToUpdateStockCodes = stockTwRepository.findAllStockCodeBySubscribers();
        logger.debug("要更新每日最新價格的股票: {}", needToUpdateStockCodes);
        if (needToUpdateStockCodes.isEmpty()) {
            logger.debug("沒有要更新的股票");
            return;
        }
        Task task = new Task(UUID.randomUUID().toString(), "更新台灣股票每日最新價格", 1);
        taskRepository.save(task);
        LocalDateTime setDateTime = LocalDate.now(ZoneId.of("Asia/Taipei")).atTime(0 ,0);
        long timestamp = setDateTime.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();

        try {
            String url = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
            JsonNode rootNode = getJsonNodeByUrl(url);
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    String stockCode = node.path("Code").asText();
                    if (!needToUpdateStockCodes.contains(stockCode)) {
                        logger.debug("沒有要更新的股票: {} , 跳過", stockCode);
                        continue;
                    }
                    logger.debug("查詢到需要更新的股票代號: {}", stockCode);
                    stockTwInfluxService.writeUpdateDailyStockTwHistoryToInflux(node, timestamp);
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

    /**
     * 刪除股票在influx的資料
     *
     * @param stockCode 股票代碼
     *
     * @throws RuntimeException 當刪除股票資料時發生錯誤
     */
    public void removeStockTwPricesDataByStockCode(String stockCode) throws RuntimeException {
        try {
            logger.debug("要刪除的股票資料代號: {}", stockCode);
            stockTwInfluxService.deleteDataByStockCode(stockCode);
            logger.debug("刪除股票資料完成");
        } catch (Exception e) {
            logger.error("刪除股票資料時發生錯誤", e);
            throw new RuntimeException("刪除股票資料時發生錯誤", e);
        }
    }

    /**
     * 取得所有股票資料
     *
     * @return List<Object [ ]> 股票代碼與名稱
     */
    public List<Object[]> getAllStockData() {
        return stockTwRepository.findAllByOrderByStockCode();
    }

    /**
     * 轉換字串日期為LocalDate
     *
     * @param date 日期字串
     *
     * @return LocalDate
     */
    private LocalDate formatStringToDate(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(date, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 透過股票代碼取得股票資料
     *
     * @param stockCode 股票代碼
     *
     * @return StockTw 股票資料
     */
    public StockTw getStockTwByStockCode(String stockCode) {
        return stockTwRepository.findByStockCode(stockCode).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼: " + stockCode));
    }

    /**
     * 獲取指定URL的JsonNode資料
     *
     * @param url 請求網址
     *
     * @return JsonNode
     */
    private JsonNode getJsonNodeByUrl(String url) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            logger.debug("查詢結果: {}", response);
            return objectMapper.readTree(response);
        } catch (JsonProcessingException e) {
            logger.error("Json轉換失敗", e);
            throw new RuntimeException("Json轉換失敗", e);
        }
    }

    /**
     * 格式化股票資料為Kline格式
     *
     * @param msgArray 股票資料，請求資料為多檔股票，每檔股票為一個JsonNode
     *
     * @return HashMap<String, Map < String, String>> 股票代碼與Kline資料
     */
    private HashMap<String, Map<String, String>> formatStockTwDataToKline(JsonNode msgArray) {
        logger.debug("msgArray: {}", msgArray);
        HashMap<String, Map<String, String>> klineData = new HashMap<>();
        Currency twdCurrency = currencyRepository.findByCurrency("TWD").orElseThrow(() -> new RuntimeException("找不到TWD幣別"));
        BigDecimal twdToUsd = twdCurrency.getExchangeRate();
        for (JsonNode msgNode : msgArray) {
            if (Objects.equals(msgNode.path("z").asText(), "-") || Objects.equals(msgNode.path("z").asText(), "--") || Objects.equals(
                    msgNode.path("h").asText(),
                    "--") || Objects.equals(msgNode.path("o").asText(), "--") || Objects.equals(msgNode.path("l").asText(), "--")) {
                continue;
            }

            logger.debug("z = {}, c = {}, tlong = {}, o = {}, h = {}, l = {}, v = {}",
                         Double.parseDouble(msgNode.path("z").asText()),
                         msgNode.path("c").asText(),
                         msgNode.path("tlong").asText(),
                         Double.parseDouble(msgNode.path("o").asText()),
                         Double.parseDouble(msgNode.path("h").asText()),
                         Double.parseDouble(msgNode.path("l").asText()),
                         Double.parseDouble(msgNode.path("v").asText()));

            BigDecimal priceUsd = (new BigDecimal(msgNode.path("z").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);
            BigDecimal highUsd = (new BigDecimal(msgNode.path("h").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);
            BigDecimal openUsd = (new BigDecimal(msgNode.path("o").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);
            BigDecimal lowUsd = (new BigDecimal(msgNode.path("l").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);

            HashMap<String, String> innerMap = new HashMap<>();
            innerMap.put("time", msgNode.path("tlong").asText());
            innerMap.put("close", priceUsd.toPlainString());
            innerMap.put("high", highUsd.toPlainString());
            innerMap.put("open", openUsd.toPlainString());
            innerMap.put("low", lowUsd.toPlainString());
            innerMap.put("volume", msgNode.path("v").asText());

            String stockId = msgNode.path("c").asText();
            klineData.put(stockId, innerMap);

            logger.debug("股票代碼: {}, 資料: {}", stockId, innerMap);
        }
        return klineData;
    }
}
