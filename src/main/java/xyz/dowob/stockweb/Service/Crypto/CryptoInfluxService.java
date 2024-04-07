package xyz.dowob.stockweb.Service.Crypto;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author tommo
 */
@Service
public class CryptoInfluxService {
    private final InfluxDBClient cryptoInfluxDBClient;
    private final InfluxDBClient cryptoHistoryInfluxDBClient;
    Logger logger = LoggerFactory.getLogger(CryptoInfluxService.class);
    @Autowired
    public CryptoInfluxService(@Qualifier("CryptoInfluxClient")InfluxDBClient cryptoInfluxClient, @Qualifier("CryptoHistoryInfluxClient") InfluxDBClient cryptoHistoryInfluxClient) {
        this.cryptoInfluxDBClient = cryptoInfluxClient;
        this.cryptoHistoryInfluxDBClient = cryptoHistoryInfluxClient;
    }

    public void writeToInflux(Map<String, Object> kline) {
        logger.debug("讀取kline數據");
        logger.debug(kline.toString());
        String time = kline.get("t").toString();
        Double open = Double.parseDouble(kline.get("o").toString());
        Double close = Double.parseDouble(kline.get("c").toString());
        Double high = Double.parseDouble(kline.get("h").toString());
        Double low = Double.parseDouble(kline.get("l").toString());
        Double volume = Double.parseDouble(kline.get("v").toString());

        Point point = Point.measurement("kline_data")
                .addTag("tradingPair", kline.get("s").toString())
                .addField("open", open)
                .addField("close", close)
                .addField("high", high)
                .addField("low", low)
                .addField("volume", volume)
                .time(Long.parseLong(time), WritePrecision.MS);
        logger.debug("建立InfluxDB Point");

        try {
            logger.debug("連接InfluxDB成功");
            try (WriteApi writeApi = cryptoInfluxDBClient.makeWriteApi()) {
                writeApi.writePoint(point);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
        }
    }

    public void writeCryptoHistoryToInflux(List<String[]> data, String tradingPair) {
        for (String[] record : data) {
            Long time = Long.parseLong(record[0]);
            Double open = Double.parseDouble(record[1]);
            Double high = Double.parseDouble(record[2]);
            Double low = Double.parseDouble(record[3]);
            Double close = Double.parseDouble(record[4]);
            Double volume = Double.parseDouble(record[5]);
            logger.debug("time = " + time
                    + ", open = " + open
                    + ", high = " + high
                    + ", low = " + low
                    + ", close = " + close
                    + ", volume = " + volume);

            Point point = Point.measurement("kline_data")
                    .addTag("tradingPair", tradingPair)
                    .addField("open", open)
                    .addField("close", close)
                    .addField("high", high)
                    .addField("low", low)
                    .addField("volume", volume)
                    .time(time, WritePrecision.MS);
            logger.debug("建立InfluxDB Point");
            try {
                logger.debug("連接InfluxDB成功");
                try (WriteApi writeApi = cryptoHistoryInfluxDBClient.makeWriteApi()) {
                    writeApi.writePoint(point);
                    logger.debug("寫入InfluxDB成功");
                }
            } catch (Exception e) {
                logger.error("寫入InfluxDB時發生錯誤", e);
            }
        }
    }
}
