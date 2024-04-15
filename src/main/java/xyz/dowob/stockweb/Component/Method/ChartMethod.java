package xyz.dowob.stockweb.Component.Method;

import com.influxdb.query.FluxTable;
import org.springframework.stereotype.Component;


import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ChartMethod {
    private String formatDate(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(instant);
    }

    public Map<String, List<Map<String, Object>>> formatToChartData(Map<String, List<FluxTable>> userSummary) {
        Map<String, List<Map<String, Object>>> chartData = new HashMap<>();
        Map<String, Map<String, Object>> latestRecord = new HashMap<>();
        chartData.put("total_sum", new ArrayList<>());
        chartData.put("currency_sum", new ArrayList<>());
        chartData.put("crypto_sum", new ArrayList<>());
        chartData.put("stock_tw_sum", new ArrayList<>());
        chartData.put("latest", new ArrayList<>());

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
                        dataPoint.put("value", record.getValueByKey("_value"));
                        dataPoints.add(dataPoint);

                        if (!latestRecord.containsKey(field) || Objects.requireNonNull(time).compareTo((Instant) latestRecord.get(field).get("date_instant")) > 0) {
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
