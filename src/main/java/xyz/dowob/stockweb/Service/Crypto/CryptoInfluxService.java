package xyz.dowob.stockweb.Service.Crypto;


import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;

import java.time.*;
import java.util.List;
import java.util.Map;

/**
 * @author yuan
 * 有關加密貨幣的InfluxDB業務邏輯
 */
@Service
public class CryptoInfluxService {
    private final InfluxDBClient cryptoInfluxDBClient;

    private final InfluxDBClient cryptoHistoryInfluxDBClient;

    private final AssetInfluxMethod assetInfluxMethod;

    private final RetryTemplate retryTemplate;

    Logger logger = LoggerFactory.getLogger(CryptoInfluxService.class);

    private final OffsetDateTime startDateTime = Instant.parse("1970-01-01T00:00:00Z").atOffset(ZoneOffset.UTC);

    private final OffsetDateTime stopDateTime = Instant.parse("2099-12-31T23:59:59Z").atOffset(ZoneOffset.UTC);


    /**
     * CryptoInfluxService構造函數
     *
     * @param cryptoInfluxClient        加密貨幣InfluxDB客戶端
     * @param cryptoHistoryInfluxClient 加密貨幣歷史InfluxDB客戶端
     * @param assetInfluxMethod         資產InfluxDB方法
     * @param retryTemplate             重試模板
     */
    @Autowired
    public CryptoInfluxService(
            @Qualifier("CryptoInfluxClient") InfluxDBClient cryptoInfluxClient, @Qualifier("CryptoHistoryInfluxClient") InfluxDBClient cryptoHistoryInfluxClient, AssetInfluxMethod assetInfluxMethod, RetryTemplate retryTemplate) {
        this.cryptoInfluxDBClient = cryptoInfluxClient;
        this.cryptoHistoryInfluxDBClient = cryptoHistoryInfluxClient;
        this.assetInfluxMethod = assetInfluxMethod;
        this.retryTemplate = retryTemplate;
    }

    @Value("${db.influxdb.bucket.crypto}")
    private String cryptoBucket;

    @Value("${db.influxdb.bucket.crypto_history}")
    private String cryptoHistoryBucket;

    @Value("${db.influxdb.org}")
    private String org;

    /**
     * WebSocket的kline數據寫入InfluxDB
     *
     * @param klineData kline數據
     */
    public void writeToInflux(Map<String, Map<String, String>> klineData) {
        logger.debug("讀取kline數據: {}", klineData.toString());
        for (Map.Entry<String, Map<String, String>> entry : klineData.entrySet()) {
            Double open = Double.parseDouble(entry.getValue().get("open"));
            Double close = Double.parseDouble(entry.getValue().get("close"));
            Double high = Double.parseDouble(entry.getValue().get("high"));
            Double low = Double.parseDouble(entry.getValue().get("low"));
            Double volume = Double.parseDouble(entry.getValue().get("volume"));

            Point point = Point.measurement("kline_data")
                               .addTag("tradingPair", entry.getKey())
                               .addField("open", open)
                               .addField("close", close)
                               .addField("high", high)
                               .addField("low", low)
                               .addField("volume", volume)
                               .time(Long.parseLong(entry.getValue().get("time")), WritePrecision.MS);
            assetInfluxMethod.writeToInflux(cryptoInfluxDBClient, point);
        }
    }

    /**
     * 將加密貨幣歷史數據寫入InfluxDB
     *
     * @param data        加密貨幣歷史數據
     * @param tradingPair 交易對
     */
    public void writeCryptoHistoryToInflux(List<String[]> data, String tradingPair) {
        for (String[] record : data) {
            Long time = Long.parseLong(record[0]);
            Double open = Double.parseDouble(record[1]);
            Double high = Double.parseDouble(record[2]);
            Double low = Double.parseDouble(record[3]);
            Double close = Double.parseDouble(record[4]);
            Double volume = Double.parseDouble(record[5]);
            logger.debug("time = {}, open = {}, high = {}, low = {}, close = {}, volume = {}", time, open, high, low, close, volume);

            Point point = Point.measurement("kline_data")
                               .addTag("tradingPair", tradingPair)
                               .addField("open", open)
                               .addField("close", close)
                               .addField("high", high)
                               .addField("low", low)
                               .addField("volume", volume)
                               .time(time, WritePrecision.MS);

            assetInfluxMethod.writeToInflux(cryptoHistoryInfluxDBClient, point);
        }
    }


    /**
     * 根據交易對刪除InfluxDB中的數據
     *
     * @param tradingPair 交易對
     *
     * @throws RuntimeException 刪除數據時發生錯誤
     */
    public void deleteDataByTradingPair(String tradingPair) {
        String predicate = String.format("_measurement=\"kline_data\" AND tradingPair=\"%s\"", tradingPair);
        logger.warn("刪除{}的歷史資料", tradingPair);
        try {
            retryTemplate.doWithRetry(() -> {
                try {
                    logger.warn("連接InfluxDB成功");
                    cryptoInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, cryptoBucket, org);
                    cryptoHistoryInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, cryptoHistoryBucket, org);
                    logger.info("刪除資料成功");
                } catch (Exception e) {
                    logger.error("刪除資料時發生錯誤: {}", e.getMessage());
                    throw new RuntimeException("刪除資料時發生錯誤: " + e.getMessage());
                }
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：{}", e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }

    /**
     * 根據交易對獲取最後一條數據的日期
     *
     * @param tradingPair 交易對
     *
     * @return 最後一條數據的日期
     */
    public LocalDate getLastDateByTradingPair(String tradingPair) {
        var ref = new Object() {
            FluxTable result;
        };
        String query = String.format("from(bucket: \"%s\") |> range(start: -14d)" + " |> filter(fn: (r) => r[\"_measurement\"] == \"kline_data\")" + " |> filter(fn: (r) => r[\"tradingPair\"] == \"%s\")" + " |> last()",
                                     cryptoHistoryBucket,
                                     tradingPair);
        try {
            retryTemplate.doWithRetry(() -> ref.result = cryptoHistoryInfluxDBClient.getQueryApi().query(query, org).getLast());
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：{}", e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }

        if (!ref.result.getRecords().isEmpty()) {
            Instant lastRecordTime = ref.result.getRecords().getFirst().getTime();
            if (lastRecordTime != null) {
                return LocalDateTime.ofInstant(lastRecordTime, ZoneId.of("UTC")).toLocalDate();
            }
        }
        return null;
    }
}
