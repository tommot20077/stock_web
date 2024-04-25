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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AssetInfluxMethod {
    private final InfluxDBClient stockTwInfluxClient;
    private final InfluxDBClient cryptoInfluxClient;
    private final InfluxDBClient currencyInfluxClient;
    private final InfluxDBClient stockTwHistoryInfluxClient;
    private final InfluxDBClient cryptoHistoryInfluxClient;
    private final RetryTemplate retryTemplate;


    @Value("${db.influxdb.bucket.crypto}")
    private String cryptoBucket;

    @Value("${db.influxdb.bucket.crypto_history}")
    private String cryptoHistoryBucket;

    @Value("${db.influxdb.bucket.stock}")
    private String stockTwBucket;

    @Value("${db.influxdb.bucket.stock_history}")
    private String stockHistoryBucket;

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.org}")
    private String org;

    @Value("${db.influxdb.bucket.crypto_history.dateline}")
    private String cryptoHistoryDateline;

    @Value("${db.influxdb.bucket.stock_tw_history.dateline}")
    private String stockHistoryDateline;

    Logger logger = LoggerFactory.getLogger(AssetInfluxMethod.class);

    @Autowired
    public AssetInfluxMethod(
            @Qualifier("StockTwInfluxClient") InfluxDBClient stockTwInfluxClient, @Qualifier("StockTwHistoryInfluxClient") InfluxDBClient stockTwHistoryInfluxClient, @Qualifier("CryptoInfluxClient") InfluxDBClient cryptoInfluxClient, @Qualifier("CryptoHistoryInfluxClient") InfluxDBClient cryptoHistoryInfluxClient, @Qualifier("CurrencyInfluxClient") InfluxDBClient currencyInfluxClient, RetryTemplate retryTemplate) {
        this.stockTwInfluxClient = stockTwInfluxClient;
        this.cryptoInfluxClient = cryptoInfluxClient;
        this.currencyInfluxClient = currencyInfluxClient;
        this.stockTwHistoryInfluxClient = stockTwHistoryInfluxClient;
        this.cryptoHistoryInfluxClient = cryptoHistoryInfluxClient;
        this.retryTemplate = retryTemplate;
    }


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
                String query = String.format("from(bucket: \"%s\") " + " |> range(start: -7d)" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" + " |> filter(fn: (r) => r[\"_field\"] == \"%s\")" + " |> filter(fn: (r) => r[\"%s\"] == \"%s\")" + " |> last()", bucket, measurement, field, assetType, symbol);
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

    public BigDecimal getLatestPrice(Asset asset) {
        logger.debug("取得最新價格" + asset);
        List<FluxTable> tables = queryLatestPrice(asset, false);
        logger.debug("取得最新價格結果: " + tables);
        if (tables.isEmpty() || tables.getFirst().getRecords().isEmpty()) {
            if (asset.getAssetType() == AssetType.CURRENCY) {
                logger.debug("可能貨幣資料對更新時間太久，改以從MySQL取得: " + asset);
                Currency currency = (Currency) asset;
                if (currency.getExchangeRate() != null) {
                    return currency.getExchangeRate();
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
                    return BigDecimal.valueOf(((Number) historyValue).doubleValue());
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
                start = LocalDateTime.from(LocalDate.parse(cryptoHistoryDateline, cryptoAndStockTwFormatter).atStartOfDay()).format(outFormatter);
            } else if (asset.getAssetType() == AssetType.STOCK_TW) {
                start = LocalDateTime.from(LocalDate.parse(stockHistoryDateline, cryptoAndStockTwFormatter).atStartOfDay()).format(outFormatter);
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
                String query = String.format("from(bucket: \"%s\") " + " |> range(start: %s, stop: now())" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" + " |> filter(fn: (r) => r[\"%s\"] == \"%s\")", bucket, start, measurement, assetType, symbol);
                logger.debug("取得價格的查詢條件: " + query);
                ref.tables = client.getQueryApi().query(query, org);
            });

            String addition = isHistory ? "history" : "latest";
            return new HashMap<>() {{
                put(asset.getId().toString() + "_" + addition, ref.tables);
            }};
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }
}
