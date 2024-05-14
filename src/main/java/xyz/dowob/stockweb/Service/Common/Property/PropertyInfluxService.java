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
import java.util.*;

/**
 * @author yuan
 * 用戶資產Influx操作服務
 * 用於將用戶資產寫入InfluxDB以及查詢用戶資產
 */
@Service
public class PropertyInfluxService {
    private final InfluxDBClient propertySummaryInfluxClient;

    private final InfluxDBClient testInfluxClient;// todo delete

    private final AssetInfluxMethod assetInfluxMethod;

    private final RetryTemplate retryTemplate;

    private final OffsetDateTime startDateTime = Instant.parse("1970-01-01T00:00:00Z").atOffset(ZoneOffset.UTC);

    private final OffsetDateTime stopDateTime = Instant.parse("2099-12-31T23:59:59Z").atOffset(ZoneOffset.UTC);


    Logger logger = LoggerFactory.getLogger(PropertyInfluxService.class);

    @Autowired
    public PropertyInfluxService(
            @Qualifier("propertySummaryInfluxClient") InfluxDBClient propertySummaryInfluxClient, @Qualifier("testInfluxClient") InfluxDBClient testInfluxClient, AssetInfluxMethod assetInfluxMethod, RetryTemplate retryTemplate) {
        this.propertySummaryInfluxClient = propertySummaryInfluxClient;
        this.testInfluxClient = testInfluxClient;
        this.assetInfluxMethod = assetInfluxMethod;
        this.retryTemplate = retryTemplate;
    }

    @Value("${db.influxdb.bucket.property_summary}")
    private String propertySummaryBucket;

    @Value("${db.influxdb.org}")
    private String org;

