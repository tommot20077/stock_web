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

    @Value("${db.influxdb.bucket}")
    private String bucket;


    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }
}
