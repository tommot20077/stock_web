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

import java.util.Map;

@Service
public class CryptoInfluxDBService {
    private final InfluxDBClient CryptoInfluxDBClient;
    Logger logger = LoggerFactory.getLogger(CryptoInfluxDBService.class);
    @Autowired
    public CryptoInfluxDBService(@Qualifier("CryptoInfluxDBClient")InfluxDBClient CryptoInfluxDBClient) {
        this.CryptoInfluxDBClient = CryptoInfluxDBClient;
    }

    public void writeToInflux(Map<String, Object> kline) {
        logger.debug("讀取kline數據");
        logger.debug(kline.toString());
        String time = kline.get("t").toString();
        Double open = Double.parseDouble(kline.get("o").toString()); // 開盤價
        Double close = Double.parseDouble(kline.get("c").toString()); // 收盤價
        Double high = Double.parseDouble(kline.get("h").toString()); // 最高價
        Double low = Double.parseDouble(kline.get("l").toString()); // 最低價
        Double volume = Double.parseDouble(kline.get("v").toString()); // 成交量

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
            try (WriteApi writeApi = CryptoInfluxDBClient.makeWriteApi()) {
                writeApi.writePoint(point);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
        }
    }
}
