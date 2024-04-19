package xyz.dowob.stockweb.Service.Common.Property;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.User;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author tommo
 */
@Service
public class PropertyInfluxService {
    private final InfluxDBClient propertySummaryInfluxClient;
    private final AssetInfluxMethod assetInfluxMethod;


    Logger logger = LoggerFactory.getLogger(PropertyInfluxService.class);

    @Autowired
    public PropertyInfluxService(@Qualifier("propertySummaryInfluxClient")InfluxDBClient propertySummaryInfluxClient, AssetInfluxMethod assetInfluxMethod) {
        this.propertySummaryInfluxClient = propertySummaryInfluxClient;
        this.assetInfluxMethod = assetInfluxMethod;
    }

    @Value("${db.influxdb.bucket.property_summary}")
    private String propertySummaryBucket;

    @Value("${db.influxdb.org}")
    private String org;

    public boolean writePropertyDataToInflux(List<PropertyListDto.writeToInfluxPropertyDto> userPropertiesDtoList, User user) {
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

    public BigDecimal calculateNetFlow(BigDecimal quantity, Asset asset) {
        BigDecimal price = assetInfluxMethod.getLatestPrice(asset);
        if (price.compareTo(BigDecimal.valueOf(-1)) == 0) {
            logger.error("無法取得最新價格");
            throw new RuntimeException("無法取得最新價格");
        }
        return price.multiply(quantity);
    }

    public void writeNetFlowToInflux (BigDecimal newNetFlow, User user) {
        Map<String, List<FluxTable>> netCashFlowTablesMap = queryByUser(propertySummaryBucket, "net_cash_flow", user, "3d", true);
        BigDecimal originNetCashFlow = BigDecimal.valueOf(0);
        if (!netCashFlowTablesMap.containsKey("net_cash_flow") || netCashFlowTablesMap.get("net_cash_flow").isEmpty() || netCashFlowTablesMap.get("net_cash_flow").getFirst().getRecords().isEmpty()) {
            logger.debug("沒有該用戶的資料，設定初始值0");
            newNetFlow = BigDecimal.valueOf(0);
        } else {
            Object value = netCashFlowTablesMap.get("net_cash_flow").getFirst().getRecords().getFirst().getValueByKey("_value");
            logger.debug("取得該用戶的資料: " + value);
            if (value instanceof Double netCashFlowBig) {
                originNetCashFlow = BigDecimal.valueOf(netCashFlowBig);
            } else {
                logger.debug("沒有該用戶的資料，設定初始值0");
                originNetCashFlow = BigDecimal.valueOf(0);
            }
        }
        BigDecimal newNetCashFlow = originNetCashFlow.add(newNetFlow);


        Point netCashFlowPoint = Point.measurement("net_cash_flow")
            .addTag("user_id", user.getId().toString())
            .addField("net_flow", newNetCashFlow)
            .time(Instant.now().toEpochMilli(), WritePrecision.MS);

        logger.debug("建立InfluxDB netCashFlowPoint");
        try {
            logger.debug("連接InfluxDB成功");
            try (WriteApi writeApi = propertySummaryInfluxClient.makeWriteApi()) {
                writeApi.writePoint(netCashFlowPoint);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
            throw new RuntimeException("寫入InfluxDB時發生錯誤", e);
        }
    }




    public Map<String, List<FluxTable>> queryByUser(String bucket, String measurement, User user, String queryTimeRange, boolean isLast) {
        Map<String, List<FluxTable>> userPropertyTablesMap = new HashMap<>();
        String summaryPredicate = createInquiryPredicateWithUserAndTimeInRange(bucket, measurement, user, queryTimeRange, isLast);
        userPropertyTablesMap.put(measurement, propertySummaryInfluxClient.getQueryApi().query(summaryPredicate, org));
        return userPropertyTablesMap;
    }

    private String createInquiryPredicateWithUserAndTimeInRange(String propertySummaryBucket, String measurement, User user, String dateRange, boolean isLast) {
        String baseQuery = String.format(
            "from(bucket: \"%s\")" +
            " |> range(start: -%s)" +
            " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" +
            " |> filter(fn: (r) => r[\"user_id\"] == \"%s\")",
                propertySummaryBucket, dateRange, measurement, user.getId()
        );
        if (isLast) {
            baseQuery += " |> last()";
        }
        logger.debug("baseQuery: " + baseQuery);
        return baseQuery;
    }

    public Map<LocalDateTime, List<FluxTable>> queryByTimeAndUser(String bucket, String measurement, List<String> fields, User user, List<LocalDateTime> specificTimes, int allowRangeOfHour, boolean isLast, boolean needToFillData) {
        Map<LocalDateTime, List<FluxTable>> userTablesMap = new HashMap<>();
        Map<LocalDateTime, String> predicate = createInquiryPredicateWithUserAndSpecificTimes(bucket, measurement, fields, user, specificTimes, allowRangeOfHour, isLast, needToFillData);

        for (Map.Entry<LocalDateTime, String> entry : predicate.entrySet()) {
            LocalDateTime specificTime = entry.getKey();
            String query = entry.getValue();

            List<FluxTable> result = propertySummaryInfluxClient.getQueryApi().query(query, org);
            userTablesMap.put(specificTime, result);
        }
        return userTablesMap;
    }

    private Map<LocalDateTime, String> createInquiryPredicateWithUserAndSpecificTimes(
            String propertySummaryBucket,
            String measurement,
            List<String> fields, User user,
            List<LocalDateTime> specificTimes,
            int allowRangeOfHour,
            boolean isLast,
            boolean needToFillData) {

        Map<LocalDateTime, String> queries = new HashMap<>();
        DateTimeFormatter influxDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
        for (LocalDateTime specificTime : specificTimes) {
            LocalDateTime rangeStart = specificTime.minusHours(allowRangeOfHour);
            LocalDateTime rangeEnd = specificTime.plusMinutes(30);

            String formattedStart = influxDateFormat.format(rangeStart);
            String formattedEnd = influxDateFormat.format(rangeEnd);

            StringBuilder baseQuery = new StringBuilder(String.format(
                    "from(bucket: \"%s\")" +
                    " |> range(start: %s, stop: %s)" +
                    " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" +
                    " |> filter(fn: (r) => r[\"user_id\"] == \"%s\")",
                    propertySummaryBucket, formattedStart, formattedEnd, measurement, user.getId()));
            if (isLast) {
                baseQuery.append(" |> last()");
            }
            if (needToFillData) {
                baseQuery.append(
                        " |> aggregateWindow(every: 1h, fn: mean, createEmpty: true)" +
                        " |> fill(usePrevious: true)"
                );

            }
            if (!fields.isEmpty()) {
                for (String field : fields) {
                    String additionalQuery = String.format("  |> filter(fn: (r) => r[\"_field\"] == \"%s\")", field);
                    baseQuery.append(additionalQuery);
                }
            }
            queries.put(specificTime, baseQuery.toString());
        }
        logger.debug("queries: " + queries);
        return queries;
    }

    public void writeUserRoiDataToInflux(ObjectNode node, User user, Long time) {
        logger.debug("讀取資料: " + node);
        Point roiPoint = Point.measurement("roi")
            .addTag("user_id", user.getId().toString())
            .addField("day", "數據不足".equals(node.get("day").asText())? null : node.get("day").asDouble())
            .addField("week", "數據不足".equals(node.get("week").asText())? null : node.get("week").asDouble())
            .addField("month", "數據不足".equals(node.get("month").asText())? null : node.get("month").asDouble())
            .addField("year", "數據不足".equals(node.get("year").asText())? null : node.get("year").asDouble())
            .time(time, WritePrecision.MS);
        try {
            logger.debug("寫入InfluxDB: " + roiPoint);
            try (WriteApi writeApi = propertySummaryInfluxClient.makeWriteApi()) {
                writeApi.writePoint(roiPoint);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
            throw new RuntimeException("寫入InfluxDB時發生錯誤", e);
        }
    }
}
