package xyz.dowob.stockweb.Service.Common.Property;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Model.User.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tommo
 */
@Service
public class PropertyInfluxService {
    private final InfluxDBClient propertySummaryInfluxClient;

    Logger logger = LoggerFactory.getLogger(PropertyInfluxService.class);

    @Autowired
    public PropertyInfluxService(@Qualifier("propertySummaryInfluxClient")InfluxDBClient propertySummaryInfluxClient) {
        this.propertySummaryInfluxClient = propertySummaryInfluxClient;
    }

    @Value("${db.influxdb.bucket.property_summary}")
    private String propertySummaryBucket;

    @Value("${db.influxdb.org}")
    private String org;

    public boolean writeToInflux(List<PropertyListDto.writeToInfluxPropertyDto> userPropertiesDtoList, User user) {
        logger.debug("讀取資產數據: " + userPropertiesDtoList.toString());
        Long time;
        boolean success = true;
        if (userPropertiesDtoList.getFirst().getTimeMillis() == 0L) {
            time = Instant.now().toEpochMilli();
        } else {
            time = userPropertiesDtoList.getFirst().getTimeMillis();
        }

        BigDecimal currencyTypeSum = new BigDecimal(0);
        BigDecimal cryptoTypeSum = new BigDecimal(0);
        BigDecimal stockTwTypeSum = new BigDecimal(0);

        for (PropertyListDto.writeToInfluxPropertyDto userPropertiesDto : userPropertiesDtoList) {
            Point specificPoint = Point.measurement("specific_property")
                    .addTag("user_id", user.getId().toString())
                    .addTag("asset_type", userPropertiesDto.getAssetType().toString())
                    .addTag("asset_id", userPropertiesDto.getAssetId().toString())
                    .addField("current_price", userPropertiesDto.getCurrentPrice())
                    .addField("current_total_price", userPropertiesDto.getCurrentTotalPrice())
                    .addField("quantity", userPropertiesDto.getQuantity())
                    .time(time, WritePrecision.MS);
            logger.debug("建立InfluxDB specificPoint");

            try {
                logger.debug("連接InfluxDB成功");
                try (WriteApi writeApi = propertySummaryInfluxClient.makeWriteApi()) {
                    writeApi.writePoint(specificPoint);
                    logger.debug("寫入InfluxDB成功");
                }

                switch (userPropertiesDto.getAssetType()) {
                    case CURRENCY:
                        currencyTypeSum = currencyTypeSum.add(userPropertiesDto.getCurrentTotalPrice());
                        break;
                    case CRYPTO:
                        cryptoTypeSum = cryptoTypeSum.add(userPropertiesDto.getCurrentTotalPrice());
                        break;
                    case STOCK_TW:
                        stockTwTypeSum = stockTwTypeSum.add(userPropertiesDto.getCurrentTotalPrice());
                        break;
                }

            } catch (Exception e) {
                logger.error("寫入InfluxDB時發生錯誤", e);
            }
        }
        Point summaryPoint = Point.measurement("summary_property")
                    .addTag("user_id", user.getId().toString())
                    .addField("currency_sum", currencyTypeSum)
                    .addField("crypto_sum", cryptoTypeSum)
                    .addField("stock_tw_sum", stockTwTypeSum)
                    .addField("total_sum", currencyTypeSum.add(cryptoTypeSum).add(stockTwTypeSum))
                    .time(time, WritePrecision.MS);
        logger.debug("建立InfluxDB specificPoint");
        try {
            logger.debug("連接InfluxDB成功");
            try (WriteApi writeApi = propertySummaryInfluxClient.makeWriteApi()) {
                writeApi.writePoint(summaryPoint);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
            success = false;
        }

        return success;
    }

    public Map<String, List<FluxTable>>queryUserPropertySum(User user) {
        Map<String, List<FluxTable>> userPropertyTablesMap = new HashMap<>();
        String specificPredicate = createPredicate(propertySummaryBucket, "specific_property", user.getId().toString());
        String summaryPredicate = createPredicate(propertySummaryBucket, "summary_property", user.getId().toString());
        userPropertyTablesMap.put("specific_property",propertySummaryInfluxClient.getQueryApi().query(specificPredicate, org));
        userPropertyTablesMap.put("summary_property",propertySummaryInfluxClient.getQueryApi().query(summaryPredicate, org));
        return userPropertyTablesMap;
    }


    private String createPredicate(String propertySummaryBucket, String measurement, String userId) {
        return String.format(
                "from(bucket: \"%s\")" +
                        " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" +
                        " |> filter(fn: (r) => r[\"user_id\"] == \"%s\")",
                propertySummaryBucket, measurement, userId
        );
    }
}
