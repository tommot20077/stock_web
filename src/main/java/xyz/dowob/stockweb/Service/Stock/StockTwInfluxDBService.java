package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
public class StockTwInfluxDBService {
    private final InfluxDBClient StockTwInfluxDBClient;
    private final InfluxDBClient StockTwHistoryInfluxDBClient;
    Logger logger = LoggerFactory.getLogger(StockTwInfluxDBService.class);
    private final OffsetDateTime startDateTime = Instant.parse("1970-01-01T00:00:00Z").atOffset(ZoneOffset.UTC);
    private final OffsetDateTime stopDateTime = Instant.parse("2099-12-31T23:59:59Z").atOffset(ZoneOffset.UTC);

    @Autowired
    public StockTwInfluxDBService(@Qualifier("StockTwInfluxClient") InfluxDBClient stockTwInfluxClient, @Qualifier("StockTwHistoryInfluxClient")InfluxDBClient stockTwHistoryInfluxClient) {
        StockTwInfluxDBClient = stockTwInfluxClient;
        StockTwHistoryInfluxDBClient = stockTwHistoryInfluxClient;
    }

    @Value("${db.influxdb.org}")
    private String org;

    @Value("${db.influxdb.bucket.stock}")
    private String stockBucket;

    @Value("${db.influxdb.bucket.stock_history}")
    private String stockHistoryBucket;

    public void writeStockTwToInflux(JsonNode msgArray) {
        logger.debug("讀取即時股價數據");
        for (JsonNode msgNode : msgArray) {
            logger.debug(msgNode.toString());
            if (Objects.equals(msgNode.path("z").asText(), "-")) {
                continue;
            }
            logger.debug("z = " + Double.parseDouble(msgNode.path("z").asText())
                    + ", c = " + msgNode.path("c").asText()
                    + ", tlong = " + msgNode.path("tlong").asText()
                    + ", o = " + Double.parseDouble(msgNode.path("o").asText())
                    + ", h = " + Double.parseDouble(msgNode.path("h").asText())
                    + ", l = " + Double.parseDouble(msgNode.path("l").asText())
                    + ", v = " + Double.parseDouble(msgNode.path("v").asText())
            );
            Double price = Double.parseDouble(msgNode.path("z").asText());
            Double high = Double.parseDouble(msgNode.path("h").asText());
            Double open = Double.parseDouble(msgNode.path("o").asText());
            Double low = Double.parseDouble(msgNode.path("l").asText());
            Double volume = Double.parseDouble(msgNode.path("v").asText());
            String time  = msgNode.path("tlong").asText();
            String stockId = msgNode.path("c").asText();


            Point point = Point.measurement("kline_data")
                    .addTag("stock_tw", stockId)
                    .addField("price", price)
                    .addField("high", high)
                    .addField("low", low)
                    .addField("open", open)
                    .addField("volume", volume)
                    .time(Long.parseLong(time), WritePrecision.MS);
            logger.debug("建立InfluxDB Point");
            writeToInflux(StockTwInfluxDBClient, point);
        }
    }


    public void writeStockTwHistoryToInflux(ArrayNode dataArray, String stockCode) {
        logger.debug("讀取歷史股價數據");
        for (JsonNode dataEntry : dataArray) {
            String dateStr = dataEntry.get(0).asText();
            Long tLong = formattedRocData(dateStr);

            String numberOfStocksVolume = dataEntry.get(1).asText().replace(",", "");
            String openingPrice = dataEntry.get(3).asText();
            String highestPrice = dataEntry.get(4).asText();
            String lowestPrice = dataEntry.get(5).asText();
            String closingPrice = dataEntry.get(6).asText();

            logger.debug("日期: " + dateStr
                    + ", 成交股數: " + numberOfStocksVolume
                    + ", 開盤價: " + openingPrice
                    + ", 最高價: " + highestPrice
                    + ", 最低價: " + lowestPrice
                    + ", 收盤價: " + closingPrice);

            Point point = Point.measurement("kline_data")
                    .addTag("stock_tw", stockCode)
                    .addField("high", Double.parseDouble(highestPrice))
                    .addField("low", Double.parseDouble(lowestPrice))
                    .addField("open", Double.parseDouble(openingPrice))
                    .addField("close", Double.parseDouble(closingPrice))
                    .addField("volume", Double.parseDouble(numberOfStocksVolume))
                    .time(tLong, WritePrecision.MS);
            logger.debug("建立InfluxDB Point");
            writeToInflux(StockTwHistoryInfluxDBClient, point);

        }
    }

