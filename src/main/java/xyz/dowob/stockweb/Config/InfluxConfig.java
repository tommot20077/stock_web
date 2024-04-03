package xyz.dowob.stockweb.Config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Value("${db.influxdb.bucket.stock}")
    private String stockBucket;

    @Value("${db.influxdb.bucket.currency}")
    private String currencyBucket;

    @Value("${db.influxdb.bucket.property_summary}")
    private String propertySummaryBucket;



    @Bean(name = "CryptoInfluxDBClient")
    public InfluxDBClient cryptoInfluxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, cryptoBucket);
    }

    @Bean(name = "StockTwInfluxDBClient")
    public InfluxDBClient StockTwInfluxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, stockBucket);
    }
    @Bean(name = "CurrencyInfluxDBClient")
    public InfluxDBClient CurrencyInfluxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, currencyBucket);
    }

    @Bean(name = "propertySummaryInfluxDBClient")
    public InfluxDBClient propertySummaryInfluxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, propertySummaryBucket);
    }
}
