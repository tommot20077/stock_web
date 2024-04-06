package xyz.dowob.stockweb.Service.Currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
public class CurrencyService {
    @Value(value = "${currency.api.url}")
    private String API_URL;

    private final CurrencyRepository currencyRepository;

    private final SubscribeRepository subscribeRepository;
    private final CurrencyInfluxDBService currencyInfluxDBService;
    Logger logger = LoggerFactory.getLogger(CurrencyService.class);

    @Autowired
    public CurrencyService(CurrencyRepository currencyRepository, SubscribeRepository subscribeRepository, CurrencyInfluxDBService currencyInfluxDBService) {
        this.currencyRepository = currencyRepository;
        this.subscribeRepository = subscribeRepository;
        this.currencyInfluxDBService = currencyInfluxDBService;
    }



    @Async
    @Transactional(rollbackFor = {Exception.class})
    public void updateCurrencyData() {
        logger.info("獲取匯率資料中");
        RestTemplate restTemplate = new RestTemplate();
        String result = restTemplate.getForObject(API_URL, String.class);
        logger.info("開始新匯率資料");
        processCurrencyData(result);
    }

    public void processCurrencyData(String jsonData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            JsonNode jsonNode = objectMapper.readTree(jsonData);



            jsonNode.fields().forEachRemaining(entry -> {
                String currency;
                String key = entry.getKey();
                if(entry.getKey().equals("USD")) {
                    currency = "USD";
                }else if(entry.getKey().equals("USDUSD")) {
                    return;
                }
                else if(key.startsWith("USD") && !Character.isDigit(key.charAt(3))) {
                    currency = key.replace("USD", "");
                } else {
                    currency = key;
                }

                BigDecimal exRate = new BigDecimal(entry.getValue().get("Exrate").asText());
                LocalDateTime updateTime = LocalDateTime.parse(entry.getValue().get("UTC").asText(), formatter);
                ZonedDateTime zonedDateTime = updateTime.atZone(TimeZone.getTimeZone("UTC").toZoneId());

                logger.debug("開始寫入InfluxDB");
                currencyInfluxDBService.writeToInflux(currency, exRate, zonedDateTime);

                Optional<Currency> existingData = currencyRepository.findByCurrency(currency);
                if (existingData.isPresent()) {
                    logger.debug(currency + "的匯率資料已存在");
                    Currency data = existingData.get();
                    if (data.getExchangeRate().compareTo(exRate) != 0) {
                        logger.debug("開始更新"+ currency + "的匯率資料");
                        data.setExchangeRate(exRate);
                        data.setUpdateTime(updateTime);
                        currencyRepository.save(data);
                        logger.debug(currency + "的匯率資料更新完成");
                    } else {
                        logger.debug(currency + "的匯率資料無需更新");
                    }
                } else {
                    logger.debug(currency + "的匯率資料不存在，新增資料中");
                    Currency currencyData = new Currency();
                    currencyData.setCurrency(currency);
                    currencyData.setExchangeRate(exRate);
                    currencyData.setUpdateTime(updateTime);
                    currencyData.setAssetType(AssetType.CURRENCY);
                    currencyRepository.save(currencyData);
                    logger.debug(currency+ "的匯率資料新增完成");
                }
            });
            logger.info("匯率資料新增完成");
        } catch (Exception e) {
            logger.error("轉換匯率資料失敗: " + e.getMessage());
            throw new RuntimeException("轉換匯率資料失敗: " + e.getMessage());
        }
    }

    public BigDecimal convertCurrency(String originCurrency, String targetCurrency, String amount) {
        BigDecimal amountDecimal = new BigDecimal(amount);

        Currency originCurrencyData = currencyRepository.findByCurrency(originCurrency).orElse(null);
        Currency targetCurrencyData = currencyRepository.findByCurrency(targetCurrency).orElse(null);
        if (originCurrencyData != null && targetCurrencyData != null) {
            return amountDecimal.multiply(
                    targetCurrencyData.getExchangeRate().divide(
                            originCurrencyData.getExchangeRate(), 6, RoundingMode.HALF_UP));
        }
        throw new RuntimeException("無法轉換指定貨幣的資料");
    }

    public Map<String, BigDecimal> getExchangeRates(List<String> currencies) {
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String currency : currencies) {
            Currency currencyData = currencyRepository.findByCurrency(currency).orElse(null);
            if (currencyData != null) {
                rates.put("USD"+currency, currencyData.getExchangeRate());
            } else {
                rates.put("USD"+currency, BigDecimal.ZERO);
                logger.warn("無法取得"+ currency +"的匯率資料");
            }
        }
        return rates;
    }

    public void subscribeCurrency(String from, String to, User user) {
        if (from.equals(to)) {
            throw new RuntimeException("訂閱貨幣不可相同");
        }
        Currency fromCurrency = currencyRepository.findByCurrency(to).orElseThrow(() -> new RuntimeException("無此貨幣資料"));
        Currency toCurrency = currencyRepository.findByCurrency(from).orElseThrow(() -> new RuntimeException("無此貨幣資料"));

        subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), toCurrency.getId(), fromCurrency.getId().toString()).ifPresent(subscribe -> {
            throw new RuntimeException("已訂閱過此貨幣對" + from + " <-> " + to);
        });
        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(toCurrency);
        subscribe.setChannel(fromCurrency.getId().toString());
        subscribe.setUserSubscribed(true);
        subscribeRepository.save(subscribe);
        logger.info(user.getUsername() + "訂閱" + from + " <-> " + to);

    }

    public void unsubscribeCurrency(String from, String to, User user) {
        if (from.equals(to)) {
            throw new RuntimeException("取消訂閱貨幣不可相同");
        }
        Currency fromCurrency = currencyRepository.findByCurrency(to).orElseThrow(() -> new RuntimeException("無此貨幣資料"));
        Currency toCurrency = currencyRepository.findByCurrency(from).orElseThrow(() -> new RuntimeException("無此貨幣資料"));
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), toCurrency.getId(), fromCurrency.getId().toString()).orElse(null);
        if (subscribe != null) {
            subscribeRepository.delete(subscribe);
            logger.info(user.getUsername() + "取消訂閱" + from + " <-> " + to);
        } else {
            throw new RuntimeException("未訂閱過此貨幣對" + from + " <-> " + to);
        }
    }

    public List<String> getCurrencyList() {
        return currencyRepository.findAllDistinctCurrencies();
    }

}
