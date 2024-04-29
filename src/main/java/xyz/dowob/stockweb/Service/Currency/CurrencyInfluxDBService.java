package xyz.dowob.stockweb.Service.Currency;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * @author yuan
 */
@Service
public class CurrencyInfluxDBService {
    private final InfluxDBClient currencyDBClient;
    private final AssetInfluxMethod assetInfluxMethod;
    Logger logger = LoggerFactory.getLogger(CurrencyInfluxDBService.class);

    @Autowired
    public CurrencyInfluxDBService(
            @Qualifier("CurrencyInfluxClient") InfluxDBClient currencyClient, AssetInfluxMethod assetInfluxMethod) {
        this.currencyDBClient = currencyClient;
        this.assetInfluxMethod = assetInfluxMethod;
    }

    public void writeToInflux(String currency, BigDecimal rate, ZonedDateTime zonedDateTime) {
        logger.debug("讀取匯率數據");
        long epochMilli = zonedDateTime.toInstant().toEpochMilli();

        Point point = Point.measurement("exchange_rate")
                           .addTag("Currency", currency)
                           .addField("rate", rate.doubleValue())
                           .time(epochMilli, WritePrecision.MS);
        logger.debug("建立InfluxDB Point");
        assetInfluxMethod.writeToInflux(currencyDBClient, point);
    }
}
