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
 * 這是一個初始化類，用於在應用程序啟動時創建InfluxDB的bucket。
 *
 * @author yuan
 */
@Component
public class InfluxInitial {
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

    private final InfluxDBClient influxClient;

    Logger log = LoggerFactory.getLogger(InfluxInitial.class);

    /**
     * 這是一個構造函數，用於注入InfluxDBClient，此注入客戶端不包含Bucket參數。
     *
     * @param influxClient influxDB客戶端
     */
    public InfluxInitial(
            @Qualifier("influxClient") InfluxDBClient influxClient) {
        this.influxClient = influxClient;
    }

    /**
     * 這是一個初始化方法，會在類別實例化後自動執行。首先，它會創建一個包含所有bucket名稱的列表和一個包含所有鍵的列表。
     * 它會獲取組織的ID。對於每一個鍵，如果該鍵為null或空，則會輸出一條日誌訊息並拋出異常。
     * 如果該bucket不存在，則會創建該bucket。
     */
    @PostConstruct
    public void init() {
        try {
            List<String> buckets = List.of(cryptoBucket,
                                           cryptoHistoryBucket,
                                           stockTwBucket,
                                           stockTwHistoryBucket,
                                           currencyBucket,
                                           propertySummaryBucket,
                                           commonEconomyBucket);
            List<String> keys = List.of(org, url, token);
            String orgId = getOrganization().getId();
            for (String key : keys) {
                if (key == null || key.isEmpty()) {
                    throw new IllegalStateException("請先配置設定: " + key);
                }
            }
            for (String bucket : buckets) {
                if (bucket == null || bucket.isEmpty()) {
                    throw new IllegalStateException("請先配置設定: " + bucket);
                }
                if (!checkBucketExists(bucket)) {
                    createBucket(bucket, orgId);
                }
            }
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }

    /**
     * 這個方法會檢查指定的bucket是否存在。如果存在，則返回true，否則返回false。
     *
     * @param bucketName 指定的bucket名稱
     *
     * @return 如果bucket存在，則返回true，否則返回false。
     */
    private boolean checkBucketExists(String bucketName) {
        Bucket bucket = influxClient.getBucketsApi().findBucketByName(bucketName);
        return bucket != null;
    }

    /**
     * 這個方法會獲取組織的ID。
     *
     * @return 組織對象
     */
    private Organization getOrganization() {
        return influxClient.getOrganizationsApi()
                           .findOrganizations()
                           .stream()
                           .filter(orgTemp -> orgTemp.getName().equals(org))
                           .findFirst()
                           .orElse(null);
    }

    /**
     * 這個方法會創建指定的bucket。
     *
     * @param bucketName 指定的bucket名稱
     * @param orgId      組織的ID
     */
    private void createBucket(String bucketName, String orgId) {
        influxClient.getBucketsApi().createBucket(bucketName, orgId);
    }
}
