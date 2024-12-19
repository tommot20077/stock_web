package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Component.Annotation.MeaninglessData;
import xyz.dowob.stockweb.Component.Event.Asset.AssetHistoryDataFetchCompleteEvent;
import xyz.dowob.stockweb.Component.Method.Kafka.KafkaProducerMethod;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Exception.SubscriptionExceptions;
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
@Log4j2
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

    @SuppressWarnings("UnstableApiUsage")
    RateLimiter rateLimiter = RateLimiter.create(0.5);

    @Value(value = "${stock_tw.finmind.token}")
    private String finMindToken;

    @Value(value = "${db.influxdb.bucket.stock_tw_history.dateline:20110101}")
    private String stockTwHistoryDateline;

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
    public StockTwService(
            StockTwRepository stockTwRepository,
            SubscribeRepository subscribeRepository,
            StockTwInfluxService stockTwInfluxService,
            CurrencyRepository currencyRepository,
            TaskRepository taskRepository,
            Optional<KafkaProducerMethod> kafkaProducerMethod,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher,
            SubscribeMethod subscribeMethod) {
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
    public void addStockSubscribeToUser(String stockId, User user) throws AssetExceptions, SubscriptionExceptions {
        StockTw stock = stockTwRepository
                .findByStockCode(stockId)
                .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_NOT_FOUND, stockId));
        Long assetId = stock.getId();
        if (subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).isPresent()) {
            throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_ALREADY_EXIST, stock.getStockCode());
        }
        if (!stock.checkUserIsSubscriber(user)) {
            subscribeMethod.addSubscriberToStockTw(stock, user.getId());
        }
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
    public void removeStockSubscribeToUser(String stockId, User user) throws AssetExceptions, SubscriptionExceptions {
        StockTw stock = stockTwRepository
                .findByStockCode(stockId)
                .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_NOT_FOUND, stockId));
        Long assetId = stock.getId();
        Subscribe subscribe = subscribeRepository
                .findByUserIdAndAssetId(user.getId(), assetId)
                .orElseThrow(() -> new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_NOT_FOUND));
        if (subscribe.isUserSubscribed()) {
            if (subscribe.isRemoveAble()) {
                if (stock.checkUserIsSubscriber(user)) {
                    subscribeMethod.removeSubscriberFromStockTw(stock, user.getId());
                }
            } else {
                throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_CANNOT_UNSUBSCRIBE,
                                                 stock.getStockCode(),
                                                 user.getUsername());
            }
            subscribeRepository.delete(subscribe);
        }
    }

    /**
     * 更新伺服器股票列表
     */
    @Transactional
    @Async
    public void updateStockList() throws AssetExceptions {
        StockTw stock;
        String stockListUrl = "https://api.finmindtrade.com/api/v4/data?";
        String url = stockListUrl + "dataset=TaiwanStockInfo&stock_id=&token=" + finMindToken;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("data");
                if (dataArray.isArray()) {
                    for (JsonNode stockNode : dataArray) {
                        stock = stockTwRepository.findByStockCode(stockNode.get("stock_id").asText()).orElse(new StockTw());
                        stock.setStockCode(stockNode.get("stock_id").asText());
                        stock.setStockName(stockNode.get("stock_name").asText());
                        stock.setIndustryCategory(stockNode.get("industry_category").asText());
                        stock.setStockType(stockNode.get("type").asText());
                        stock.setUpdateTime(formatStringToDate(stockNode.get("date").asText()));
                        stock.setAssetType(AssetType.STOCK_TW);
                        stockTwRepository.save(stock);
                    }
                } else {
                    throw new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_DATA_RESOLVE_ERROR);
                }
            } catch (Exception e) {
                throw new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_DATA_UPDATE_ERROR);
            }
        } else {
            throw new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_DATA_UPDATE_ERROR);
        }
    }

    /**
     * 檢查獲取股票資料網址是否有效
     *
     * @return Map<String, List < String>> 回傳成功與失敗的股票代碼
     */
    @SuppressWarnings("UnstableApiUsage")
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
                return;
            }
            try {
                JsonNode rootNode = getJsonNodeByUrl(url);
                JsonNode msgArray = rootNode.path("msgArray");
                if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
                    checkSuccessList.add((String) subscribe[0]);
                    inquiryList.add(inquiry);
                } else {
                    checkFailList.add((String) subscribe[0]);
                }
                rateLimiter.acquire();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
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
    public void trackStockNowPrices(List<String> stockInquiryList) throws AssetExceptions, JsonProcessingException {
        final StringBuilder inquireUrl = new StringBuilder(stockCurrentPriceUrl);
        stockInquiryList.forEach(stockInquiry -> inquireUrl.append(stockInquiry).append("|"));
        JsonNode rootNode = getJsonNodeByUrl(inquireUrl.toString());
        JsonNode msgArray = rootNode.path("msgArray");
        if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
            Map<String, Map<String, String>> klineData = formatStockTwDataToKline(msgArray);
            if (kafkaProducerMethod.isPresent()) {
                kafkaProducerMethod.get().sendMessage("stock_tw_kline", klineData);
            } else {
                stockTwInfluxService.writeToInflux(klineData);
            }
        }
    }

    /**
     * 追蹤股票完整歷史價格,並處理任務狀態
     */
    @Async

    @SuppressWarnings("UnstableApiUsage")
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
                String url;
                if (Objects.equals(stockTw.getStockType(), "twse")) {
                    url = String.format("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=%s&stockNo=%s",
                                        date.format(DateTimeFormatter.ofPattern("yyyyMM01")),
                                        stockTw.getStockCode());

                } else if (Objects.equals(stockTw.getStockType(), "tpex")) {
                    url = String.format("https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?code=%s&date=%s",
                                        stockTw.getStockCode(),
                                        date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));


                } else {
                    throw new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_DATA_RESOLVE_ERROR);
                }
                rateLimiter.acquire();
                JsonNode rootNode = getJsonNodeByUrl(url);
                int total;
                if (Objects.equals(stockTw.getStockType(), "twse")) {
                    total = rootNode.path("total").asInt();
                } else {
                    total = rootNode.path("tables").get(0).path("totalCount").asInt();
                }
                if (total > 0) {
                    ArrayNode dataArray;
                    if (Objects.equals(stockTw.getStockType(), "twse")) {
                        dataArray = (ArrayNode) rootNode.path("data");
                    } else {
                        dataArray = (ArrayNode) rootNode.path("tables").get(0).path("data");
                    }
                    stockTwInfluxService.writeStockTwHistoryToInflux(dataArray, stockTw.getStockCode());
                    date = date.minusMonths(1);
                    neverHasData = false;
                } else {
                    if (neverHasData && !rollbackChance) {
                        throw new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_DATA_EMPTY, stockTw.getStockCode());
                    } else if (rollbackChance) {
                        rollbackChance = false;
                        date = date.minusMonths(1);
                    } else {
                        hasData = false;
                    }
                }
            }
            task.completeTask(TaskStatusType.SUCCESS, "完成獲取歷史股價: " + stockTw.getStockCode());
            stockTw.setHasAnySubscribed(true);
            stockTwRepository.save(stockTw);
            applicationEventPublisher.publishEvent(new AssetHistoryDataFetchCompleteEvent(this, true, stockTw));
        } catch (Exception e) {
            log.error("獲取歷史股價時發生錯誤: {} {}", stockTw.getStockCode(), e.getMessage());
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
        Set<String> needToUpdateTwseStockCodes = stockTwRepository.findAllStockCodeBySubscribers("twse");
        Set<String> needToUpdateTpexStockCodes = stockTwRepository.findAllStockCodeBySubscribers("tpex");
        if (needToUpdateTwseStockCodes.isEmpty()) {
            return;
        }
        Task task = new Task(UUID.randomUUID().toString(), "更新台灣股票每日最新價格", 1);
        taskRepository.save(task);
        LocalDateTime setDateTime = LocalDate.now(ZoneId.of("Asia/Taipei")).atTime(0, 0);
        long timestamp = setDateTime.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();
        try {
            String twseUrl = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
            JsonNode rootNode = getJsonNodeByUrl(twseUrl);
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    String stockCode = node.path("Code").asText();
                    if (!needToUpdateTwseStockCodes.contains(stockCode)) {
                        continue;
                    }
                    stockTwInfluxService.writeUpdateDailyStockTwHistoryToInflux(node, timestamp, true);
                }
            }


            needToUpdateTpexStockCodes.forEach(stockCode -> {
                String tpexUrl = String.format("https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?code=%s&date=%s",
                                               stockCode,
                                               setDateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));
                try {
                    JsonNode tpexRootNode = getJsonNodeByUrl(tpexUrl);
                    int total = tpexRootNode.path("tables").get(0).path("totalCount").asInt();
                    if (total > 0) {
                        ArrayNode dataArray = (ArrayNode) rootNode.path("tables").get(0).path("data");
                        if (!dataArray.isEmpty()) {
                            stockTwInfluxService.writeUpdateDailyStockTwHistoryToInflux(dataArray.get(total - 1), timestamp, false, stockCode);
                        }
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });

            task.completeTask(TaskStatusType.SUCCESS, "更新每日最新價格的股票完成");
        } catch (Exception e) {
            task.completeTask(TaskStatusType.FAILED, "更新每日最新價格時發生錯誤");
        }
        taskRepository.save(task);
    }

    /**
     * 刪除股票在influx的資料
     *
     * @param stockCode 股票代碼
     */
    public void removeStockTwPricesDataByStockCode(String stockCode) throws RuntimeException {
        try {
            stockTwInfluxService.deleteDataByStockCode(stockCode);
        } catch (Exception e) {
            throw new AssetExceptions(AssetExceptions.ErrorEnum.DELETE_ASSET_DATA_ERROR, stockCode, e.getMessage());
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
    public StockTw getStockTwByStockCode(String stockCode) throws AssetExceptions {
        return stockTwRepository
                .findByStockCode(stockCode)
                .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.STOCK_NOT_FOUND, stockCode));
    }

    /**
     * 獲取指定URL的JsonNode資料
     *
     * @param url 請求網址
     *
     * @return JsonNode
     */
    private JsonNode getJsonNodeByUrl(String url) throws JsonProcessingException {
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject(url, String.class);
        return objectMapper.readTree(response);
    }

    /**
     * 格式化股票資料為Kline格式
     *
     * @param msgArray 股票資料，請求資料為多檔股票，每檔股票為一個JsonNode
     *
     * @return HashMap<String, Map < String, String>> 股票代碼與Kline資料
     */
    @MeaninglessData
    private HashMap<String, Map<String, String>> formatStockTwDataToKline(JsonNode msgArray) throws AssetExceptions {
        HashMap<String, Map<String, String>> klineData = new HashMap<>();
        Currency twdCurrency = currencyRepository
                .findByCurrency("TWD")
                .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.DEFAULT_CURRENCY_NOT_FOUND, "TWD"));
        BigDecimal twdToUsd = twdCurrency.getExchangeRate();
        for (JsonNode msgNode : msgArray) {
            if (Objects.equals(msgNode.path("z").asText(), "-") || Objects.equals(msgNode.path("z").asText(), "--") || Objects.equals(
                    msgNode.path("h").asText(),
                    "--") || Objects.equals(msgNode.path("o").asText(), "--") || Objects.equals(msgNode.path("l").asText(), "--")) {
                continue;
            }
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
        }
        return klineData;
    }
}
