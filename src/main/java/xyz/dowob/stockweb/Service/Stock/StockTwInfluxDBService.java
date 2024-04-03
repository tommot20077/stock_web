package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

@Service
public class StockTwInfluxDBService {
    private final InfluxDBClient StockTwInfluxDBClient;
    Logger logger = LoggerFactory.getLogger(StockTwInfluxDBService.class);

    @Autowired
    public StockTwInfluxDBService(@Qualifier("StockTwInfluxDBClient") InfluxDBClient stockTwInfluxDBClient) {
        StockTwInfluxDBClient = stockTwInfluxDBClient;
    }

    public void writeToInflux(JsonNode msgArray) {
        logger.debug("讀取即時股價數據");
        for (JsonNode msgNode : msgArray) {
            logger.debug(msgNode.toString());
            if (!Objects.equals(msgNode.path("z").asText(), "-")) {
                continue;
            }
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

            try {
                logger.debug("連接InfluxDB成功");
                try (WriteApi writeApi = StockTwInfluxDBClient.makeWriteApi()) {
                    writeApi.writePoint(point);
                    logger.debug("寫入InfluxDB成功");
                }
            } catch (Exception e) {
                logger.error("寫入InfluxDB時發生錯誤", e);
            }
        }
    }

}
