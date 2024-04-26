package xyz.dowob.stockweb.Service.Common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Handler.AssetHandler;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.AssetRepository;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

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


    @Async
    public void getAssetHistoryInfo(Asset asset, String type, String timestamp) {
        Map<String, List<FluxTable>> tableMap;
        String key = String.format("kline_%s_%s", type, asset.getId());
        logger.info("開始處理資產: " + asset.getId());
        try {
            getAssetStatisticsAndSaveToRedis(asset, key);
            switch (type){
                case "history":
                    redisService.saveHashToCache(key, "status", "processing", 168);
                    tableMap = assetInfluxMethod.queryByAsset(asset, true, timestamp);
                    if (nodataMethod(asset, type, tableMap, key)) {
                        return;
                    }
                    saveAssetInfoToRedis(tableMap, key, "history");
                    break;
                case "current":
                    redisService.saveHashToCache(key, "status", "processing", 168);
                    tableMap = assetInfluxMethod.queryByAsset(asset, false, timestamp);
                    if (nodataMethod(asset, type, tableMap, key)) {
                        return;
                    }
                    saveAssetInfoToRedis(tableMap, key, "current");
                    break;
                default:
                    throw new RuntimeException("錯誤的查詢類型");
            }

        } catch (Exception e) {
            redisService.saveHashToCache(key, "status", "fail", 168);
            throw new RuntimeException("發生錯誤: ", e);
        }
    }

    private boolean nodataMethod(Asset asset, String type, Map<String, List<FluxTable>> tableMap, String key) {
        logger.debug("tableMap: "+ tableMap );
        if (tableMap.get("%s_%s".formatted(asset.getId(), type)).isEmpty()) {
            List<String> listCache = redisService.getCacheListValueFromKey(key + ":data");
            if (listCache.isEmpty()) {
                logger.debug("此資產沒有過資料紀錄，設定緩存狀態為no_data");
                redisService.saveHashToCache(key, "status", "no_data", 168);
            }
            logger.debug("此資產有資料紀錄，設定緩存狀態為success");
            redisService.saveHashToCache(key, "status", "success", 168);
            return true;
        }
        return false;
    }


    private void saveAssetInfoToRedis(Map<String, List<FluxTable>> tableMap, String key, String type) {
        try {
            Map<String, AssetKlineDataDto> klineDataMap = new LinkedHashMap<>();
            String lastTimePoint  = null;
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
                        Double value = null;
                        String formattedValue = null;
                        Object valueObj = record.getValueByKey("_value");
                        if (valueObj instanceof Double) {
                            value = ((Double) valueObj);
                            formattedValue = String.format("%.2f", value);
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
            redisService.rPushToCacheList(key + ":data", kLineJson, 168);
            redisService.saveHashToCache(key, "type", type, 168);
            redisService.saveHashToCache(key, "last_timestamp", lastTimePoint, 168);
            redisService.saveHashToCache(key, "status", "success", 168);
        } catch (Exception e) {
            throw new RuntimeException("資產資料處理錯誤: ", e);
        }
    }

    private void getAssetStatisticsAndSaveToRedis (Asset asset, String key) {
        List<LocalDateTime> localDateList = assetInfluxMethod.getStatisticDate();
        Map<LocalDateTime, String> resultMap = new TreeMap<>();
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
        };

        Map<LocalDateTime, List<FluxTable>> queryResultMap = assetInfluxMethod.queryByTimeAndUser(select[0].toString(), select[1].toString(), filters, null, localDateList, 72, true, false);
        for (Map.Entry<LocalDateTime, List<FluxTable>> entry : queryResultMap.entrySet()) {
            if (entry.getValue().isEmpty() || entry.getValue().getFirst().getRecords().isEmpty()) {
                logger.debug(entry.getKey() + " 取得指定資產價格資料: " + null);
                resultMap.put(entry.getKey(), null);
            } else {
                for (FluxTable table : entry.getValue()) {
                    for (FluxRecord record : table.getRecords()) {
                        Double value = (Double) record.getValueByKey("_value");
                        logger.debug(entry.getKey() + " 取得指定資產價格資料: " + value);
                        if (value == null) {
                            resultMap.put(entry.getKey(), null);
                        } else {
                            resultMap.put(entry.getKey(), String.valueOf(value));
                        }
                    }
                }
            }
        }
        resultMap.put(localDateList.getFirst(), (assetInfluxMethod.getLatestPrice(asset)).toString());
        logger.debug("結果資料: " + resultMap);
        List<String> resultList = new ArrayList<>();
        for (Map.Entry<LocalDateTime, String> entry : resultMap.entrySet()) {
            if (entry.getValue() != null) {
                resultList.add(entry.getValue());
            } else {
                resultList.add("數據不足");
            }

        }
        redisService.saveListToCache(key + ":statistics", resultList, 168);
    }


    public Asset getAssetById(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> new RuntimeException("找不到資產"));
    }


    public String formatRedisAssetInfoCacheToJson(String type, String key, User user, Long assetId) {
        ArrayNode mergeArray = objectMapper.createArrayNode();
        Map <String, Object> resultMap = new HashMap<>();


        List<String> cacheDataList = redisService.getCacheListValueFromKey(key + ":data");
        String timestamp = redisService.getHashValueFromKey(key, "last_timestamp");
        try {
            for (String item : cacheDataList) {
                ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(item);
                mergeArray.addAll(arrayNode);
            }
            resultMap.put("data", mergeArray);
            resultMap.put("type", type);
            resultMap.put("last_timestamp", timestamp);

            List<String> cacheStatisticsList = redisService.getCacheListValueFromKey(key + ":statistics");
            if (user == null) {
                resultMap.put("statistics", cacheStatisticsList);
            } else {
                List<String> statisticsListForUser = new ArrayList<>();
                for (String item : cacheStatisticsList) {
                    if ("數據不足".equals(item)) {
                        statisticsListForUser.add(item);
                        continue;
                    }
                    BigDecimal bigDecimal = new BigDecimal(item);
                    Asset asset = assetRepository.findById(assetId).orElseThrow(() -> new RuntimeException("找不到資產"));
                    BigDecimal formatPrice = assetHandler.exrateToPreferredCurrency(asset, bigDecimal, user.getPreferredCurrency());
                    statisticsListForUser.add(formatPrice.toString());
                }
                resultMap.put("statistics", statisticsListForUser);
            }

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
}


