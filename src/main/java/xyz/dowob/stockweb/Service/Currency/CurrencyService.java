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

/**
 * @author yuan
 * 有關貨幣的業務邏輯
 */
@Service
public class CurrencyService {
    @Value(value = "${currency.api.url:https://tw.rter.info/capi.php}")
    private String apiUrl;

    private final CurrencyRepository currencyRepository;

    private final SubscribeRepository subscribeRepository;

    private final CurrencyInfluxService currencyInfluxService;

    Logger logger = LoggerFactory.getLogger(CurrencyService.class);

    /**
     * CurrencyService構造函數
     *
     * @param currencyRepository    貨幣數據庫
     * @param subscribeRepository   訂閱數據庫
     * @param currencyInfluxService 貨幣InfluxDB服務
     */
    @Autowired
    public CurrencyService(CurrencyRepository currencyRepository, SubscribeRepository subscribeRepository, CurrencyInfluxService currencyInfluxService) {
        this.currencyRepository = currencyRepository;
        this.subscribeRepository = subscribeRepository;
        this.currencyInfluxService = currencyInfluxService;
    }


    /**
     * 獲取匯率資料並更新資料庫
     * 使用Async注解，使方法異步執行
     * 使用Transactional注解，異步方法內的事務管理
     * 使用RestTemplate獲取貨幣資料
     */
    @Async
    @Transactional(rollbackFor = {Exception.class})
    public void updateCurrencyData() {
        logger.info("獲取匯率資料中");
        RestTemplate restTemplate = new RestTemplate();
        String result = restTemplate.getForObject(apiUrl, String.class);
        logger.info("開始新匯率資料");
        processCurrencyData(result);
    }

    /**
     * 處理匯率資料
     * 將資料轉換為Currency對象並寫入資料庫
     * 拆解json數據，並根據數據是否存在進行新增或更新
     *
     * @param jsonData 匯率資料
     */
    public void processCurrencyData(String jsonData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            JsonNode jsonNode = objectMapper.readTree(jsonData);


            jsonNode.fields().forEachRemaining(entry -> {
                String currency;
                String key = entry.getKey();
                if ("USD".equals(entry.getKey())) {
                    currency = "USD";
                } else if ("USDUSD".equals(entry.getKey())) {
                    return;
                } else if (key.startsWith("USD") && !Character.isDigit(key.charAt(3))) {
                    currency = key.replace("USD", "");
                } else {
                    currency = key;
                }

                BigDecimal exRate = new BigDecimal(entry.getValue().get("Exrate").asText());
                LocalDateTime updateTime = LocalDateTime.parse(entry.getValue().get("UTC").asText(), formatter);
                ZonedDateTime zonedDateTime = updateTime.atZone(TimeZone.getTimeZone("UTC").toZoneId());

                logger.debug("開始寫入InfluxDB");
                currencyInfluxService.writeToInflux(currency, exRate, zonedDateTime);

                Optional<Currency> existingData = currencyRepository.findByCurrency(currency);
                if (existingData.isPresent()) {
                    logger.debug(currency + "的匯率資料已存在");
                    Currency data = existingData.get();
                    if (data.getExchangeRate().compareTo(exRate) != 0) {
                        logger.debug("開始更新" + currency + "的匯率資料");
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
                    logger.debug(currency + "的匯率資料新增完成");
                }
            });
            logger.info("匯率資料新增完成");
        } catch (Exception e) {
            logger.error("轉換匯率資料失敗: " + e.getMessage());
            throw new RuntimeException("轉換匯率資料失敗: " + e.getMessage());
        }
    }

    /**
     * 轉換貨幣
     * 根據貨幣名稱和金額進行貨幣轉換
     * 通過查詢資料庫獲取匯率資料，並進行轉換
     *
     * @param originCurrency 原始貨幣
     * @param targetCurrency 目標貨幣
     * @param amount         金額
     *
     * @return 轉換後的金額
     *
     * @throws RuntimeException 無法轉換指定貨幣的資料
     */
    public BigDecimal convertCurrency(String originCurrency, String targetCurrency, String amount) {
        BigDecimal amountDecimal = new BigDecimal(amount);

        Currency originCurrencyData = currencyRepository.findByCurrency(originCurrency).orElse(null);
        Currency targetCurrencyData = currencyRepository.findByCurrency(targetCurrency).orElse(null);
        if (originCurrencyData != null && targetCurrencyData != null) {
            return amountDecimal.multiply(targetCurrencyData.getExchangeRate()
                                                            .divide(originCurrencyData.getExchangeRate(), 6, RoundingMode.HALF_UP));
        }
        throw new RuntimeException("無法轉換指定貨幣的資料");
    }


