package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
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

    Logger logger = LoggerFactory.getLogger(StockTwInfluxService.class);

    private final OffsetDateTime startDateTime = Instant.parse("1970-01-01T00:00:00Z").atOffset(ZoneOffset.UTC);

    private final OffsetDateTime stopDateTime = Instant.parse("2099-12-31T23:59:59Z").atOffset(ZoneOffset.UTC);

    @Autowired
    public StockTwInfluxService(
            @Qualifier("StockTwInfluxClient") InfluxDBClient stockTwInfluxClient, @Qualifier("StockTwHistoryInfluxClient") InfluxDBClient stockTwHistoryInfluxClient, AssetInfluxMethod assetInfluxMethod, CurrencyRepository currencyRepository, RetryTemplate retryTemplate) {
        StockTwInfluxDBClient = stockTwInfluxClient;
        StockTwHistoryInfluxDBClient = stockTwHistoryInfluxClient;
        this.assetInfluxMethod = assetInfluxMethod;
        this.currencyRepository = currencyRepository;
        this.retryTemplate = retryTemplate;
    }

    @Value("${db.influxdb.org}")
    private String org;

    @Value("${db.influxdb.bucket.stock_tw}")
    private String stockBucket;

    @Value("${db.influxdb.bucket.stock_tw_history}")
    private String stockHistoryBucket;

    /**
     * 五秒搓合交易的kline數據寫入InfluxDB
     *
     * @param msgArray kline數據
     */
    public void writeStockTwToInflux(JsonNode msgArray) {
        logger.debug("讀取即時股價數據");
        Currency twdCurrency = currencyRepository.findByCurrency("TWD").orElseThrow(() -> new RuntimeException("找不到TWD幣別"));
        BigDecimal twdToUsd = twdCurrency.getExchangeRate();
        for (JsonNode msgNode : msgArray) {
            logger.debug(msgNode.toString());
            if (Objects.equals(msgNode.path("z").asText(), "-")) {
                continue;
            }
            logger.debug("z = " + Double.parseDouble(msgNode.path("z").asText()) + ", c = " + msgNode.path("c")
                                                                                                     .asText() + ", tlong = " + msgNode.path(
                    "tlong").asText() + ", o = " + Double.parseDouble(msgNode.path("o")
                                                                             .asText()) + ", h = " + Double.parseDouble(msgNode.path("h")
                                                                                                                               .asText()) + ", l = " + Double.parseDouble(
                    msgNode.path("l").asText()) + ", v = " + Double.parseDouble(msgNode.path("v").asText()));

            BigDecimal priceUsd = (new BigDecimal(msgNode.path("z").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);
            BigDecimal highUsd = (new BigDecimal(msgNode.path("h").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);
            BigDecimal openUsd = (new BigDecimal(msgNode.path("o").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);
            BigDecimal lowUsd = (new BigDecimal(msgNode.path("l").asText())).divide(twdToUsd, 3, RoundingMode.HALF_UP);

            logger.debug("(轉換後) z = " + priceUsd + ", c = " + msgNode.path("c").asText() + ", tlong = " + msgNode.path("tlong")
                                                                                                                    .asText() + ", o = " + openUsd + ", h = " + highUsd + ", l = " + lowUsd + ", v = " + msgNode.path(
                    "v").asText());

            Double priceDouble = priceUsd.doubleValue();
            Double highDouble = highUsd.doubleValue();
            Double openDouble = openUsd.doubleValue();
            Double lowDouble = lowUsd.doubleValue();
            Double volumeDouble = Double.parseDouble(msgNode.path("v").asText());
            String time = msgNode.path("tlong").asText();
            String stockId = msgNode.path("c").asText();


            if (Objects.equals(msgNode.path("z").asText(), "--") || Objects.equals(msgNode.path("h").asText(), "--") || Objects.equals(
                    msgNode.path("o").asText(),
                    "--") || Objects.equals(msgNode.path("l").asText(), "--")) {
                continue;
            }

            Point point = Point.measurement("kline_data")
                               .addTag("stock_tw", stockId)
                               .addField("close", priceDouble)
                               .addField("high", highDouble)
                               .addField("low", lowDouble)
                               .addField("open", openDouble)
                               .addField("volume", volumeDouble)
                               .time(Long.parseLong(time), WritePrecision.MS);
            logger.debug("建立InfluxDB Point");
            assetInfluxMethod.writeToInflux(StockTwInfluxDBClient, point);
        }
    }

    /**
     * 歷史股價數據寫入InfluxDB
     *
     * @param dataArray 股價數據
     * @param stockCode 股票代碼
     */
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

            logger.debug("(轉換前)日期: " + dateStr + ", 成交股數: " + numberOfStocksVolume + ", 開盤價: " + openingPrice + ", 最高價: " + highestPrice + ", 最低價: " + lowestPrice + ", 收盤價: " + closingPrice);

            if (Objects.equals(openingPrice, "--") || Objects.equals(highestPrice, "--") || Objects.equals(lowestPrice,
                                                                                                           "--") || Objects.equals(
                    closingPrice,
                    "--")) {
                continue;
            }
            writeKlineDataPoint(tLong, stockCode, numberOfStocksVolume, openingPrice, highestPrice, lowestPrice, closingPrice);
        }
    }

    /**
     * 每日更新股價數據寫入InfluxDB
     *
     * @param node          股價數據
     * @param todayLongTime 日期Long格式
     */
    public void writeUpdateDailyStockTwHistoryToInflux(JsonNode node, Long todayLongTime) {
        logger.debug("讀取每日更新股價數據");
        String stockCode = node.path("Code").asText();
        String tradeVolume = node.path("TradeVolume").asText();
        String openingPrice = node.path("OpeningPrice").asText();
        String highestPrice = node.path("HighestPrice").asText();
        String lowestPrice = node.path("LowestPrice").asText();
        String closingPrice = node.path("ClosingPrice").asText();

        logger.debug("(轉換前)日期(Long): " + todayLongTime.toString() + ", 成交股數: " + tradeVolume + ", 開盤價: " + openingPrice + ", 最高價: " + highestPrice + ", 最低價: " + lowestPrice + ", 收盤價: " + closingPrice);

        if (Objects.equals(openingPrice, "--") || Objects.equals(highestPrice, "--") || Objects.equals(lowestPrice, "--") || Objects.equals(
                closingPrice,
                "--")) {
            return;
        }

        writeKlineDataPoint(todayLongTime, stockCode, tradeVolume, openingPrice, highestPrice, lowestPrice, closingPrice);
    }

    /**
     * 將民國日期轉換為西元日期
     *
     * @param dateStr 民國日期
     *
     * @return 西元日期
     */
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


    /**
     * 刪除股票代碼的歷史資料
     *
     * @param stockCode 股票代碼
     *
     * @throws RuntimeException 重試失敗
     */
    public void deleteDataByStockCode(String stockCode) {
        String predicate = String.format("_measurement=\"kline_data\" AND stock_tw=\"%s\"", stockCode);
        logger.debug("刪除" + stockCode + "的歷史資料");
        try {
            retryTemplate.doWithRetry(() -> {
                try {
                    logger.debug("連接InfluxDB成功");
                    StockTwInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, stockBucket, org);
                    StockTwHistoryInfluxDBClient.getDeleteApi().delete(startDateTime, stopDateTime, predicate, stockHistoryBucket, org);
                    logger.debug("刪除資料成功");
                } catch (Exception e) {
                    logger.error("刪除資料時發生錯誤", e);
                }
            });
        } catch (Exception e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getMessage(), e);
        }
    }


    /**
     * 將kline數據寫入InfluxDB
     *
     * @param todayLongTime 日期Long格式
     * @param stockCode     股票代碼
     * @param tradeVolume   成交股數
     * @param openingPrice  開盤價
     * @param highestPrice  最高價
     * @param lowestPrice   最低價
     * @param closingPrice  收盤價
     */
    private void writeKlineDataPoint(Long todayLongTime, String stockCode, String tradeVolume, String openingPrice, String highestPrice, String lowestPrice, String closingPrice) {
        Currency twdCurrency = currencyRepository.findByCurrency("TWD").orElseThrow(() -> new RuntimeException("找不到TWD幣別"));
        BigDecimal twdToUsd = twdCurrency.getExchangeRate();

        Double formatOpeningPrice = (new BigDecimal(openingPrice)).divide(twdToUsd, 3, RoundingMode.HALF_UP).doubleValue();
        Double formatHighestPrice = (new BigDecimal(highestPrice)).divide(twdToUsd, 3, RoundingMode.HALF_UP).doubleValue();
        Double formatLowestPrice = (new BigDecimal(lowestPrice)).divide(twdToUsd, 3, RoundingMode.HALF_UP).doubleValue();
        Double formatClosingPrice = (new BigDecimal(closingPrice)).divide(twdToUsd, 3, RoundingMode.HALF_UP).doubleValue();


        Point point = Point.measurement("kline_data")
                           .addTag("stock_tw", stockCode)
                           .addField("high", formatHighestPrice)
                           .addField("low", formatLowestPrice)
                           .addField("open", formatOpeningPrice)
                           .addField("close", formatClosingPrice)
                           .addField("volume", Double.parseDouble(tradeVolume))
                           .time(todayLongTime, WritePrecision.MS);
        logger.debug("建立InfluxDB Point");
        logger.debug("(轉換後)日期(Long): " + todayLongTime.toString() + ", 成交股數: " + tradeVolume + ", 開盤價: " + formatOpeningPrice + ", 最高價: " + formatHighestPrice + ", 最低價: " + formatLowestPrice + ", 收盤價: " + formatClosingPrice);

        assetInfluxMethod.writeToInflux(StockTwHistoryInfluxDBClient, point);
    }
}