    public void writeUpdateDailyStockTwHistoryToInflux(JsonNode node, Long todayTlong) {
        logger.debug("讀取每日更新股價數據");
        String stockCode = node.path("Code").asText();
        String tradeVolume = node.path("TradeVolume").asText();
        String openingPrice = node.path("OpeningPrice").asText();
        String highestPrice = node.path("HighestPrice").asText();
        String lowestPrice = node.path("LowestPrice").asText();
        String closingPrice = node.path("ClosingPrice").asText();

        logger.debug("日期(Long): " + todayTlong.toString()
                + ", 成交股數: " + tradeVolume
                + ", 開盤價: " + openingPrice
                + ", 最高價: " + highestPrice
                + ", 最低價: " + lowestPrice
                + ", 收盤價: " + closingPrice);

        Point point = Point.measurement("kline_data")
                           .addTag("stock_tw", stockCode)
                           .addField("high", Double.parseDouble(highestPrice))
                           .addField("low", Double.parseDouble(lowestPrice))
                           .addField("open", Double.parseDouble(openingPrice))
                           .addField("close", Double.parseDouble(closingPrice))
                           .addField("volume", Double.parseDouble(tradeVolume))
                           .time(todayTlong, WritePrecision.MS);
        logger.debug("建立InfluxDB Point");
        writeToInflux(StockTwHistoryInfluxDBClient, point);
    }






    private void writeToInflux(InfluxDBClient client, Point point) {
        try {
            logger.debug("連接InfluxDB成功");
            try (WriteApi writeApi = client.makeWriteApi()) {
                writeApi.writePoint(point);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
        }
    }
    private Long formattedRocData(String dateStr) {
        String[] dateParts = dateStr.split("/");
        int yearRoc = Integer.parseInt(dateParts[0]);
        int yearAd = yearRoc + 1911;
        String formattedDate = yearAd + "/" + dateParts[1] + "/" + dateParts[2];

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDate localDate = LocalDate.parse(formattedDate, formatter);

        Instant instant = localDate.atStartOfDay(ZoneId.of("Asia/Taipei")).toInstant();
        return instant.toEpochMilli();
    }



    public void deleteDataByStockCode(String stockCode) {
        String predicate = String.format("_measurement=\"kline_data\" AND stock_tw=\"%s\"", stockCode);
        logger.debug("刪除" + stockCode + "的歷史資料");
        try {
            logger.debug("連接InfluxDB成功");
            StockTwInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, stockBucket, org);
            StockTwHistoryInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, stockHistoryBucket, org);
            logger.debug("刪除資料成功");
        } catch (Exception e) {
            logger.error("刪除資料時發生錯誤", e);
        }
    }

    //todo 視情況刪除
/*
    public LocalDate queryLastDataDateFromInfluxByStockCode(String stockCode) {
        String query = String.format(
                "from(bucket: \"%s\") |> range(start: -14d)" +
                        " |> filter(fn: (r) => r._measurement == \"kline_data\")" +
                        " |> filter(fn: (r) => r.stock_tw == \"%s\")" +
                        " |> last()",
                stockHistoryBucket, stockCode
        );
        FluxTable result = StockTwHistoryInfluxDBClient.getQueryApi().query(query, org).getLast();
        if (!result.getRecords().isEmpty()) {
            Instant lastRecordTime = result.getRecords().getFirst().getTime();
            if (lastRecordTime != null) {
                return LocalDateTime.ofInstant(lastRecordTime, ZoneId.of("UTC")).toLocalDate();
            }
        }
        return null;
    }
    */
}
