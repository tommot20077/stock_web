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



    @Bean(name = "CryptoInfluxDBClient")
    public InfluxDBClient cryptoInfluxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, cryptoBucket);
    }

    @Bean(name = "StockInfluxDBClient")
    public InfluxDBClient stockInfluxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, stockBucket);
    }
}