    /**
     * 獲取匯率資料
     * 根據貨幣名稱列表獲取匯率資料
     * 通過查詢資料庫獲取匯率資料
     *
     * @param currencies 貨幣名稱列表
     *
     * @return 匯率資料
     */
    public Map<String, BigDecimal> getExchangeRates(List<String> currencies) {
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String currency : currencies) {
            Currency currencyData = currencyRepository.findByCurrency(currency).orElse(null);
            if (currencyData != null) {
                rates.put("USD" + currency, currencyData.getExchangeRate());
            } else {
                rates.put("USD" + currency, BigDecimal.ZERO);
                logger.warn("無法取得" + currency + "的匯率資料");
            }
        }
        return rates;
    }

    /**
     * 獲取用戶訂閱的貨幣對
     * 根據用戶獲取訂閱的貨幣對
     * 通過查詢資料庫獲取訂閱資料
     *
     * @param user 用戶
     */
    public void subscribeCurrency(String from, String to, User user) {
        if (from.equals(to)) {
            throw new RuntimeException("訂閱貨幣不可相同");
        }
        Currency fromCurrency = currencyRepository.findByCurrency(to).orElseThrow(() -> new RuntimeException("無此貨幣資料"));
        Currency toCurrency = currencyRepository.findByCurrency(from).orElseThrow(() -> new RuntimeException("無此貨幣資料"));

        subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), toCurrency.getId(), fromCurrency.getCurrency())
                           .ifPresent(subscribe -> {
                               throw new RuntimeException("已訂閱過此貨幣對" + from + "  ⇄  " + to);
                           });
        subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), fromCurrency.getId(), toCurrency.getCurrency())
                           .ifPresent(subscribe -> {
                               throw new RuntimeException("已訂閱過此貨幣對" + from + "  ⇄  " + to);
                           });


        logger.debug("用戶主動訂閱，此訂閱設定可刪除");
        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(toCurrency);
        subscribe.setChannel(fromCurrency.getCurrency());
        subscribe.setUserSubscribed(true);
        subscribe.setRemoveAble(true);
        subscribeRepository.save(subscribe);
        logger.info(user.getUsername() + "訂閱" + from + "  ⇄  " + to);

    }

    /**
     * 取消用戶訂閱的貨幣對
     * 根據用戶取消訂閱的貨幣對
     * 通過查詢資料庫獲取訂閱資料
     *
     * @param user 用戶
     */
    public void unsubscribeCurrency(String from, String to, User user) throws Exception {
        if (from.equals(to)) {
            throw new RuntimeException("取消訂閱貨幣不可相同");
        }
        Currency fromCurrency = currencyRepository.findByCurrency(to).orElseThrow(() -> new RuntimeException("無此貨幣資料"));
        Currency toCurrency = currencyRepository.findByCurrency(from).orElseThrow(() -> new RuntimeException("無此貨幣資料"));
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                                   toCurrency.getId(),
                                                                                   fromCurrency.getCurrency()).orElse(null);
        if (subscribe == null) {
            subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), fromCurrency.getId(), toCurrency.getCurrency())
                                           .orElseThrow(() -> new RuntimeException("未訂閱過此貨幣對" + from + "  ⇄  " + to));
        }
        if (subscribe.isRemoveAble()) {
            subscribeRepository.delete(subscribe);
            logger.info(user.getUsername() + "取消訂閱" + from + "  ⇄  " + to);
        } else {
            logger.warn("此訂閱: " + fromCurrency.getCurrency() + "  ⇄  " + toCurrency.getCurrency() + " 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
            throw new Exception("此訂閱: " + fromCurrency.getCurrency() + "  ⇄  " + toCurrency.getCurrency() + " 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
        }
    }

    /**
     * 獲取貨幣列表
     * 獲取資料庫中所有貨幣的名稱
     *
     * @return 貨幣列表
     */
    public List<String> getCurrencyList() {
        return currencyRepository.findAllDistinctCurrencies();
    }
}