    /**
     * 將用戶資產寫入InfluxDB並計算總資產
     * 分成specific_property與summary_property兩個表
     * specific_property用於存放特定資產，分為currency, crypto, stock_tw
     * summary_property用於存放總資產，分為currency_sum, crypto_sum, stock_tw_sum, total_sum
     *
     * @param userPropertiesDtoList 用戶資產列表 DTO
     * @param user                  用戶
     */
    public void writePropertyDataToInflux(List<PropertyListDto.writeToInfluxPropertyDto> userPropertiesDtoList, User user) {
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
                    try {
                        try (WriteApi writeApi = propertySummaryInfluxClient.makeWriteApi()) {
                            writeApi.writePoint(specificPoint);
                            logger.debug("寫入資料{}成功", specificPoint);
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


    /**
     * 計算用戶資產淨流量
     *
     * @param quantity 數量
     * @param asset    資產
     *
     * @return 淨流量
     */
    public BigDecimal calculateNetFlow(BigDecimal quantity, Asset asset) {
        BigDecimal price = assetInfluxMethod.getLatestPrice(asset);
        if (price.compareTo(BigDecimal.valueOf(-1)) == 0) {
            logger.error("無法取得最新價格");
            throw new RuntimeException("無法取得最新價格");
        }
        return price.multiply(quantity);
    }

    /**
     * 將用戶淨流量寫入InfluxDB
     *
     * @param newNetFlow 新淨流量
     * @param user       用戶
     */
    public void writeNetFlowToInflux(BigDecimal newNetFlow, User user) {

        Map<String, List<FluxTable>> netCashFlowTablesMap = queryInflux(propertySummaryBucket,
                                                                        "net_cash_flow",
                                                                        null,
                                                                        user,
                                                                        "3d",
                                                                        true,
                                                                        false,
                                                                        false,
                                                                        false);
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


    /**
     * 透過用戶查詢用戶資產
     *
     * @param bucket         bucket
     * @param measurement    查詢表
     * @param filter         查詢條件
     * @param user           用戶
     * @param queryTimeRange 查詢時間範圍
     * @param isLast         是否取最後一筆
     * @param isFirst        是否取第一筆
     * @param isCount        是否計算總數
     * @param isSum          是否計算總和
     *
     * @return 用戶資產列表
     */
    public Map<String, List<FluxTable>> queryInflux(String bucket, String measurement, Map<String, Map<String, List<String>>> filter, User user, String queryTimeRange, boolean isLast, boolean isFirst, boolean isCount, boolean isSum) {
        Map<String, List<FluxTable>> userPropertyTablesMap = new HashMap<>();
        String summaryPredicate = createInquiryPredicate(bucket,
                                                         measurement,
                                                         filter,
                                                         user,
                                                         queryTimeRange,
                                                         isLast,
                                                         isFirst,
                                                         isCount,
                                                         isSum);
        var ref = new Object() {
            List<FluxTable> userPropertyTables;
        };
        try {
            retryTemplate.doWithRetry(() -> ref.userPropertyTables = propertySummaryInfluxClient.getQueryApi()
                                                                                                .query(summaryPredicate, org));
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }

        userPropertyTablesMap.put(measurement, ref.userPropertyTables);
        return userPropertyTablesMap;
    }

    /**
     * 建立influx查詢語句
     *
     * @param propertySummaryBucket bucket
     * @param measurement           查詢表
     * @param filter                查詢過濾條件
     * @param user                  用戶
     * @param dateRange             查詢時間範圍
     * @param isLast                是否取最後一筆
     * @param isFirst               是否取第一筆
     * @param isCount               是否計算總數
     * @param isSum                 是否計算總和
     *
     * @return 查詢條件
     */
    private String createInquiryPredicate(String propertySummaryBucket, String measurement, Map<String, Map<String, List<String>>> filter, User user, String dateRange, boolean isLast, boolean isFirst, boolean isCount, boolean isSum) {
        StringBuilder baseQuery = new StringBuilder(String.format("from(bucket: \"%s\")" + " |> range(start: -%s)" + " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")",
                                                                  propertySummaryBucket,
                                                                  dateRange,
                                                                  measurement));
        if (isLast) {
            baseQuery.append(" |> last()");
        } else if (isFirst) {
            baseQuery.append(" |> first()");
        } else if (isCount) {
            baseQuery.append(" |> count()");
        } else if (isSum) {
            baseQuery.append(" |> sum(column: \"_value\")");
        }

        if (user != null) {
            baseQuery.append(String.format(" |> filter(fn: (r) => r[\"user_id\"] == \"%s\")", user.getId()));
        }

        if (filter != null) {
            int filterSize = filter.size();
            for (Map.Entry<String, Map<String, List<String>>> entry : filter.entrySet()) {
                String logic = "or".equals(entry.getKey()) ? " or " : " and ";
                for (Map.Entry<String, List<String>> subEntry : entry.getValue().entrySet()) {
                    StringBuilder subFilter = new StringBuilder();
                    for (int i = 0; i < subEntry.getValue().size(); i++) {
                        if (!subFilter.isEmpty()) {
                            subFilter.append(logic);
                        }
                        subFilter.append(String.format("r[\"%s\"] == \"%s\"", subEntry.getKey(), subEntry.getValue().get(i)));
                    }
                    if (filterSize == 1) {
                        baseQuery.append(" |> filter(fn: (r) => ").append(subFilter).append(")");
                    }
                }
            }
        }

        logger.debug("baseQuery: " + baseQuery);
        return baseQuery.toString();
    }


    /**
     * 將用戶ROI資料寫入InfluxDB
     * 分成日、週、月、年四個表
     *
     * @param node ROI資料
     * @param user 用戶
     * @param time 時間
     */
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

    public void writeUserRoiStatisticsToInflux(Map<String, BigDecimal> dataMap, User user) {
        Point roiStatisticsPoint = Point.measurement("roi_statistics")
                                        .addTag("user_id", user.getId().toString())
                                        .addField("average", dataMap.get("roiAverageRoi").doubleValue())
                                        .addField("sigma", dataMap.get("roiSigma").doubleValue())
                                        .time(Instant.now().toEpochMilli(), WritePrecision.MS);
        assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, roiStatisticsPoint);
    }

    public void writeUserSharpRatioToInflux(Map<String, String> dataMap, User user) {
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            Double sharpRatio = "數據不足".equals(entry.getValue()) ? null : Double.parseDouble(entry.getValue());
            logger.debug("key: " + entry.getKey() + " value: " + entry.getValue());
            Point sharpRatioPoint = Point.measurement("roi_statistics")
                                         .addTag("user_id", user.getId().toString())
                                         .addTag(entry.getKey(), entry.getKey())
                                         .addField("sharp_ratio", sharpRatio)
                                         .time(Instant.now().toEpochMilli(), WritePrecision.MS);
            assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, sharpRatioPoint);
        }
    }

    public void writeUserDrawDownToInflux(Map<String, Map<String, List<BigDecimal>>> dataMap, User user) {
        List<String> key = List.of("week", "month", "year");
        Long time = Instant.now().toEpochMilli();
        for (String k : key) {
            Map<String, List<BigDecimal>> drawDownMap = dataMap.get(k);
            for (Map.Entry<String, List<BigDecimal>> entry : drawDownMap.entrySet()) {
                Point drawDownPoint = Point.measurement("roi_statistics")
                                           .addTag("user_id", user.getId().toString())
                                           .addTag("time_range", k)
                                           .addTag("type", entry.getKey())
                                           .addField("max_draw_down_value", entry.getValue().getFirst().doubleValue())
                                           .addField("max_draw_down_rate", entry.getValue().get(1).doubleValue())
                                           .time(time, WritePrecision.MS);
                assetInfluxMethod.writeToInflux(propertySummaryInfluxClient, drawDownPoint);
            }
        }
    }

    /**
     * 刪除特定用戶的特定資產歷史資料
     *
     * @param user 用戶
     */
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

    /**
     * 重設特定用戶的淨流量歷史資料
     *
     * @param user 用戶
     */
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
