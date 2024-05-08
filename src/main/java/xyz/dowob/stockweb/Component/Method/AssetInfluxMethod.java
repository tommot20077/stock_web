package xyz.dowob.stockweb.Component.Method;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yuan
 */
@Component
public class AssetInfluxMethod {
    private final InfluxDBClient stockTwInfluxClient;

    private final InfluxDBClient cryptoInfluxClient;

    private final InfluxDBClient currencyInfluxClient;

    private final InfluxDBClient stockTwHistoryInfluxClient;

    private final InfluxDBClient cryptoHistoryInfluxClient;

    private final InfluxDBClient propertySummaryInfluxClient;

    private final CurrencyRepository currencyRepository;

    private final RetryTemplate retryTemplate;


    @Value("${db.influxdb.bucket.crypto}")
    private String cryptoBucket;

    @Value("${db.influxdb.bucket.crypto_history}")
    private String cryptoHistoryBucket;

    @Value("${db.influxdb.bucket.stock_tw}")
    private String stockTwBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}")
    private String stockHistoryBucket;

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.org}")
    private String org;

    @Value("${db.influxdb.bucket.crypto_history.dateline:20180101}")
    private String cryptoHistoryDateline;

    @Value("${db.influxdb.bucket.stock_tw_history.dateline:20110101}")
    private String stockHistoryDateline;

    Logger logger = LoggerFactory.getLogger(AssetInfluxMethod.class);

    @Autowired
    public AssetInfluxMethod(
            @Qualifier("StockTwInfluxClient") InfluxDBClient stockTwInfluxClient, @Qualifier("StockTwHistoryInfluxClient") InfluxDBClient stockTwHistoryInfluxClient, @Qualifier("CryptoInfluxClient") InfluxDBClient cryptoInfluxClient, @Qualifier("CryptoHistoryInfluxClient") InfluxDBClient cryptoHistoryInfluxClient, @Qualifier("CurrencyInfluxClient") InfluxDBClient currencyInfluxClient, @Qualifier("propertySummaryInfluxClient") InfluxDBClient propertySummaryInfluxClient, CurrencyRepository currencyRepository, RetryTemplate retryTemplate) {
        this.stockTwInfluxClient = stockTwInfluxClient;
        this.cryptoInfluxClient = cryptoInfluxClient;
        this.currencyInfluxClient = currencyInfluxClient;
        this.stockTwHistoryInfluxClient = stockTwHistoryInfluxClient;
        this.cryptoHistoryInfluxClient = cryptoHistoryInfluxClient;
        this.propertySummaryInfluxClient = propertySummaryInfluxClient;
        this.currencyRepository = currencyRepository;
        this.retryTemplate = retryTemplate;
    }

    /**
     * 取得資料庫和客戶端，以及查詢條件
     *
     * @param asset          資產
     * @param useHistoryData 是否使用歷史資料
     *
     * @return Object[] {bucket, client, measurement, field, assetType, symbol}
     *
     * @throws RuntimeException 重試失敗時的最後一次錯誤
     */

    private Object[] getBucketAndClient(Asset asset, boolean useHistoryData) {
        Object bucket, client;
        String klineDataKey = "kline_data", rateKey = "exchange_rate";
        String closeKey = "close", rateTypeKey = "rate";
        String tradingPairKey = "tradingPair", currencyKey = "Currency", stockCodeKey = "stock_tw";

        return switch (asset.getAssetType()) {
            case CRYPTO -> {
                CryptoTradingPair cryptoTradingPair = (CryptoTradingPair) asset;
                bucket = useHistoryData ? cryptoHistoryBucket : cryptoBucket;
                client = useHistoryData ? cryptoHistoryInfluxClient : cryptoInfluxClient;
                yield new Object[]{bucket, client, klineDataKey, closeKey, tradingPairKey, cryptoTradingPair.getTradingPair()};
            }
            case CURRENCY -> {
                Currency currency = (Currency) asset;
                bucket = currencyBucket;
                client = currencyInfluxClient;
                yield new Object[]{bucket, client, rateKey, rateTypeKey, currencyKey, currency.getCurrency()};
            }
            case STOCK_TW -> {
                StockTw stockTw = (StockTw) asset;
                bucket = useHistoryData ? stockHistoryBucket : stockTwBucket;
                client = useHistoryData ? stockTwHistoryInfluxClient : stockTwInfluxClient;
                yield new Object[]{bucket, client, klineDataKey, closeKey, stockCodeKey, stockTw.getStockCode()};
            }
        };
    }

    /**
     * 查詢最新價格
     *
     * @param asset          資產
     * @param useHistoryData 是否使用歷史資料
     *
     * @return FluxTable
     *
     * @throws RuntimeException 重試失敗時的最後一次錯誤
     */
    private List<FluxTable> queryLatestPrice(Asset asset, boolean useHistoryData) throws RuntimeException {
        try {
            var ref = new Object() {
                List<FluxTable> tables;
            };
            retryTemplate.doWithRetry(() -> {
                Object[] bucketAndClient = getBucketAndClient(asset, useHistoryData);
                String bucket = (String) bucketAndClient[0];
                InfluxDBClient client = (InfluxDBClient) bucketAndClient[1];
                String measurement = (String) bucketAndClient[2];
                String field = (String) bucketAndClient[3];
                String assetType = (String) bucketAndClient[4];
                String symbol = (String) bucketAndClient[5];
                String query = String.format("from(bucket: \"%s\") " + " |> range(start: -7d)" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" + " |> filter(fn: (r) => r[\"_field\"] == \"%s\")" + " |> filter(fn: (r) => r[\"%s\"] == \"%s\")" + " |> last()",
                                             bucket,
                                             measurement,
                                             field,
                                             assetType,
                                             symbol);
                logger.debug("取得價格的查詢條件: " + query);
                ref.tables = client.getQueryApi().query(query, org);
            });
            return ref.tables;
        } catch (RetryException e) {
            Exception lastException = e.getLastException();
            logger.error("重試失敗，最後一次錯誤信息：" + lastException.getMessage(), lastException);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + lastException.getMessage(), lastException);
        }
    }

    /**
     * 取得最新價格
     *
     * @param asset 資產
     *
     * @return 最新價格
     */
    public BigDecimal getLatestPrice(Asset asset) {
        logger.debug("取得最新價格" + asset);
        List<FluxTable> tables = queryLatestPrice(asset, false);
        logger.debug("取得最新價格結果: " + tables);

        Currency twd = currencyRepository.findByCurrency("TWD").orElseThrow(() -> new RuntimeException("找不到 TWD"));

        if (tables.isEmpty() || tables.getFirst().getRecords().isEmpty()) {
            if (asset.getAssetType() == AssetType.CURRENCY) {
                logger.debug("可能貨幣資料對更新時間太久，改以從MySQL取得: " + asset);
                Currency currency = (Currency) asset;
                if (currency.getExchangeRate() != null) {
                    return BigDecimal.ONE.divide(currency.getExchangeRate(), 6, RoundingMode.HALF_UP);
                }
            } else {
                logger.debug("取得最新價格失敗 + " + asset);
                logger.debug("改成使用最新的歷史資料");
                List<FluxTable> historyTables = queryLatestPrice(asset, true);
                logger.debug("取得最新歷史價格結果: " + historyTables);

                if (historyTables.isEmpty() || historyTables.getFirst().getRecords().isEmpty()) {
                    logger.debug("取得最新歷史價格失敗 + " + asset);
                    return BigDecimal.valueOf(-1);
                }
                FluxRecord historyRecord = historyTables.getFirst().getRecords().getFirst();
                Object historyValue = historyRecord.getValueByKey("_value");
                if (historyValue instanceof Number) {
                    if (asset instanceof StockTw) {
                        return BigDecimal.valueOf(((Number) historyValue).doubleValue())
                                         .divide(twd.getExchangeRate(), 6, RoundingMode.HALF_UP);
                    } else {
                        return BigDecimal.valueOf(((Number) historyValue).doubleValue());
                    }
                } else {
                    logger.debug("取得最新歷史價格失敗 + " + asset);
                    return BigDecimal.valueOf(-1);
                }
            }
            logger.debug("取得最新價格失敗 + " + asset);
            return BigDecimal.valueOf(-1);
        }
        FluxRecord record = tables.getFirst().getRecords().getFirst();
        Object value = record.getValueByKey("_value");
        logger.debug("取得價格為: " + value);

        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } else {
            logger.debug("取得最新歷史價格失敗 + " + asset);
            return BigDecimal.valueOf(-1);
        }
    }


    /**
     * 寫入InfluxDB
     *
     * @param influxClient InfluxDB客戶端
     * @param point        資料點
     */
    public void writeToInflux(InfluxDBClient influxClient, Point point) {
        try {
            retryTemplate.doWithRetry(() -> {
                try {
                    logger.debug("寫入InfluxDB: " + point);
                    try (WriteApi writeApi = influxClient.makeWriteApi()) {
                        writeApi.writePoint(point);
                        logger.debug("寫入InfluxDB成功");
                    }
                } catch (Exception e) {
                    logger.error("寫入InfluxDB時發生錯誤", e);
                    throw new RuntimeException("寫入InfluxDB時發生錯誤", e);
                }
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }

    /**
     * 查詢資產價格
     *
     * @param asset     資產
     * @param isHistory 是否查詢歷史資料
     * @param timeStamp 時間戳
     *
     * @return Map<String, List < FluxTable>> {assetId + "_history/current", FluxTable}
     */
    public Map<String, List<FluxTable>> queryByAsset(Asset asset, Boolean isHistory, String timeStamp) {
        var ref = new Object() {
            List<FluxTable> tables;
        };
        Object[] bucketAndClient = getBucketAndClient(asset, isHistory);
        DateTimeFormatter outFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'");
        DateTimeFormatter cryptoAndStockTwFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String start;

        if (timeStamp != null) {
            start = timeStamp;
        } else {
            if (asset.getAssetType() == AssetType.CRYPTO) {
                start = LocalDateTime.from(LocalDate.parse(cryptoHistoryDateline, cryptoAndStockTwFormatter).atStartOfDay())
                                     .format(outFormatter);
            } else if (asset.getAssetType() == AssetType.STOCK_TW) {
                start = LocalDateTime.from(LocalDate.parse(stockHistoryDateline, cryptoAndStockTwFormatter).atStartOfDay())
                                     .format(outFormatter);
            } else {
                start = "1970-01-01T00:00:00.000Z";
            }
        }
        logger.debug("取得價格的起始時間: " + start);

        try {
            retryTemplate.doWithRetry(() -> {
                String bucket = (String) bucketAndClient[0];
                InfluxDBClient client = (InfluxDBClient) bucketAndClient[1];
                String measurement = (String) bucketAndClient[2];
                String assetType = (String) bucketAndClient[4];
                String symbol = (String) bucketAndClient[5];
                String query = String.format("from(bucket: \"%s\") " + " |> range(start: %s, stop: now())" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" + " |> filter(fn: (r) => r[\"%s\"] == \"%s\")",
                                             bucket,
                                             start,
                                             measurement,
                                             assetType,
                                             symbol);
                logger.debug("取得價格的查詢條件: " + query);
                ref.tables = client.getQueryApi().query(query, org);
            });

            String addition = isHistory ? "history" : "current";
            return new HashMap<>() {{
                put(asset.getId().toString() + "_" + addition, ref.tables);
            }};
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }

    /**
     * 查詢資產價格
     *
     * @param propertySummaryBucket 用戶總資產資料庫
     * @param measurement           資料表
     * @param filters               過濾條件, key: 欄位名, value: 欄位值
     *                              例如: { "user_id": "1" }
     *                              會被轉換成 |> filter(fn: (r) => r["user_id"] == "1")
     * @param user                  用戶
     *                              如果為null則不過濾用戶
     * @param specificTimes         指定時間
     * @param allowRangeOfHour      允許的時間誤差範圍
     * @param isLast                是否只只要最新的資料點
     * @param needToFillData        是否需要自動填充資料
     *
     * @return Map<String, List < FluxTable>> {assetId + "_history/current", FluxTable}
     *
     * @throws RuntimeException 重試失敗時的最後一次錯誤
     */
    public Map<LocalDateTime, String> createInquiryPredicateWithUserAndSpecificTimes(
            String propertySummaryBucket, String measurement, Map<String, String> filters, User user, List<LocalDateTime> specificTimes, int allowRangeOfHour, boolean isLast, boolean needToFillData) {

        Map<LocalDateTime, String> queries = new HashMap<>();
        DateTimeFormatter influxDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
        for (LocalDateTime specificTime : specificTimes) {
            LocalDateTime rangeStart = specificTime.minusHours(allowRangeOfHour);
            LocalDateTime rangeEnd = specificTime.plusMinutes(30);

            String formattedStart = influxDateFormat.format(rangeStart);
            String formattedEnd = influxDateFormat.format(rangeEnd);

            StringBuilder baseQuery = new StringBuilder(String.format("from(bucket: \"%s\")" + " |> range(start: %s, stop: %s)" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")",
                                                                      propertySummaryBucket,
                                                                      formattedStart,
                                                                      formattedEnd,
                                                                      measurement));
            if (isLast) {
                baseQuery.append(" |> last()");
            }
            if (user != null) {
                String additionalQuery = String.format(" |> filter(fn: (r) => r[\"user_id\"] == \"%s\")", user.getId());
                baseQuery.append(additionalQuery);
            }


            if (needToFillData) {
                baseQuery.append(" |> aggregateWindow(every: 1h, fn: mean, createEmpty: true)" + " |> fill(usePrevious: true)");
            }
            if (!filters.isEmpty()) {
                for (Map.Entry<String, String> additionalFilters : filters.entrySet()) {
                    String additionFilterKey = additionalFilters.getKey();
                    String additionFilterValue = additionalFilters.getValue();
                    String additionalQuery = String.format("  |> filter(fn: (r) => r[\"%s\"] == \"%s\")",
                                                           additionFilterKey,
                                                           additionFilterValue);
                    baseQuery.append(additionalQuery);
                }
            }
            queries.put(specificTime, baseQuery.toString());
        }
        logger.debug("queries: " + queries);
        return queries;
    }

    /**
     * 查詢資產價格根據時間跟用戶
     *
     * @param bucket           使用的資料庫
     * @param measurement      資料表
     * @param filters          過濾條件, key: 欄位名, value: 欄位值
     * @param user             用戶
     * @param specificTimes    指定時間
     * @param allowRangeOfHour 允許的時間誤差範圍
     * @param isLast           是否只只要最新的資料點
     * @param needToFillData   是否需要自動填充資料
     *
     * @return Map<String, List < FluxTable>> {assetId + "_history/current", FluxTable}
     */

    public Map<LocalDateTime, List<FluxTable>> queryByTimeAndUser(String bucket, String measurement, Map<String, String> filters, User user, List<LocalDateTime> specificTimes, int allowRangeOfHour, boolean isLast, boolean needToFillData) {
        Map<LocalDateTime, List<FluxTable>> userTablesMap = new HashMap<>();
        Map<LocalDateTime, String> predicate = createInquiryPredicateWithUserAndSpecificTimes(bucket,
                                                                                              measurement,
                                                                                              filters,
                                                                                              user,
                                                                                              specificTimes,
                                                                                              allowRangeOfHour,
                                                                                              isLast,
                                                                                              needToFillData);

        for (Map.Entry<LocalDateTime, String> entry : predicate.entrySet()) {
            LocalDateTime specificTime = entry.getKey();
            String query = entry.getValue();

            var ref = new Object() {
                List<FluxTable> result;
            };
            try {
                retryTemplate.doWithRetry(() -> {
                    ref.result = propertySummaryInfluxClient.getQueryApi().query(query, org);
                });
            } catch (RetryException e) {
                logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
                throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
            }
            userTablesMap.put(specificTime, ref.result);
        }
        return userTablesMap;
    }

    /**
     * 取得ROI統計日期
     *
     * @return List<LocalDateTime>
     */
    public List<LocalDateTime> getStatisticDate() {
        LocalDateTime today = LocalDateTime.now();
        List<LocalDateTime> localDateTime = new ArrayList<>();
        localDateTime.add(today);
        localDateTime.add(today.minusDays(1));
        localDateTime.add(today.minusWeeks(1));
        localDateTime.add(today.minusMonths(1));
        localDateTime.add(today.minusYears(1));
        return localDateTime;
    }
}
