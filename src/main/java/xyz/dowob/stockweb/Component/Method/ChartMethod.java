package xyz.dowob.stockweb.Component.Method;

import com.influxdb.query.FluxTable;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Model.Currency.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author yuan
 */
@Component
public class ChartMethod {

    /**
     * 格式化時間
     *
     * @param instant 時間
     *
     * @return 格式化後的時間
     */
    private String formatDate(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(instant);
    }

    /**
     * 格式化資料為圖表資料
     *
     * @param userSummary    使用者資料
     * @param preferCurrency 使用者偏好幣別
     *
     * @return 圖表資料 Map<String, List<Map<String, Object>>>
     * key: 圖表類型, value: 圖表資料 List<Map<String, Object>> (field, date_instant, date_Format, value)
     */
    public Map<String, List<Map<String, Object>>> formatToChartData(Map<String, List<FluxTable>> userSummary, Currency preferCurrency) {
        Map<String, List<Map<String, Object>>> chartData = new HashMap<>();
        Map<String, Map<String, Object>> latestRecord = new HashMap<>();
        chartData.put("total_sum", new ArrayList<>());
        chartData.put("currency_sum", new ArrayList<>());
        chartData.put("crypto_sum", new ArrayList<>());
        chartData.put("stock_tw_sum", new ArrayList<>());
        chartData.put("latest", new ArrayList<>());
        BigDecimal exchangeRate = preferCurrency.getExchangeRate();

        userSummary.forEach((key, tables) -> {
            tables.forEach(table -> {
                table.getRecords().forEach(record -> {
                    String field = (String) record.getValueByKey("_field");
                    List<Map<String, Object>> dataPoints = chartData.get(field);
                    if (dataPoints != null) {
                        Map<String, Object> dataPoint = new HashMap<>();
                        Instant time = (Instant) record.getValueByKey("_time");
                        dataPoint.put("field", field);
                        dataPoint.put("date_instant", time);
                        dataPoint.put("date_Format", formatDate(time));


                        Double value = (Double) record.getValueByKey("_value");
                        if (value != null) {
                            BigDecimal exchangeValue = new BigDecimal(value).multiply(exchangeRate).setScale(3, RoundingMode.HALF_UP);
                            dataPoint.put("value", exchangeValue);
                        } else {
                            dataPoint.put("value", record.getValueByKey("_value"));
                        }

                        dataPoints.add(dataPoint);

                        if (!latestRecord.containsKey(field) || Objects.requireNonNull(time)
                                                                       .compareTo((Instant) latestRecord.get(field)
                                                                                                        .get("date_instant")) > 0) {
                            latestRecord.put(field, dataPoint);
                        }
                    }
                });
            });
        });
        latestRecord.forEach((field, dataPoint) -> {
            chartData.get("latest").add(dataPoint);
        });
        return chartData;
    }

}
