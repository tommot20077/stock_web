package xyz.dowob.stockweb.Component.Method.Initial;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.Bucket;
import com.influxdb.client.domain.Organization;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author yuan
 */
@Component
public class InfluxInitial {
    @Value("${db.influxdb.url}") private String url;

    @Value("${db.influxdb.token}") private String token;

    @Value("${db.influxdb.org}") private String org;

    @Value("${db.influxdb.bucket.crypto}") private String cryptoBucket;

    @Value("${db.influxdb.bucket.crypto_history}") private String cryptoHistoryBucket;

    @Value("${db.influxdb.bucket.stock_tw}") private String stockTwBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}") private String stockTwHistoryBucket;

    @Value("${db.influxdb.bucket.currency}") private String currencyBucket;

    @Value("${db.influxdb.bucket.property_summary}") private String propertySummaryBucket;

    @Value("${db.influxdb.org_id}") private String orgId;

    private final InfluxDBClient influxClient;
    Logger logger = LoggerFactory.getLogger(InfluxInitial.class);


    public InfluxInitial(@Qualifier("influxClient") InfluxDBClient influxClient) {
        this.influxClient = influxClient;
    }

    @PostConstruct
    public void init() {
        List<String> buckets = List.of(cryptoBucket,
                                    cryptoHistoryBucket,
                                    stockTwBucket,
                                    stockTwHistoryBucket,
                                    currencyBucket,
                                    propertySummaryBucket);

        List<String> keys = List.of(org,
                                    url,
                                    token);

        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                logger.info("請先配置設定" + key);
                throw new IllegalStateException("請先配置設定" + key);
            }
        }

        for (String bucket : buckets) {
            logger.debug("檢查influx配置: " + bucket);
            if (bucket == null || bucket.isEmpty()) {
                logger.warn("請先配置設定" + bucket);
                throw new IllegalStateException("請先配置設定" + bucket);
            }
            try {
                if (!checkBucketExists(bucket)) {
                    logger.info("influx沒有指定的bucket，建立bucket: " + bucket);
                    createBucket(bucket);
                }
            } catch (Exception e) {
                logger.error("influx建立bucket失敗: " + bucket, e);
            }
        }
    }

    private boolean checkBucketExists(String bucketName) {
        Bucket bucket = influxClient.getBucketsApi().findBucketByName(bucketName);
        return bucket != null;
    }

    private void createBucket(String bucketName) {
        influxClient.getBucketsApi().createBucket(bucketName, orgId);
    }
}
