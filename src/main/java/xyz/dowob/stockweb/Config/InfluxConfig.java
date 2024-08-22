package xyz.dowob.stockweb.Config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author yuan
 * InfluxDB設定
 * 1. 連線資訊
 * 2. Token
 * 3. 組織
 * 4. Bucket
 * 5. 連線逾時
 * 6. 讀寫逾時
 */
@Configuration
public class InfluxConfig {
    @Value("${db.influxdb.url}")
    private String url;

    @Value("${db.influxdb.token}")
    private String token;

    @Value("${db.influxdb.org}")
    private String org;

    @Value("${db.influxdb.bucket.crypto}")
    private String cryptoBucket;

    @Value("${db.influxdb.bucket.crypto_history}")
    private String cryptoHistoryBucket;

    @Value("${db.influxdb.bucket.stock_tw}")
    private String stockTwBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}")
    private String stockTwHistoryBucket;

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.bucket.property_summary}")
    private String propertySummaryBucket;

    @Value("${db.influxdb.bucket.common_economy}")
    private String commonEconomyBucket;

    @Value("${db.influxdb.connect_timeout:60}")
    private int influxConnectTimeout;

    @Value("${db.influxdb.read_write_timeout:30}")
    private int influxReadTimeout;


    /**
     * 創建InfluxDBClient, 連線至cryptoBucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "CryptoInfluxClient")
    public InfluxDBClient cryptoInfluxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, cryptoBucket);
    }

    /**
     * 創建InfluxDBClient, 連線至cryptoHistoryBucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "CryptoHistoryInfluxClient")
    public InfluxDBClient cryptoHistoryBucket() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, cryptoHistoryBucket);
    }

    /**
     * 創建InfluxDBClient, 連線至stockTwBucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "StockTwInfluxClient")
    public InfluxDBClient stockTwInfluxClient() {
        return createClient(stockTwBucket);
    }

    /**
     * 創建InfluxDBClient, 連線至stockTwHistoryBucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "StockTwHistoryInfluxClient")
    public InfluxDBClient stockTwHistoryInfluxClient() {
        return createClient(stockTwHistoryBucket);
    }

    /**
     * 創建InfluxDBClient, 連線至currencyBucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "CurrencyInfluxClient")
    public InfluxDBClient currencyInfluxClient() {
        return createClient(currencyBucket);
    }

    /**
     * 創建InfluxDBClient, 連線至propertySummaryBucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "propertySummaryInfluxClient")
    public InfluxDBClient propertySummaryInfluxClient() {
        return createClient(propertySummaryBucket);
    }

    /**
     * 創建InfluxDBClient, 連線至commonEconomyData
     *
     * @return InfluxDBClient
     */
    @Bean(name = "commonEconomyInfluxClient")
    public InfluxDBClient governmentBondsInfluxClient() {
        return createClient(commonEconomyBucket);
    }


    /**
     * 創建InfluxDBClient, 連線至預設bucket
     *
     * @return InfluxDBClient
     */
    @Bean(name = "influxClient")
    public InfluxDBClient influxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org);
    }

    /**
     * 創建InfluxDBClient
     *
     * @param bucketName bucket名稱
     *
     * @return InfluxDBClient
     */
    private InfluxDBClient createClient(String bucketName) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().connectTimeout(influxConnectTimeout,
                                                                                 java.util.concurrent.TimeUnit.SECONDS)
                                                                 .readTimeout(influxReadTimeout, java.util.concurrent.TimeUnit.SECONDS)
                                                                 .writeTimeout(influxReadTimeout, java.util.concurrent.TimeUnit.SECONDS);


        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                                                             .url(url)
                                                             .authenticateToken(token.toCharArray())
                                                             .org(org)
                                                             .bucket(bucketName)
                                                             .okHttpClient(builder)
                                                             .build();

        return InfluxDBClientFactory.create(options);
    }

    /**
     * 測試用bucket資料庫
     */
    @Bean(name = "testInfluxClient")
    public InfluxDBClient testInfluxClient() {
        return createClient("test_data");
    }
}
