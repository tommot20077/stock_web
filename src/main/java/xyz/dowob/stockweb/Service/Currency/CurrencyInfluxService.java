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
import java.math.RoundingMode;
import java.time.ZonedDateTime;

/**
 * @author yuan
 * <p>
 * 有關貨幣的InfluxDB業務邏輯
 */
@Service
public class CurrencyInfluxService {
    private final InfluxDBClient currencyClient;

    private final AssetInfluxMethod assetInfluxMethod;

    Logger logger = LoggerFactory.getLogger(CurrencyInfluxService.class);

    /**
     * CurrencyInfluxService構造函數
     *
     * @param currencyClient    貨幣InfluxDB客戶端
     * @param assetInfluxMethod 資產InfluxDB方法
     */
    @Autowired
    public CurrencyInfluxService(
            @Qualifier("CurrencyInfluxClient") InfluxDBClient currencyClient, AssetInfluxMethod assetInfluxMethod) {
        this.currencyClient = currencyClient;
        this.assetInfluxMethod = assetInfluxMethod;
    }

    /**
     * 將匯率數據寫入InfluxDB
     *
     * @param currency      貨幣
     * @param rate          匯率
     * @param zonedDateTime 時間
     */
    public void writeToInflux(String currency, BigDecimal rate, ZonedDateTime zonedDateTime) {
        logger.debug("讀取匯率數據");
        long epochMilli = zonedDateTime.toInstant().toEpochMilli();
        BigDecimal formattedRate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP);
        Point point = Point.measurement("exchange_rate")
                           .addTag("Currency", currency)
                           .addField("rate", formattedRate.doubleValue())
                           .time(epochMilli, WritePrecision.MS);
        logger.debug("建立InfluxDB Point");
        assetInfluxMethod.writeToInflux(currencyClient, point);
    }
}
