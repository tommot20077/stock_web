package xyz.dowob.stockweb.Service.Currency;

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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

@Service
public class CurrencyInfluxDBService {
    private final InfluxDBClient currencyDBClient;
    Logger logger = LoggerFactory.getLogger(CurrencyInfluxDBService.class);
    @Autowired
    public CurrencyInfluxDBService(@Qualifier("CurrencyInfluxDBClient")InfluxDBClient CurrencyDBClient) {
        this.currencyDBClient = CurrencyDBClient;
    }

    public void writeToInflux(String currency, BigDecimal rate, ZonedDateTime zonedDateTime) {
        logger.debug("讀取匯率數據");
        long epochMilli = zonedDateTime.toInstant().toEpochMilli();

        Point point = Point.measurement("exchange_rate")
                .addTag("Currency", currency)
                .addField("rate", rate.doubleValue())
                .time(epochMilli, WritePrecision.MS);
        logger.debug("建立InfluxDB Point");

        try {
            logger.debug("連接InfluxDB成功");
            try (WriteApi writeApi = currencyDBClient.makeWriteApi()) {
                writeApi.writePoint(point);
                logger.debug("寫入InfluxDB成功");
            }
        } catch (Exception e) {
            logger.error("寫入InfluxDB時發生錯誤", e);
        }
    }
}
