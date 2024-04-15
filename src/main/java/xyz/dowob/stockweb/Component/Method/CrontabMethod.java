package xyz.dowob.stockweb.Component.Method;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.Common.Property.PropertyService;
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author tommo
 */
@Component
public class CrontabMethod {
    private final TokenService tokenService;
    private final CurrencyService currencyService;
    private final StockTwService stockTwService;
    private final CryptoService cryptoService;
    private final UserService userService;
    private final PropertyService propertyService;
    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    private final SubscribeMethod subscribeMethod;
    @Autowired
    public CrontabMethod(TokenService tokenService, CurrencyService currencyService, StockTwService stockTwService, CryptoService cryptoService, UserService userService, PropertyService propertyService , CryptoWebSocketHandler cryptoWebSocketHandler, SubscribeMethod subscribeMethod) {
        this.tokenService = tokenService;
        this.currencyService = currencyService;
        this.stockTwService = stockTwService;
        this.cryptoService = cryptoService;
        this.userService = userService;
        this.propertyService = propertyService;
        this.cryptoWebSocketHandler = cryptoWebSocketHandler;
        this.subscribeMethod = subscribeMethod;
    }

    private List<String> trackableStocks = new ArrayList<>();
    Logger logger = LoggerFactory.getLogger(CrontabMethod.class);


    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanExpiredTokens(){
        tokenService.removeExpiredTokens();
    }

    @Scheduled(cron = "0 30 */2 * * ?")
    public void updateCurrencyData() {
        currencyService.updateCurrencyData();
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Taipei")
    public void updateStockList() {
        stockTwService.updateStockList();
    }

    @Scheduled(cron = "0 30 8 * * ? ", zone = "Asia/Taipei")
    public void checkSubscriptions() throws JsonProcessingException {
        logger.debug("正在檢查訂閱狀況");
        logger.debug(String.valueOf(trackableStocks));
        Map<String, List<String>> subscriptionValidity = stockTwService.checkSubscriptionValidity();
        if (subscriptionValidity != null) {
            logger.debug("成功獲取列表，加入到處理欄中");
            trackableStocks = subscriptionValidity.get("inquiry");
        } else {
            logger.debug("無法獲取列表，重新獲取");
            trackableStocks = stockTwService.checkSubscriptionValidity().get("inquiry");
            if (trackableStocks != null && !trackableStocks.isEmpty()) {
                logger.debug("成功獲取列表，加入到處理欄中");
                stockTwService.trackStockNowPrices(trackableStocks);
            } else {
                logger.warn("沒有可以訂閱的股票");
            }
        }
    }

    @Scheduled(cron = "*/5 * 9-13 * * MON-FRI ", zone = "Asia/Taipei")
    public void trackPricesPeriodically() throws JsonProcessingException {
        if (!trackableStocks.isEmpty()) {
            logger.debug("已經獲取列表");
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Taipei"));
            if (now.isAfter(LocalTime.of(13,30)) && now.getMinute() % 10 == 0) {
                logger.debug("收盤時間:更新速度為10分鐘");
                stockTwService.trackStockNowPrices(trackableStocks);
            } else {
                logger.debug("開盤時間:更新速度為5秒");
                stockTwService.trackStockNowPrices(trackableStocks);
            }
        } else {
            logger.warn("列表為空，嘗試獲取訂閱列表");
            checkSubscriptions();
        }
    }

    @Scheduled(cron = "0 30 16 * * MON-FRI ", zone = "Asia/Taipei")
    public void updateStockHistoryPrices() {
        logger.debug("開始更新股票的每日最新價格");
        stockTwService.trackStockHistoryPricesWithUpdateDaily();
    }

    @Scheduled(cron = "0 30 2 * * ? ", zone = "UTC")
    public void updateCryptoHistoryPrices() {
        logger.debug("開始更新加密貨幣的每日最新價格");
        cryptoService.trackCryptoHistoryPricesWithUpdateDaily();
    }

    @Scheduled(cron = "0 30 */4 * * ? ", zone = "UTC")
    public void checkHistoryData() {
        logger.debug("開始檢查資產的歷史數據");
        subscribeMethod.CheckSubscribedAssets();
    }

    @Scheduled(cron = "0 0 */1 * * ? ", zone = "UTC")
    public void recordUserPropertySummary() {
        logger.debug("開始記錄使用者的資產總價");
        List<User> users = userService.getAllUsers();
        try {
            for (User user : users) {
                List<PropertyListDto.getAllPropertiesDto> getAllPropertiesDtoList = propertyService.getUserAllProperties(user, false);
                List<PropertyListDto.writeToInfluxPropertyDto> toInfluxPropertyDto = propertyService.convertGetAllPropertiesDtoToWriteToInfluxPropertyDto(getAllPropertiesDtoList);
                propertyService.writeAllPropertiesToInflux(toInfluxPropertyDto, user);
            }
            logger.debug("記錄完成");
        } catch (Exception e) {
            logger.error("記錄失敗", e);
        }
    }


    @Scheduled(fixedRate = 60000)
    public void checkAndReconnectWebSocket() {
        if (cryptoService.isNeedToCheckConnection() && !cryptoWebSocketHandler.isRunning()) {
            cryptoService.checkAndReconnectWebSocket();
        }
    }

}
