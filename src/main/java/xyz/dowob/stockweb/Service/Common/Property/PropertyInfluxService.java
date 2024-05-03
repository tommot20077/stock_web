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
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yuan
 */
@Service
public class PropertyInfluxService {
    private final InfluxDBClient propertySummaryInfluxClient;
    private final AssetInfluxMethod assetInfluxMethod;
    private final RetryTemplate retryTemplate;
    private final OffsetDateTime startDateTime = Instant.parse("1970-01-01T00:00:00Z").atOffset(ZoneOffset.UTC);
    private final OffsetDateTime stopDateTime = Instant.parse("2099-12-31T23:59:59Z").atOffset(ZoneOffset.UTC);


    Logger logger = LoggerFactory.getLogger(PropertyInfluxService.class);

    @Autowired
    public PropertyInfluxService(
            @Qualifier("propertySummaryInfluxClient") InfluxDBClient propertySummaryInfluxClient, AssetInfluxMethod assetInfluxMethod, RetryTemplate retryTemplate) {
        this.propertySummaryInfluxClient = propertySummaryInfluxClient;
        this.assetInfluxMethod = assetInfluxMethod;
        this.retryTemplate = retryTemplate;
    }

    @Value("${db.influxdb.bucket.property_summary}") private String propertySummaryBucket;

    @Value("${db.influxdb.org}") private String org;

