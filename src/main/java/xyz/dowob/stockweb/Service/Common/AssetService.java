package xyz.dowob.stockweb.Service.Common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Component.Annotation.MeaninglessData;
import xyz.dowob.stockweb.Component.Handler.AssetHandler;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Component.Method.AssetTrie.Trie;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Dto.Common.AssetListDto;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Exception.FormatExceptions;
import xyz.dowob.stockweb.Exception.RepositoryExceptions;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.AssetRepository;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static xyz.dowob.stockweb.Exception.AssetExceptions.ErrorEnum.*;
import static xyz.dowob.stockweb.Exception.FormatExceptions.ErrorEnum.*;

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

    /**
     * AssetService建構子
     *
     * @param assetRepository    資產資料庫操作介面
     * @param assetInfluxMethod  資產InfluxDB相關方法
     * @param objectMapper       JSON轉換物件
     * @param redisService       Redis緩存相關服務
     * @param assetHandler       資產處理器
     * @param currencyRepository 貨幣資料庫操作介面
     * @param stockTwRepository  台股資料庫操作介面
     * @param cryptoRepository   加密貨幣資料庫操作介面
     */
    public AssetService(AssetRepository assetRepository, AssetInfluxMethod assetInfluxMethod, ObjectMapper objectMapper, RedisService redisService, AssetHandler assetHandler, CurrencyRepository currencyRepository, StockTwRepository stockTwRepository, CryptoRepository cryptoRepository) {
        this.assetRepository = assetRepository;
        this.assetInfluxMethod = assetInfluxMethod;
        this.objectMapper = objectMapper;
        this.redisService = redisService;
        this.assetHandler = assetHandler;
        this.currencyRepository = currencyRepository;
        this.stockTwRepository = stockTwRepository;
        this.cryptoRepository = cryptoRepository;
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}")
    private String stockTwHistoryBucket;

    @Value("${db.influxdb.bucket.crypto_history}")
    private String cryptoHistoryBucket;

    @Value("${db.influxdb.bucket.common_economy}")
    private String commonEconomyBucket;

    @Value("${common.global_page_size:100}")
    private int pageSize;

    public Map<String, List<FluxTable>> getAssetKlineData(Long assetId, String type, String timestamp) throws AssetExceptions {
        Asset asset = getAssetById(assetId);
        return assetInfluxMethod.queryByAsset(asset, "history".equals(type), timestamp);
    }

    /**
     * 當資產歷史數據判斷邏輯
     * 當資產沒有數據時，設定緩存狀態為no_data
     * 當資產有數據時，設定緩存狀態為success
     *
     * @param assetId  資產Id
     * @param type     查詢類型
     * @param tableMap 資料表
     *
     * @return 是否有數據
     */
    public boolean checkNewDataMethod(Long assetId, String type, Map<String, List<FluxTable>> tableMap) throws RepositoryExceptions {
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("kline_%s", hashInnerKey);
        if (tableMap.get("%s_%s".formatted(assetId, type)).isEmpty()) {
            List<String> listCache = redisService.getCacheListValueFromKey(listKey + "data");
            if (listCache.isEmpty()) {
                redisService.saveHashToCache("kline", hashInnerKey + "status", "no_data", 48);
                return false;
            }
            redisService.saveHashToCache("kline", hashInnerKey + "status", "success", 48);
            return false;
        }
        return true;
    }

    /**
     * 將資產數據存儲到Redis中。
     *
     * @param tableMap     資料表。
     * @param key          緩存鍵。
     * @param hashInnerKey 緩存內部鍵。
     */
    @MeaninglessData
    public void saveAssetInfoToRedis(Map<String, List<FluxTable>> tableMap, String key, String hashInnerKey) {
        try {
            redisService.saveHashToCache("kline", hashInnerKey + "status", "processing", 48);
            List<String> klineDataMap = formatKlineTableByTime(tableMap);
            redisService.rPushToCacheList(key + "data", klineDataMap.getFirst(), 24);
            redisService.saveHashToCache("kline", hashInnerKey + "last_timestamp", klineDataMap.get(1), 48);
            redisService.saveHashToCache("kline", hashInnerKey + "status", "success", 48);
        } catch (Exception e) {
            redisService.saveHashToCache("kline", hashInnerKey + "status", "fail", 48);
            throw new FormatExceptions(ASSET_FORMAT_ERROR, tableMap);
        }
    }

    @MeaninglessData
    public List<String> formatKlineTableByTime(Map<String, List<FluxTable>> tableMap) throws JsonProcessingException, FormatExceptions {
        String lastTimePoint = null;
        List<AssetKlineDataDto> klineDataList = new ArrayList<>();
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
                    AssetKlineDataDto dataDto = klineDataList.stream()
                                                             .filter(klineDataDto -> klineDataDto.getTimestamp().equals(timestamp))
                                                             .findFirst()
                                                             .orElseGet(() -> {
                                                                 AssetKlineDataDto newDto = new AssetKlineDataDto();
                                                                 newDto.setTimestamp(timestamp);
                                                                 klineDataList.add(newDto);
                                                                 return newDto;
                                                             });
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
                                    throw new FormatExceptions(ASSET_FORMAT_ERROR, field);
                            }
                        }
                    }
                }
            }
        }
        String kLineJson = objectMapper.writeValueAsString(klineDataList);
        ArrayList<String> result = new ArrayList<>();
        result.add(kLineJson);
        result.add(lastTimePoint);
        return result;
    }

    /**
     * 獲取資產統計數據並存儲到Redis中。
     *
     * @param asset 資產對象。
     *
     * @return 資產統計數據列表。
     */
    public List<String> getAssetStatisticsAndSaveToRedis(Asset asset) throws AssetExceptions {
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
            default -> throw new AssetExceptions(ASSET_NOT_FOUND, asset);
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
                priceMap.put(entry.getKey(), null);
            } else {
                for (FluxTable table : entry.getValue()) {
                    for (FluxRecord record : table.getRecords()) {
                        if (record.getValueByKey("_value") instanceof Double doubleValue) {
                            BigDecimal value = BigDecimal.valueOf(doubleValue);
                            priceMap.put(entry.getKey(), String.format("%.6f", value));
                        } else {
                            priceMap.put(entry.getKey(), null);
                        }
                    }
                }
            }
        }
        priceMap.put(localDateList.getFirst(), (assetInfluxMethod.getLatestPrice(asset)).toString());
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

    /**
     * 獲取資產統計數據並存儲到Redis中。
     *
     * @param assetId 資產對象的識別Id。
     *
     * @return Asset 資產對象。
     *
     * @throws RuntimeException 當找不到資產時拋出異常。
     */
    public Asset getAssetById(Long assetId) throws AssetExceptions {
        return assetRepository.findById(assetId).orElseThrow(() -> new AssetExceptions(ASSET_NOT_FOUND, assetId));
    }

    /**
     * 轉換資產統計數據為JSON格式。
     *
     * @param cachedAssetJson 緩存資產JSON數據。
     * @param asset           資產對象。
     * @param user            用戶對象。
     *
     * @return 資產統計數據JSON。
     */
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
                default -> throw new AssetExceptions(ASSET_NOT_FOUND, asset);
            }
            return objectMapper.writeValueAsString(resultMap);
        } catch (Exception e) {
            throw new FormatExceptions(ASSET_FORMAT_ERROR, cachedAssetJson);
        }
    }

    /**
     * 獲取具有訂閱資產的資產列表
     *
     * @param crypto   是否包含加密貨幣
     * @param stockTw  是否包含台股
     * @param currency 是否包含貨幣
     *
     * @return 資產列表
     */
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

    /**
     * 根據資產類型獲取資產分頁數據
     *
     * @param category 資產類型
     * @param page     分頁
     * @param isCache  是否緩存
     *
     * @return 資產列表
     */
    @MeaninglessData
    public List<Asset> findAssetPageByType(String category, int page, boolean isCache) throws JsonProcessingException, AssetExceptions {
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

    /**
     * 轉換資產列表為前端類型並存儲到Redis中。
     * 前端格式 只需要包含資產名稱、資產Id、是否訂閱、資產類型
     *
     * @param assetList 資產列表
     * @param innerKey  內部鍵
     *
     * @return 資產列表JSON
     *
     * @throws JsonProcessingException 當JSON處理錯誤時拋出異常
     */
    @SuppressWarnings("unchecked")
    @MeaninglessData
    public String formatStringAssetListToFrontendType(List<?> assetList, String innerKey) throws JsonProcessingException, AssetExceptions, FormatExceptions {
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
                    default -> throw new AssetExceptions(ASSET_NOT_FOUND, asset);
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
            throw new FormatExceptions(ASSET_LIST_FORMAT_ERROR, assetList);
        }
        return cacheHashDataToRedis("frontendAssetList", innerKey, resultList, 24);
    }

    /**
     * 轉換緩存的資產列表JSON為資產列表
     *
     * @param assetJson 資產JSON
     *
     * @return 資產列表
     *
     * @throws JsonProcessingException 當JSON處理錯誤時拋出異常
     */
    @MeaninglessData
    public List<Map<String, Object>> formatJsonToAssetList(String assetJson) throws JsonProcessingException {
        if (assetJson == null) {
            return null;
        }
        return objectMapper.readValue(assetJson, new TypeReference<>() {});
    }

    /**
     * 轉換泛型數據為JSON格式並存儲到Redis中。
     *
     * @param key        緩存鍵
     * @param innerKey   緩存內部鍵
     * @param content    泛型數據
     * @param expireTime 過期時間
     *
     * @throws JsonProcessingException 當JSON處理錯誤時拋出異常
     */
    private <T> String cacheHashDataToRedis(String key, String innerKey, T content, int expireTime) throws JsonProcessingException {
        String json = formatContentToJson(content);
        redisService.saveHashToCache(key, innerKey, json, expireTime);
        return json;
    }

    /**
     * 轉換泛型數據為JSON格式並存儲到Redis中。
     *
     * @param key        緩存鍵
     * @param content    泛型數據
     * @param expireTime 過期時間
     *
     * @throws JsonProcessingException 當JSON處理錯誤時拋出異常
     */
    @MeaninglessData
    public <T> String cacheValueDataToRedis(String key, T content, int expireTime) throws JsonProcessingException {
        String json = formatContentToJson(content);
        redisService.saveValueToCache(key, json, expireTime);
        return json;
    }

    /**
     * 轉換泛型數據為JSON格式。
     *
     * @param content 泛型數據
     *
     * @return JSON數據
     *
     * @throws JsonProcessingException 當JSON處理錯誤時拋出異常
     */
    @MeaninglessData
    private <T> String formatContentToJson(T content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(content);
    }

    /**
     * 獲取資產總頁數
     *
     * @param category 資產類型
     * @param pageSize 每頁數量
     *
     * @return 總頁數
     */
    public int findAssetTotalPage(String category, int pageSize) throws AssetExceptions {
        PageRequest pageRequest = PageRequest.of(0, pageSize);
        return getAssetType(category) != null ? assetRepository.findAllByAssetType(getAssetType(category), pageRequest)
                                                               .getTotalPages() : assetRepository.findAll(pageRequest).getTotalPages();
    }

    /**
     * 獲取資產類型
     *
     * @param category 資產類型
     *
     * @return 資產類型
     */
    private AssetType getAssetType(String category) throws AssetExceptions {
        return switch (category) {
            case "crypto" -> AssetType.CRYPTO;
            case "stock_tw" -> AssetType.STOCK_TW;
            case "currency" -> AssetType.CURRENCY;
            case "all" -> null;
            default -> throw new AssetExceptions(ASSET_TYPE_NOT_FOUND, category);
        };
    }

    /**
     * 獲取政府債券數據並存儲到InfluxDB中。
     */
    public void GovernmentBondsDataFetcherAndSaveToInflux() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36");
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
        String url = "https://hk.investing.com/rates-bonds/world-government-bonds";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String result = response.getBody();
        Map<String, Map<String, BigDecimal>> governmentBondsMap = new HashMap<>();
        if (result != null) {
            Document document = Jsoup.parse(result);
            Elements tables = document.select("#leftColumn table");
            for (Element table : tables) {
                StringBuilder countryName = null;
                Map<String, BigDecimal> bondMap = new HashMap<>();
                Elements rows = table.select("tbody>tr");
                String period = "";
                for (Element row : rows) {
                    Element nameElement = row.select("td.plusIconTd a").first();
                    if (nameElement == null) {
                        continue;
                    }
                    String href = nameElement.attr("href");
                    String[] parts = href.split("/");
                    String[] lastPartSplit = parts[parts.length - 1].replace("-bond-yield", "").split("-");
                    Pattern pattern = Pattern.compile("\\d+");
                    Matcher matcher;
                    for (int i = lastPartSplit.length - 1; i >= 0; i--) {
                        matcher = pattern.matcher(lastPartSplit[i]);
                        if (matcher.find()) {
                            period = lastPartSplit[i] + (i + 1 < lastPartSplit.length ? "-" + lastPartSplit[i + 1] : "");
                            period = period.replace("years", "year");
                            period = period.replace("months", "month");
                            period = period.replace("weeks", "week");
                            if (countryName == null) {
                                countryName = new StringBuilder();
                                countryName.append(String.join("-", Arrays.copyOfRange(lastPartSplit, 0, i)));
                            }
                            break;
                        }
                    }
                    if (Objects.equals(lastPartSplit[lastPartSplit.length - 1], "overnight")) {
                        period = "overnight";
                    }
                    String yield = row.select("td").get(2).text();
                    if (countryName != null) {
                        bondMap.put(period, new BigDecimal(yield));
                    }
                }
                if (countryName != null) {
                    governmentBondsMap.put(countryName.toString().replace(".", ""), bondMap);
                }
            }
        }
        assetInfluxMethod.formatGovernmentBondsToPoint(governmentBondsMap);
    }

    /**
     * 獲取政府債券數據，此方法需要有序排序。
     * 排序以國家名稱為主，再以期限排序。
     *
     * @return 政府債券數據
     */
    public Map<String, Map<String, BigDecimal>> getGovernmentBondData() {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        List<FluxTable> tableList = assetInfluxMethod.queryByTimeAndUser(commonEconomyBucket,
                                                                         "government_bonds",
                                                                         null,
                                                                         null,
                                                                         List.of(now),
                                                                         48,
                                                                         true,
                                                                         false).get(now);
        for (FluxTable table : tableList) {
            for (FluxRecord record : table.getRecords()) {
                String country = (String) record.getValueByKey("country");
                String period = record.getField();
                Double doubleRate = (Double) record.getValue();
                BigDecimal rate;
                if (doubleRate != null) {
                    rate = BigDecimal.valueOf(doubleRate);
                } else {
                    continue;
                }
                result.computeIfAbsent(country, k -> new HashMap<>()).put(period, rate);
            }
        }
        Comparator<String> periodComparator = (p1, p2) -> {
            int value1 = periodToSortValue(p1);
            int value2 = periodToSortValue(p2);
            return Integer.compare(value1, value2);
        };
        for (Map.Entry<String, Map<String, BigDecimal>> entry : result.entrySet()) {
            Map<String, BigDecimal> sortedMap = new TreeMap<>(periodComparator);
            sortedMap.putAll(entry.getValue());
            result.put(entry.getKey(), sortedMap);
        }
        Map<String, Map<String, BigDecimal>> sortedResult = new TreeMap<>(result);
        return new LinkedHashMap<>(sortedResult);
    }

    /**
     * 格式化政府債券數據，此方法需要有序排序。
     * 排序以期限為主，再以國家名稱排序。
     *
     * @param governmentBondData 政府債券數據
     * @param <T>                泛型
     *
     * @return 格式化後的政府債券數據
     */
    public <T> Map<String, Map<String, BigDecimal>> formatGovernmentBondDataByTime(T governmentBondData) throws AssetExceptions {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> dataMap = checkGovernmentBondData(governmentBondData);
        List<String> sortedPeriods = dataMap.entrySet()
                                            .stream()
                                            .flatMap(entry -> entry.getValue().keySet().stream())
                                            .sorted(Comparator.comparingInt(this::periodToSortValue))
                                            .distinct()
                                            .toList();
        sortedPeriods.forEach(period -> dataMap.forEach((country, bondMap) -> {
            BigDecimal rate = bondMap.get(period);
            if (rate != null) {
                result.computeIfAbsent(period, k -> new TreeMap<>()).put(country, rate);
            }
        }));
        return result;
    }

    /**
     * 自訂義排序方法，將期限轉換為排序值。
     * 期限排序值為: 隔夜(0) < 1周(1) < 1個月(4) < 1年(52) (以周為單位)
     *
     * @param period 期限
     *
     * @return 排序值
     */
    private int periodToSortValue(String period) {
        if (period.contains("overnight")) {
            return 0;
        }
        if (period.contains("week")) {
            return Integer.parseInt(period.split("-")[0]);
        }
        if (period.contains("month")) {
            return Integer.parseInt(period.split("-")[0]) * 4;
        }
        if (period.contains("year")) {
            return Integer.parseInt(period.split("-")[0]) * 52;
        }
        throw new RuntimeException(new AssetExceptions(DEBT_DATA_UPDATE_ERROR, period));
    }

    /**
     * 檢查政府債券數據類型
     *
     * @param governmentBondData 政府債券數據
     * @param <T>                泛型
     *
     * @return 政府債券數據
     */
    @SuppressWarnings("unchecked")
    private <T> Map<String, Map<String, BigDecimal>> checkGovernmentBondData(T governmentBondData) throws AssetExceptions {
        if (governmentBondData instanceof String) {
            try {
                return objectMapper.readValue((String) governmentBondData, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new AssetExceptions(DEBT_DATA_RESOLVE_ERROR, e);
            }
        } else if (governmentBondData instanceof Map<?, ?>) {
            return (Map<String, Map<String, BigDecimal>>) governmentBondData;
        } else {
            throw new AssetExceptions(DEBT_DATA_RESOLVE_ERROR, governmentBondData);
        }
    }

    /**
     * 獲取資產列表前綴樹，當Redis中沒有資產前綴樹時，將從MySQL獲取資產數據並構建前綴樹
     *
     * @return 資產前綴樹
     */
    @MeaninglessData
    public Trie getAssetTrie() throws FormatExceptions {
        String assetTrie = redisService.getCacheValueFromKey("assetTrie");
        if (assetTrie == null) {
            return cacheTrieToRedis();
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(assetTrie));
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Trie) ois.readObject();
        } catch (Exception e) {
            throw new FormatExceptions(ASSET_TRIE_FORMAT_ERROR, e);
        }
    }

    /**
     * 轉換資產前綴樹成字串流並緩存到Redis中
     *
     * @return 資產前綴樹
     */
    public Trie cacheTrieToRedis() throws FormatExceptions {
        Trie trie = getAssetDataAndBuildTrie();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(trie);
            String formateString = Base64.getEncoder().encodeToString(bos.toByteArray());
            redisService.saveValueToCache("assetTrie", formateString, 48);
        } catch (Exception e) {
            throw new FormatExceptions(ASSET_TRIE_FORMAT_ERROR, e);
        }
        return trie;
    }

    /**
     * 獲取資產數據並構建前綴樹，將資產依照類型提取名稱，並構建前綴樹
     *
     * @return 資產前綴樹
     */
    private Trie getAssetDataAndBuildTrie() {
        List<AssetListDto> assetList = assetRepository.findAll().stream().flatMap(asset -> {
            List<Object[]> attribute = switch (asset.getAssetType()) {
                case CRYPTO: {
                    CryptoTradingPair cryptoTradingPair = (CryptoTradingPair) asset;
                    List<Object[]> list = new ArrayList<>();
                    list.add(new Object[]{cryptoTradingPair.getTradingPair(), cryptoTradingPair.isHasAnySubscribed()});
                    yield list;
                }
                case STOCK_TW: {
                    StockTw stockTw = (StockTw) asset;
                    List<Object[]> list = new ArrayList<>();
                    list.add(new Object[]{stockTw.getStockCode() + "_Stock_Tw", stockTw.isHasAnySubscribed()});
                    list.add(new Object[]{stockTw.getStockName() + "_Stock_Tw", stockTw.isHasAnySubscribed()});
                    yield list;
                }
                case CURRENCY: {
                    List<Object[]> list = new ArrayList<>();
                    list.add(new Object[]{((Currency) asset).getCurrency(), true});
                    yield list;
                }
            };
            return attribute.stream().map(objects -> new AssetListDto(asset.getId(), objects[0].toString(), (boolean) objects[1]));
        }).toList();
        Trie trie = new Trie();
        for (AssetListDto assetDto : assetList) {
            trie.insert(assetDto);
        }
        return trie;
    }

    /**
     * 根據關鍵字搜索資產列表
     *
     * @param keyword 關鍵字
     * @param trie    資產前綴樹
     *
     * @return 府和前綴的資產列表JSON
     */
    @MeaninglessData
    public String searchAssetList(String keyword, Trie trie) throws JsonProcessingException {
        List<AssetListDto> searchResult = trie.search(keyword.toUpperCase());
        return objectMapper.writeValueAsString(searchResult);
    }

    /**
     * 根據資產名稱獲取資產對象，若找不到則返回null
     *
     * @param assetName 資產名稱
     *
     * @return 資產對象
     */
    public Asset getAssetByAssetName(String assetName) {
        Optional<CryptoTradingPair> crypto = cryptoRepository.findByTradingPair(assetName);
        if (crypto.isPresent()) {
            return crypto.get();
        }
        Optional<StockTw> stockTw = stockTwRepository.findByStockCode(assetName);
        if (stockTw.isPresent()) {
            return stockTw.get();
        }
        Optional<Currency> currency = currencyRepository.findByCurrency(assetName);
        return currency.orElse(null);
    }
}
