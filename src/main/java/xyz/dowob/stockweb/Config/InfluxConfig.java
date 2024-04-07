package xyz.dowob.stockweb.Config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author tommo
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

    @Value("${db.influxdb.bucket.stock}")
    private String stockBucket;

    @Value("${db.influxdb.bucket.stock_history}")
    private String stockHistoryBucket;

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.bucket.property_summary}")
    private String propertySummaryBucket;



    @Bean(name = "CryptoInfluxClient")
    public InfluxDBClient cryptoInfluxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, cryptoBucket);
    }

    @Bean(name = "CryptoHistoryInfluxClient")
    public InfluxDBClient cryptoHistoryBucket() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, cryptoHistoryBucket);
    }
    @Bean(name = "StockTwInfluxClient")
    public InfluxDBClient stockTwInfluxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, stockBucket);
    }
    @Bean(name = "StockTwHistoryInfluxDBClient")
    public InfluxDBClient stockTwHistoryInfluxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, stockHistoryBucket);
    }
    @Bean(name = "CurrencyInfluxDBClient")
    public InfluxDBClient currencyInfluxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, currencyBucket);
    }

    @Bean(name = "propertySummaryInfluxDBClient")
    public InfluxDBClient propertySummaryInfluxClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, propertySummaryBucket);
    }
}