    public void writePropertyDataToInflux(List<PropertyListDto.writeToInfluxPropertyDto> userPropertiesDtoList, User user) {
        logger.debug("讀取資產數據: " + userPropertiesDtoList.toString());
        Long time;
        if (userPropertiesDtoList.getFirst().getTimeMillis() == 0L) {
            time = Instant.now().toEpochMilli();
        } else {
            time = userPropertiesDtoList.getFirst().getTimeMillis();
        }

        var ref = new Object() {
            BigDecimal currencyTypeSum = new BigDecimal(0);
            BigDecimal cryptoTypeSum = new BigDecimal(0);
            BigDecimal stockTwTypeSum = new BigDecimal(0);
        };
        try {
            retryTemplate.doWithRetry(() -> {
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
                    } catch (Exception e) {
                        logger.error("寫入InfluxDB時發生錯誤", e);
                        throw new RuntimeException("寫入InfluxDB時發生錯誤", e);
                    }
                    switch (userPropertiesDto.getAssetType()) {
                        case CURRENCY:
                            ref.currencyTypeSum = ref.currencyTypeSum.add(userPropertiesDto.getCurrentTotalPrice());
                            break;
                        case CRYPTO:
                            ref.cryptoTypeSum = ref.cryptoTypeSum.add(userPropertiesDto.getCurrentTotalPrice());
                            break;
                        case STOCK_TW:
                            ref.stockTwTypeSum = ref.stockTwTypeSum.add(userPropertiesDto.getCurrentTotalPrice());
                            break;
                    }
                }
                Point summaryPoint = Point.measurement("summary_property")
                                          .addTag("user_id", user.getId().toString())
                                          .addField("currency_sum", ref.currencyTypeSum)
                                          .addField("crypto_sum", ref.cryptoTypeSum)
                                          .addField("stock_tw_sum", ref.stockTwTypeSum)
                                          .addField("total_sum", ref.currencyTypeSum.add(ref.cryptoTypeSum).add(ref.stockTwTypeSum))
                                          .time(time, WritePrecision.MS);
                logger.debug("建立InfluxDB specificPoint");
                assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, summaryPoint);
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
        }
    }


    public BigDecimal calculateNetFlow(BigDecimal quantity, Asset asset) {
        BigDecimal price = assetInfluxMethod.getLatestPrice(asset);
        if (price.compareTo(BigDecimal.valueOf(-1)) == 0) {
            logger.error("無法取得最新價格");
            throw new RuntimeException("無法取得最新價格");
        }
        return price.multiply(quantity);
    }

    public void writeNetFlowToInflux(BigDecimal newNetFlow, User user) {

        Map<String, List<FluxTable>> netCashFlowTablesMap = queryByUser(propertySummaryBucket, "net_cash_flow", user, "3d", true);
        BigDecimal originNetCashFlow;
        if (!netCashFlowTablesMap.containsKey("net_cash_flow") || netCashFlowTablesMap.get("net_cash_flow")
                                                                                      .isEmpty() || netCashFlowTablesMap.get("net_cash_flow")
                                                                                                                        .getFirst()
                                                                                                                        .getRecords()
                                                                                                                        .isEmpty()) {
            logger.debug("沒有該用戶的資料，設定初始值0");
            originNetCashFlow = BigDecimal.valueOf(0);
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
        logger.debug("更新後的現金流量: " + newNetCashFlow);
        try {
            retryTemplate.doWithRetry(() -> {
                Point netCashFlowPoint = Point.measurement("net_cash_flow")
                                              .addTag("user_id", user.getId().toString())
                                              .addField("net_cash_flow", newNetCashFlow)
                                              .time(Instant.now().toEpochMilli(), WritePrecision.MS);

                logger.debug("建立InfluxDB netCashFlowPoint");
                assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, netCashFlowPoint);
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }


    public Map<String, List<FluxTable>> queryByUser(String bucket, String measurement, User user, String queryTimeRange, boolean isLast) {
        Map<String, List<FluxTable>> userPropertyTablesMap = new HashMap<>();
        String summaryPredicate = createInquiryPredicateWithUserAndTimeInRange(bucket, measurement, user, queryTimeRange, isLast);
        var ref = new Object() {
            List<FluxTable> userPropertyTables;
        };
        try {
            retryTemplate.doWithRetry(() -> {
                ref.userPropertyTables = propertySummaryInfluxClient.getQueryApi().query(summaryPredicate, org);
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }

        userPropertyTablesMap.put(measurement, ref.userPropertyTables);
        return userPropertyTablesMap;
    }

    private String createInquiryPredicateWithUserAndTimeInRange(String propertySummaryBucket, String measurement, User user, String dateRange, boolean isLast) {
        String baseQuery = String.format("from(bucket: \"%s\")" + " |> range(start: -%s)" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" + " |> filter(fn: (r) => r[\"user_id\"] == \"%s\")",
                                         propertySummaryBucket,
                                         dateRange,
                                         measurement,
                                         user.getId());
        if (isLast) {
            baseQuery += " |> last()";
        }
        logger.debug("baseQuery: " + baseQuery);
        return baseQuery;
    }


    public void writeUserRoiDataToInflux(ObjectNode node, User user, Long time) {
        logger.debug("讀取資料: " + node);
        Point roiPoint = Point.measurement("roi")
                              .addTag("user_id", user.getId().toString())
                              .addField("day", "數據不足".equals(node.get("day").asText()) ? null : node.get("day").asDouble())
                              .addField("week", "數據不足".equals(node.get("week").asText()) ? null : node.get("week").asDouble())
                              .addField("month", "數據不足".equals(node.get("month").asText()) ? null : node.get("month").asDouble())
                              .addField("year", "數據不足".equals(node.get("year").asText()) ? null : node.get("year").asDouble())
                              .time(time, WritePrecision.MS);

        assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, roiPoint);

    }

    public void deleteSpecificPropertyDataByUserAndAsset(User user) {
        String predicate = String.format("_measurement=\"specific_property\" AND user_id=\"%s\"", user.getId());
        logger.warn("刪除" + user.getId() + "的特定資產歷史資料");
        try {
            retryTemplate.doWithRetry(() -> {
                try {
                    logger.warn("連接InfluxDB成功");
                    propertySummaryInfluxClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, propertySummaryBucket, org);
                    logger.info("刪除資料成功");
                } catch (Exception e) {
                    logger.error("刪除資料時發生錯誤: " + e.getMessage());
                    throw new RuntimeException("刪除資料時發生錯誤: " + e.getMessage());
                }
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }

    public void setZeroSummaryByUser(User user) {
        Long time = Instant.now().toEpochMilli();
        Point summaryPoint = Point.measurement("summary_property")
                                  .addTag("user_id", user.getId().toString())
                                  .addField("currency_sum", 0.0)
                                  .addField("crypto_sum", 0.0)
                                  .addField("stock_tw_sum", 0.0)
                                  .addField("total_sum", 0.0)
                                  .time(time, WritePrecision.MS);
        logger.debug("建立InfluxDB specificPoint");
        assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, summaryPoint);

        Point netFlowPoint = Point.measurement("net_cash_flow")
                                  .addTag("user_id", user.getId().toString())
                                  .addField("net_cash_flow", 0.0)
                                  .time(time, WritePrecision.MS);
        logger.debug("建立InfluxDB netFlowPoint");
        assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, netFlowPoint);
    }
}
