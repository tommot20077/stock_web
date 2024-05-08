package xyz.dowob.stockweb.Service.Common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Handler.AssetHandler;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.AssetRepository;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author yuan
 * 用於資產相關的業務邏輯。
 */
@Service
public class AssetService {
    private final AssetRepository assetRepository;

    private final AssetInfluxMethod assetInfluxMethod;

    private final ObjectMapper objectMapper;

    private final RedisService redisService;

    private final AssetHandler assetHandler;

    private final CurrencyRepository currencyRepository;

    private final StockTwRepository stockTwRepository;

    private final CryptoRepository cryptoRepository;

    Logger logger = LoggerFactory.getLogger(AssetService.class);

    @Autowired
    public AssetService(AssetRepository assetRepository, AssetInfluxMethod assetInfluxMethod, ObjectMapper objectMapper, RedisService redisService, AssetHandler assetHandler, CurrencyRepository currencyRepository, StockTwRepository stockTwRepository, CryptoRepository cryptoRepository) {
        this.assetRepository = assetRepository;
        this.assetInfluxMethod = assetInfluxMethod;
        this.objectMapper = objectMapper;
        this.redisService = redisService;
        this.assetHandler = assetHandler;
        this.currencyRepository = currencyRepository;
        this.stockTwRepository = stockTwRepository;
        this.cryptoRepository = cryptoRepository;
    }

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}")
    private String stockTwHistoryBucket;

    @Value("${db.influxdb.bucket.crypto_history}")
    private String cryptoHistoryBucket;

    @Value("${common.global_page_size:100}")
    private int pageSize;


    /**
     * 異步處理資產歷史數據，並將其存儲到Redis中。
     *
     * @param asset     資產對象。
     * @param type      查詢類型。
     * @param timestamp 查詢時間戳。
     */
    @Async
    public void getAssetHistoryInfo(Asset asset, String type, String timestamp) {
        logger.debug("開始處理資產type: " + type + " timestamp: " + timestamp);
        Map<String, List<FluxTable>> tableMap;
        String hashInnerKey = String.format("%s_%s:", type, asset.getId());
        String listKey = String.format("kline_%s", hashInnerKey);
        logger.debug("開始處理資產: " + asset.getId());
        try {

            switch (type) {
                case "history":
                    redisService.saveHashToCache("kline", hashInnerKey + "status", "processing", 168);
                    tableMap = assetInfluxMethod.queryByAsset(asset, true, timestamp);
                    if (nodataMethod(asset, type, tableMap, listKey, hashInnerKey)) {
                        return;
                    }
                    saveAssetInfoToRedis(tableMap, listKey, hashInnerKey, "history");
                    break;
                case "current":
                    redisService.saveHashToCache("kline", hashInnerKey + "status", "processing", 168);
                    tableMap = assetInfluxMethod.queryByAsset(asset, false, timestamp);
                    if (nodataMethod(asset, type, tableMap, listKey, hashInnerKey)) {
                        return;
                    }
                    saveAssetInfoToRedis(tableMap, listKey, hashInnerKey, "current");
                    break;
                default:
                    throw new RuntimeException("錯誤的查詢類型");
            }

        } catch (Exception e) {
            redisService.saveHashToCache("kline", hashInnerKey + "status", "fail", 168);
            throw new RuntimeException("發生錯誤: ", e);
        }
    }

    /**
     * 當資產歷史數據判斷邏輯
     * 當資產沒有數據時，設定緩存狀態為no_data
     * 當資產有數據時，設定緩存狀態為success
     *
     * @param asset        資產對象
     * @param type         查詢類型
     * @param tableMap     資料表
     * @param listKey      緩存列表鍵
     * @param hashInnerKey 緩存內部鍵
     *
     * @return 是否有數據
     */
    private boolean nodataMethod(Asset asset, String type, Map<String, List<FluxTable>> tableMap, String listKey, String hashInnerKey) {
        logger.debug("tableMap: " + tableMap);
        if (tableMap.get("%s_%s".formatted(asset.getId(), type)).isEmpty()) {
            List<String> listCache = redisService.getCacheListValueFromKey(listKey + "data");
            if (listCache.isEmpty()) {
                logger.debug("此資產沒有過資料紀錄，設定緩存狀態為no_data");
                redisService.saveHashToCache("kline", hashInnerKey + "status", "no_data", 168);
                return true;
            }
            logger.debug("此資產有資料紀錄，設定緩存狀態為success");
            redisService.saveHashToCache("kline", hashInnerKey + "status", "success", 168);
            return true;
        }
        return false;
    }


    /**
     * 將資產數據存儲到Redis中。
     *
     * @param tableMap     資料表。
     * @param key          緩存鍵。
     * @param hashInnerKey 緩存內部鍵。
     * @param type         查詢類型。
     */
    @Async
    protected void saveAssetInfoToRedis(Map<String, List<FluxTable>> tableMap, String key, String hashInnerKey, String type) {
        try {
            Map<String, AssetKlineDataDto> klineDataMap = new LinkedHashMap<>();
            String lastTimePoint = null;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

            for (Map.Entry<String, List<FluxTable>> entry : tableMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                for (FluxTable fluxTable : entry.getValue()) {
                    if (fluxTable.getRecords().isEmpty()) {
                        continue;
                    }
                    for (FluxRecord record : fluxTable.getRecords()) {
                        Instant recordTimestamp = ((Instant) Objects.requireNonNull(record.getValueByKey("_time")));
                        String timestamp = formatter.format(recordTimestamp);

                        if (lastTimePoint == null || Instant.parse(lastTimePoint).isBefore(Instant.parse(timestamp))) {
                            lastTimePoint = timestamp;
                        }
                        String field = (String) record.getValueByKey("_field");
                        BigDecimal value = null;
                        String formattedValue = null;
                        Object valueObj = record.getValueByKey("_value");
                        if (valueObj instanceof Double doubleValue) {
                            value = BigDecimal.valueOf(doubleValue);
                            formattedValue = String.format("%.6f", value);
                        }
                        AssetKlineDataDto dataDto = klineDataMap.getOrDefault(timestamp, new AssetKlineDataDto());
                        dataDto.setTimestamp(timestamp);
                        if (value != null && field != null) {
                            {
                                switch (field) {
                                    case "open":
                                        dataDto.setOpen(formattedValue);
                                        break;
                                    case "high":
                                        dataDto.setHigh(formattedValue);
                                        break;
                                    case "low":
                                        dataDto.setLow(formattedValue);
                                        break;
                                    case "close":
                                        dataDto.setClose(formattedValue);
                                        break;
                                    case "volume":
                                        dataDto.setVolume(formattedValue);
                                        break;
                                    case "rate":
                                        dataDto.setClose(formattedValue);
                                        dataDto.setOpen(formattedValue);
                                        dataDto.setHigh(formattedValue);
                                        dataDto.setLow(formattedValue);
                                        dataDto.setVolume("0");
                                        break;
                                    default:
                                        throw new RuntimeException("資產資料轉換錯誤: " + field);
                                }
                            }
                        }
                        klineDataMap.put(timestamp, dataDto);
                    }
                }
            }
            objectMapper.registerModule(new JavaTimeModule());
            String kLineJson = objectMapper.writeValueAsString(klineDataMap.values());
            redisService.rPushToCacheList(key + "data", kLineJson, 168);
            redisService.saveHashToCache("kline", hashInnerKey + "type", type, 168);
            redisService.saveHashToCache("kline", hashInnerKey + "last_timestamp", lastTimePoint, 168);
            redisService.saveHashToCache("kline", hashInnerKey + "status", "success", 168);
        } catch (Exception e) {
            throw new RuntimeException("資產資料處理錯誤: ", e);
        }
    }


    public List<String> getAssetStatisticsAndSaveToRedis(Asset asset) {
        List<LocalDateTime> localDateList = assetInfluxMethod.getStatisticDate();
        Map<LocalDateTime, String> priceMap = new TreeMap<>();
        Map<String, String> filters = new HashMap<>();
        Object[] select = new Object[2];

        switch (asset) {
            case StockTw stockTw -> {
                filters.put("_field", "close");
                filters.put("stock_tw", stockTw.getStockCode());
                select[0] = stockTwHistoryBucket;
                select[1] = "kline_data";
            }
            case CryptoTradingPair cryptoTradingPair -> {
                filters.put("_field", "close");
                filters.put("tradingPair", cryptoTradingPair.getTradingPair());
                select[0] = cryptoHistoryBucket;
                select[1] = "kline_data";
            }
            case Currency currency -> {
                filters.put("_field", "rate");
                filters.put("Currency", currency.getCurrency());
                select[0] = currencyBucket;
                select[1] = "exchange_rate";
            }
            default -> {
                logger.error("無法取得指定資產資料: " + asset);
                throw new RuntimeException("無法取得指定資產資料: " + asset);
            }
        }

        Map<LocalDateTime, List<FluxTable>> queryResultMap = assetInfluxMethod.queryByTimeAndUser(select[0].toString(),
                                                                                                  select[1].toString(),
                                                                                                  filters,
                                                                                                  null,
                                                                                                  localDateList,
                                                                                                  72,
                                                                                                  true,
                                                                                                  false);
        for (Map.Entry<LocalDateTime, List<FluxTable>> entry : queryResultMap.entrySet()) {
            if (entry.getValue().isEmpty() || entry.getValue().getFirst().getRecords().isEmpty()) {
                logger.debug(entry.getKey() + " 取得指定資產價格資料: " + null);
                priceMap.put(entry.getKey(), null);
            } else {
                for (FluxTable table : entry.getValue()) {
                    for (FluxRecord record : table.getRecords()) {
                        if (record.getValueByKey("_value") instanceof Double doubleValue) {
                            BigDecimal value = BigDecimal.valueOf(doubleValue);
                            logger.debug(entry.getKey() + " 取得指定資產價格資料: " + value);
                            priceMap.put(entry.getKey(), String.format("%.6f", value));
                        } else {
                            logger.debug(entry.getKey() + " 取得指定資產價格資料: " + null);
                            priceMap.put(entry.getKey(), null);
                        }
                    }
                }
            }
        }
        priceMap.put(localDateList.getFirst(), (assetInfluxMethod.getLatestPrice(asset)).toString());
        logger.debug("結果資料: " + priceMap);
        List<String> resultList = new ArrayList<>();
        for (Map.Entry<LocalDateTime, String> entry : priceMap.entrySet()) {
            if (entry.getValue() != null) {
                resultList.add(entry.getValue());
            } else {
                resultList.add("數據不足");
            }
        }
        return resultList;
    }


    public Asset getAssetById(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> new RuntimeException("找不到資產"));
    }

    public String formatRedisAssetInfoCacheToJson(List<String> cachedAssetJson, Asset asset, User user) {
        try {
            Map<String, Object> resultMap = new HashMap<>();

            if (user == null) {
                resultMap.put("statistics", cachedAssetJson);
            } else {
                List<String> statisticsListForUser = new ArrayList<>();
                for (String item : cachedAssetJson) {
                    if ("數據不足".equals(item)) {
                        statisticsListForUser.add(item);
                        continue;
                    }
                    BigDecimal bigDecimal = new BigDecimal(item);
                    BigDecimal formatPrice = assetHandler.exrateToPreferredCurrency(asset, bigDecimal, user.getPreferredCurrency());
                    statisticsListForUser.add(formatPrice.setScale(6, RoundingMode.HALF_UP).toString());
                }
                resultMap.put("statistics", statisticsListForUser);
            }

            switch (asset) {
                case StockTw stockTw -> resultMap.put("assetName", stockTw.getStockName());
                case CryptoTradingPair cryptoTradingPair -> resultMap.put("assetName", cryptoTradingPair.getBaseAsset());
                case Currency currency -> resultMap.put("assetName", currency.getCurrency());
                default -> {
                    logger.error("無法取得指定資產資料: " + asset);
                    throw new RuntimeException("無法取得指定資產資料: " + asset);
                }
            }
            return objectMapper.writeValueAsString(resultMap);
        } catch (JsonProcessingException e) {
            logger.error("資產資料處理錯誤: ", e);
            throw new RuntimeException("資產資料處理錯誤: ", e);
        }
    }

    public String formatRedisAssetKlineCacheToJson(String type, String listKey, String hashInnerKey, User user) {
        ArrayNode mergeArray = objectMapper.createArrayNode();
        Map<String, Object> resultMap = new HashMap<>();

        List<String> cacheDataList = redisService.getCacheListValueFromKey(listKey + "data");
        String timestamp = redisService.getHashValueFromKey("kline", hashInnerKey + "last_timestamp");
        try {
            for (String item : cacheDataList) {
                ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(item);
                mergeArray.addAll(arrayNode);
            }

            resultMap.put("data", mergeArray);
            resultMap.put("type", type);
            resultMap.put("last_timestamp", timestamp);
            resultMap.put("preferCurrencyExrate", user.getPreferredCurrency().getExchangeRate());


            return objectMapper.writeValueAsString(resultMap);
        } catch (JsonProcessingException e) {
            logger.error("資產資料處理錯誤: ", e);
            throw new RuntimeException("資產資料處理錯誤: ", e);
        }
    }


    public List<Asset> findHasSubscribeAsset() {
        return findHasSubscribeAsset(true, true, true);
    }

    public List<Asset> findHasSubscribeAsset(boolean crypto, boolean stockTw, boolean currency) {
        List<Asset> result = new ArrayList<>();
        if (crypto) {
            result.addAll(cryptoRepository.findAllByHasAnySubscribed(true));
        }
        if (stockTw) {
            result.addAll(stockTwRepository.findAllByHasAnySubscribed(true));
        }
        if (currency) {
            result.addAll(currencyRepository.findAll());
        }
        return result;
    }

    public List<Asset> findAssetPageByType(String category, int page, boolean isCache) throws JsonProcessingException {

        PageRequest pageRequest = PageRequest.of(page - 1, pageSize);
        List<Asset> assetsList = getAssetType(category) != null ? assetRepository.findAllByAssetType(getAssetType(category), pageRequest)
                                                                                 .getContent() : assetRepository.findAll(pageRequest)
                                                                                                                .getContent();

        if (isCache) {
            String key = "asset";
            String innerKey = category + "_page_" + page;
            cacheHashDataToRedis(key, innerKey, assetsList, 8);
        }
        return assetsList;
    }

    public String formatStringAssetListToFrontendType(List<?> assetList, String innerKey) throws JsonProcessingException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        if (assetList == null || assetList.isEmpty()) {
            return null;
        }

        if (assetList.getFirst() instanceof Asset) {
            for (Object a : assetList) {
                Asset asset = (Asset) a;
                Map<String, Object> resultMap = new HashMap<>();
                switch (asset) {
                    case StockTw stockTw -> {
                        resultMap.put("assetName", stockTw.getStockName());
                        resultMap.put("isSubscribed", stockTw.isHasAnySubscribed());
                    }
                    case CryptoTradingPair cryptoTradingPair -> {
                        resultMap.put("assetName", cryptoTradingPair.getTradingPair());
                        resultMap.put("isSubscribed", cryptoTradingPair.isHasAnySubscribed());
                    }
                    case Currency currency -> {
                        resultMap.put("assetName", currency.getCurrency());
                        resultMap.put("isSubscribed", true);
                    }
                    default -> {
                        logger.error("無法取得指定資產資料: " + asset);
                        throw new RuntimeException("無法取得指定資產資料: " + asset);
                    }
                }
                resultMap.put("assetId", asset.getId());
                resultMap.put("type", asset.getAssetType().toString());
                resultList.add(resultMap);
            }
        } else if (assetList.getFirst() instanceof Map<?, ?>) {
            for (Object asset : assetList) {
                Map<String, Object> assetMap = (Map<String, Object>) asset;
                resultList.add((assetMap));
            }
        } else {
            throw new RuntimeException("資產類型錯誤: " + assetList);
        }
        return cacheHashDataToRedis("frontendAssetList", innerKey, resultList, 24);

    }

    public List<Map<String, Object>> formatJsonToAssetList(String assetJson) throws JsonProcessingException {
        if (assetJson == null) {
            return null;
        }
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper.readValue(assetJson, new TypeReference<>() {});
    }


    private <T> String cacheHashDataToRedis(String key, String innerKey, T content, int expireTime) throws JsonProcessingException {
        String json = formatContentToJson(content);
        redisService.saveHashToCache(key, innerKey, json, expireTime);
        return json;
    }

    public <T> String formatContentToJson(T content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(content);
    }

    public int findAssetTotalPage(String category, int pageSize) {
        PageRequest pageRequest = PageRequest.of(0, pageSize);
        return getAssetType(category) != null ? assetRepository.findAllByAssetType(getAssetType(category), pageRequest)
                                                               .getTotalPages() : assetRepository.findAll(pageRequest).getTotalPages();
    }

    private AssetType getAssetType(String category) {
        return switch (category) {
            case "crypto" -> AssetType.CRYPTO;
            case "stock_tw" -> AssetType.STOCK_TW;
            case "currency" -> AssetType.CURRENCY;
            case "all" -> null;
            default -> throw new RuntimeException("找不到資產類型: " + category);
        };

    }
}


