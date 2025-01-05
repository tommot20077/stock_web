package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author yuan
 * 台灣股票有關於Influx服務
 */
@Service
public class StockTwInfluxService {
    private final InfluxDBClient StockTwInfluxDBClient;

    private final InfluxDBClient StockTwHistoryInfluxDBClient;

    private final AssetInfluxMethod assetInfluxMethod;

    private final CurrencyRepository currencyRepository;

    private final RetryTemplate retryTemplate;

    private final OffsetDateTime startDateTime = Instant.parse("1970-01-01T00:00:00Z").atOffset(ZoneOffset.UTC);

    private final OffsetDateTime stopDateTime = Instant.parse("2099-12-31T23:59:59Z").atOffset(ZoneOffset.UTC);

    @Value("${db.influxdb.org}")
    private String org;

    @Value("${db.influxdb.bucket.stock_tw}")
    private String stockBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}")
    private String stockHistoryBucket;

    @Value("{db.influxdb.max_send_num:100}")
    private int maxSendNum;

    /**
     * StockTwInfluxService構造函數
     *
     * @param stockTwInfluxClient        台灣股票InfluxDB客戶端
     * @param stockTwHistoryInfluxClient 台灣股票歷史InfluxDB客戶端
     * @param assetInfluxMethod          資產InfluxDB方法
     * @param currencyRepository         貨幣資料庫
     * @param retryTemplate              重試模板
     */
    public StockTwInfluxService (
            @Qualifier("StockTwInfluxClient") InfluxDBClient stockTwInfluxClient, @Qualifier("StockTwHistoryInfluxClient")
    InfluxDBClient stockTwHistoryInfluxClient, AssetInfluxMethod assetInfluxMethod, CurrencyRepository currencyRepository, RetryTemplate retryTemplate) {
        StockTwInfluxDBClient = stockTwInfluxClient;
        StockTwHistoryInfluxDBClient = stockTwHistoryInfluxClient;
        this.assetInfluxMethod = assetInfluxMethod;
        this.currencyRepository = currencyRepository;
        this.retryTemplate = retryTemplate;
    }

    /**
     * 五秒搓合交易的kline數據寫入InfluxDB
     *
     * @param msgArray kline數據
     */
    public void writeToInflux (Map<String, Map<String, String>> msgArray) {
        List<Point> points = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> dataMap : msgArray.entrySet()) {
            String stockId = dataMap.getKey();
            Map<String, String> data = dataMap.getValue();
            String time = data.get("time");
            Double closeDouble = Double.parseDouble(data.get("close"));
            Double highDouble = Double.parseDouble(data.get("high"));
            Double openDouble = Double.parseDouble(data.get("open"));
            Double lowDouble = Double.parseDouble(data.get("low"));
            Double volumeDouble = Double.parseDouble(data.get("volume"));
            Point point = Point.measurement("kline_data")
                               .addTag("stock_tw", stockId)
                               .addField("open", openDouble)
                               .addField("close", closeDouble)
                               .addField("high", highDouble)
                               .addField("low", lowDouble)
                               .addField("volume", volumeDouble)
                               .time(Long.parseLong(time), WritePrecision.MS);
            points.add(point);
            if (points.size() >= maxSendNum) {
                assetInfluxMethod.writeToInflux(StockTwInfluxDBClient, points);
                points.clear();
            }
        }
        if (!points.isEmpty()) {
            assetInfluxMethod.writeToInflux(StockTwInfluxDBClient, points);
        }
    }

    /**
     * 歷史股價數據寫入InfluxDB
     *
     * @param dataArray 股價數據
     * @param stockCode 股票代碼
     */
    public void writeStockTwHistoryToInflux (ArrayNode dataArray, String stockCode) throws AssetExceptions {
        List<Point> points = new ArrayList<>();
        for (JsonNode dataEntry : dataArray) {
            String dateStr = dataEntry.get(0).asText();
            Long tLong = formattedRocData(dateStr);
            String numberOfStocksVolume = dataEntry.get(1).asText().replace(",", "");
            String openingPrice = dataEntry.get(3).asText();
            String highestPrice = dataEntry.get(4).asText();
            String lowestPrice = dataEntry.get(5).asText();
            String closingPrice = dataEntry.get(6).asText();
            if (Objects.equals(openingPrice, "--") || Objects.equals(highestPrice, "--") || Objects.equals(lowestPrice,
                                                                                                           "--"
            ) || Objects.equals(closingPrice, "--")) {
                continue;
            }
            Point point = formatKlineDataToPoint(tLong,
                                                 stockCode,
                                                 numberOfStocksVolume,
                                                 openingPrice,
                                                 highestPrice,
                                                 lowestPrice,
                                                 closingPrice
            );
            points.add(point);
            if (points.size() >= maxSendNum) {
                assetInfluxMethod.writeToInflux(StockTwHistoryInfluxDBClient, points);
                points.clear();
            }
        }
        if (!points.isEmpty()) {
            assetInfluxMethod.writeToInflux(StockTwHistoryInfluxDBClient, points);
        }
    }

    /**
     * 每日更新股價數據寫入InfluxDB
     *
     * @param node      股價數據
     * @param timestamp 日期Long格式
     */
    public void writeUpdateDailyStockTwHistoryToInflux (JsonNode node, Long timestamp, boolean isTwse, String... tpexStockCode) throws AssetExceptions {
        if (node == null) {
            return;
        }
        String stockCode;
        String tradeVolume;
        String openingPrice;
        String highestPrice;
        String lowestPrice;
        String closingPrice;
        if (isTwse) {
            stockCode = node.path("Code").asText();
            tradeVolume = node.path("TradeVolume").asText();
            openingPrice = node.path("OpeningPrice").asText();
            highestPrice = node.path("HighestPrice").asText();
            lowestPrice = node.path("LowestPrice").asText();
            closingPrice = node.path("ClosingPrice").asText();
        } else {
            stockCode = tpexStockCode[0];
            tradeVolume = node.get(1).asText().replace(",", "");
            openingPrice = node.get(3).asText();
            highestPrice = node.get(4).asText();
            lowestPrice = node.get(5).asText();
            closingPrice = node.get(6).asText();
        }

        if (Objects.equals(openingPrice, "--") || Objects.equals(highestPrice, "--") || Objects.equals(lowestPrice, "--") || Objects.equals(
                closingPrice,
                "--"
        )) {
            return;
        }
        Point point = formatKlineDataToPoint(timestamp, stockCode, tradeVolume, openingPrice, highestPrice, lowestPrice, closingPrice);
        assetInfluxMethod.writeToInflux(StockTwHistoryInfluxDBClient, point);
    }

    /**
     * 將民國日期轉換為西元日期
     *
     * @param dateStr 民國日期
     *
     * @return 西元日期
     */
    private Long formattedRocData (String dateStr) {
        String[] dateParts = dateStr.split("/");
        int yearRoc = Integer.parseInt(dateParts[0]);
        int yearAd = yearRoc + 1911;
        String formattedDate = yearAd + "/" + dateParts[1] + "/" + dateParts[2];
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDate localDate = LocalDate.parse(formattedDate, formatter);
        Instant instant = localDate.atStartOfDay(ZoneId.of("Asia/Taipei")).toInstant();
        return instant.toEpochMilli();
    }

    /**
     * 刪除股票代碼的歷史資料
     *
     * @param stockCode 股票代碼
     *
     * @throws RuntimeException 重試失敗
     */
    public void deleteDataByStockCode (String stockCode) {
        String predicate = String.format("_measurement=\"kline_data\" AND stock_tw=\"%s\"", stockCode);
        try {
            retryTemplate.doWithRetry(() -> {
                StockTwInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, stockBucket, org);
                StockTwHistoryInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, stockHistoryBucket, org);
            });
        } catch (Exception e) {
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getMessage(), e);
        }
    }

    /**
     * 將kline數據寫入InfluxDB
     *
     * @param timestamp    日期Long格式
     * @param stockCode    股票代碼
     * @param tradeVolume  成交股數
     * @param openingPrice 開盤價
     * @param highestPrice 最高價
     * @param lowestPrice  最低價
     * @param closingPrice 收盤價
     */
    private Point formatKlineDataToPoint (Long timestamp, String stockCode, String tradeVolume, String openingPrice, String highestPrice, String lowestPrice, String closingPrice) throws AssetExceptions {

        Currency twdCurrency = currencyRepository.findByCurrency("TWD")
                                                 .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.DEFAULT_CURRENCY_NOT_FOUND,
                                                                                        "TWD"
                                                 ));
        BigDecimal twdToUsd = twdCurrency.getExchangeRate();
        return Point.measurement("kline_data")
                    .addTag("stock_tw", stockCode)
                    .addField("high", formatPrice(highestPrice, twdToUsd))
                    .addField("low", formatPrice(lowestPrice, twdToUsd))
                    .addField("open", formatPrice(openingPrice, twdToUsd))
                    .addField("close", formatPrice(closingPrice, twdToUsd))
                    .addField("volume", Double.parseDouble(tradeVolume))
                    .time(timestamp, WritePrecision.MS);

    }

    /**
     * 格式化價格，將台幣轉換為美元，並保留三位小數
     *
     * @param price        價格
     * @param exchangeRate 匯率
     *
     * @return 格式化後的價格
     */
    private Double formatPrice (String price, BigDecimal exchangeRate) {
        return (new BigDecimal(price)).divide(exchangeRate, 3, RoundingMode.HALF_UP).doubleValue();
    }
}
