package xyz.dowob.stockweb.Service.Currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Exception.SubscriptionExceptions;
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

import static xyz.dowob.stockweb.Exception.AssetExceptions.ErrorEnum.CURRENCY_CONVERT_ERROR;
import static xyz.dowob.stockweb.Exception.AssetExceptions.ErrorEnum.CURRENCY_DATA_UPDATE_ERROR;

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

    /**
     * CurrencyService構造函數
     *
     * @param currencyRepository    貨幣數據庫
     * @param subscribeRepository   訂閱數據庫
     * @param currencyInfluxService 貨幣InfluxDB服務
     */
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
    public void updateCurrencyData() throws AssetExceptions {
        RestTemplate restTemplate = new RestTemplate();
        String result = restTemplate.getForObject(apiUrl, String.class);
        processCurrencyData(result);
    }

    /**
     * 處理匯率資料
     * 將資料轉換為Currency對象並寫入資料庫
     * 拆解json數據，並根據數據是否存在進行新增或更新
     *
     * @param jsonData 匯率資料
     */
    public void processCurrencyData(String jsonData) throws AssetExceptions {
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
                currencyInfluxService.writeToInflux(currency, exRate, zonedDateTime);
                Optional<Currency> existingData = currencyRepository.findByCurrency(currency);
                if (existingData.isPresent()) {
                    Currency data = existingData.get();
                    if (data.getExchangeRate().compareTo(exRate) != 0) {
                        data.setExchangeRate(exRate);
                        data.setUpdateTime(updateTime);
                        currencyRepository.save(data);
                    }
                } else {
                    Currency currencyData = new Currency();
                    currencyData.setCurrency(currency);
                    currencyData.setExchangeRate(exRate);
                    currencyData.setUpdateTime(updateTime);
                    currencyData.setAssetType(AssetType.CURRENCY);
                    currencyRepository.save(currencyData);
                }
            });
        } catch (Exception e) {
            throw new AssetExceptions(CURRENCY_DATA_UPDATE_ERROR);
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
     */
    public BigDecimal convertCurrency(String originCurrency, String targetCurrency, String amount) throws AssetExceptions {
        BigDecimal amountDecimal = new BigDecimal(amount);
        Currency originCurrencyData = currencyRepository.findByCurrency(originCurrency).orElse(null);
        Currency targetCurrencyData = currencyRepository.findByCurrency(targetCurrency).orElse(null);
        if (originCurrencyData != null && targetCurrencyData != null) {
            return amountDecimal.multiply(targetCurrencyData.getExchangeRate()
                                                            .divide(originCurrencyData.getExchangeRate(), 6, RoundingMode.HALF_UP));
        }
        throw new AssetExceptions(CURRENCY_CONVERT_ERROR, originCurrency, targetCurrency);
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
        currencies.forEach(currency -> {
            Currency currencyData = currencyRepository.findByCurrency(currency).orElse(null);
            if (currencyData != null) {
                rates.put("USD" + currency, currencyData.getExchangeRate());
            } else {
                rates.put("USD" + currency, BigDecimal.ZERO);
            }
        });
        return rates;
    }

    /**
     * 獲取用戶訂閱的貨幣對
     * 根據用戶獲取訂閱的貨幣對
     * 通過查詢資料庫獲取訂閱資料
     *
     * @param user 用戶
     */
    public void subscribeCurrency(String from, String to, User user) throws SubscriptionExceptions, AssetExceptions {
        if (from.equals(to)) {
            throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_SAME_ASSET, from);
        }
        Currency fromCurrency = currencyRepository.findByCurrency(from)
                                                  .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_NOT_FOUND, from));
        Currency toCurrency = currencyRepository.findByCurrency(to)
                                                .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_NOT_FOUND, to));
        subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), toCurrency.getId(), fromCurrency.getCurrency())
                           .ifPresent(subscribe -> {
                               throw new RuntimeException(new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_ALREADY_EXIST,
                                                                                     String.format("%s ⇄ %s", from, to)));
                           });
        subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), fromCurrency.getId(), toCurrency.getCurrency())
                           .ifPresent(subscribe -> {
                               throw new RuntimeException(new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_ALREADY_EXIST,
                                                                                     String.format("%s ⇄ %s", from, to)));
                           });
        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(toCurrency);
        subscribe.setChannel(fromCurrency.getCurrency());
        subscribe.setUserSubscribed(true);
        subscribe.setRemoveAble(true);
        subscribeRepository.save(subscribe);
    }

    /**
     * 取消用戶訂閱的貨幣對
     * 根據用戶取消訂閱的貨幣對
     * 通過查詢資料庫獲取訂閱資料
     *
     * @param user 用戶
     */
    public void unsubscribeCurrency(String from, String to, User user) {
        if (from.equals(to)) {
            throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_SAME_ASSET, from);
        }
        Currency fromCurrency = currencyRepository.findByCurrency(from)
                                                  .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_NOT_FOUND, from));
        Currency toCurrency = currencyRepository.findByCurrency(to)
                                                .orElseThrow(() -> new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_NOT_FOUND, to));
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                                   toCurrency.getId(),
                                                                                   fromCurrency.getCurrency()).orElse(null);
        if (subscribe == null) {
            subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), fromCurrency.getId(), toCurrency.getCurrency())
                                           .orElseThrow(() -> new RuntimeException(new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_NOT_EXIST,
                                                                                                              String.format("%s ⇄ %s",
                                                                                                                            from,
                                                                                                                            to))));
        }
        if (subscribe.isRemoveAble()) {
            subscribeRepository.delete(subscribe);
        } else {
            throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_CANNOT_UNSUBSCRIBE,
                                             String.format("%s ⇄ %s", from, to),
                                             user.getUsername());
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
