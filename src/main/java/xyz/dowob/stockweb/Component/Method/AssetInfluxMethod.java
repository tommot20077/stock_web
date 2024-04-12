package xyz.dowob.stockweb.Component.Method;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AssetInfluxMethod {
    private final InfluxDBClient stockTwInfluxClient;
    private final InfluxDBClient cryptoInfluxClient;
    private final InfluxDBClient currencyInfluxClient;
    private final CurrencyRepository currencyRepository;

    @Value("${db.influxdb.bucket.crypto}")
    private String cryptoBucket;

    @Value("${db.influxdb.bucket.stock}")
    private String stockTwBucket;

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.org}")
    private String org;
    Logger logger = LoggerFactory.getLogger(AssetInfluxMethod.class);

    @Autowired
    public AssetInfluxMethod(@Qualifier("StockTwInfluxClient") InfluxDBClient stockTwInfluxClient,
                             @Qualifier("CryptoInfluxClient") InfluxDBClient cryptoInfluxClient,
                             @Qualifier("CurrencyInfluxClient") InfluxDBClient currencyInfluxClient, CurrencyRepository currencyRepository) {
        this.stockTwInfluxClient = stockTwInfluxClient;
        this.cryptoInfluxClient = cryptoInfluxClient;
        this.currencyInfluxClient = currencyInfluxClient;
        this.currencyRepository = currencyRepository;
    }



    private Object[] getBucketAndClient(Asset asset) {
        switch (asset.getAssetType()) {
            case CRYPTO:
                CryptoTradingPair cryptoTradingPair = (CryptoTradingPair) asset;
                return new Object[]{cryptoBucket, cryptoInfluxClient, "kline_data", "close", "tradingPair", cryptoTradingPair.getTradingPair()};
            case CURRENCY:
                Currency currency = (Currency) asset;
                return new Object[]{currencyBucket, currencyInfluxClient, "exchange_rate", "rate", "Currency", currency.getCurrency()};
            case STOCK_TW:
                StockTw stockTw = (StockTw) asset;
                return new Object[]{stockTwBucket, stockTwInfluxClient, "kline_data", "price", "stock_tw", stockTw.getStockCode()};
            default:
                throw new IllegalArgumentException("不支援的資產類型");
        }
    }

    private List<FluxTable> queryLatestPrice(Asset asset) {
        Object[] bucketAndClient = getBucketAndClient(asset);
        String bucket = (String) bucketAndClient[0];
        InfluxDBClient client = (InfluxDBClient) bucketAndClient[1];
        String measurement = (String) bucketAndClient[2];
        String field = (String) bucketAndClient[3];
        String assetType = (String) bucketAndClient[4];
        String symbol = (String) bucketAndClient[5];


        String query = String.format(
                "from(bucket: \"%s\") |> range(start: -7d)" +
                        " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" +
                        " |> filter(fn: (r) => r[\"_field\"] == \"%s\")" +
                        " |> filter(fn: (r) => r[\"%s\"] == \"%s\")" +
                        " |> last()",
                bucket, measurement, field, assetType, symbol
                );
        return client.getQueryApi().query(query, org);
    }

    public BigDecimal getLatestPrice(Asset asset) {
        logger.debug("取得最新價格" + asset);
        List<FluxTable> tables = queryLatestPrice(asset);
        logger.debug("取得最新價格結果: " + tables);
        if (tables.isEmpty() || tables.getFirst().getRecords().isEmpty()) {
            if (asset.getAssetType() == AssetType.CURRENCY) {
                logger.debug("可能貨幣資料對更新時間太久，改以從MySQL取得: " + asset);
                Currency currency = (Currency) asset;
                if (currency.getExchangeRate() != null) {
                    return currency.getExchangeRate();
                }
            }
            logger.debug("取得最新價格失敗 + " + asset);
            return BigDecimal.valueOf(-1);
        }
        FluxRecord record = tables.getFirst().getRecords().getFirst();
        Object value = record.getValueByKey("_value");

        if(value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } else {
            logger.debug("取得最新價格失敗 + " + asset);
            return BigDecimal.valueOf(-1);
        }
    }
}
