package xyz.dowob.stockweb.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.TokenService;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Crontab {
    private final TokenService tokenService;
    private final CurrencyService currencyService;
    private final StockTwService stockTwService;
    private final CryptoService cryptoService;
    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    @Autowired
    public Crontab(TokenService tokenService, CurrencyService currencyService, StockTwService stockTwService, CryptoService cryptoService, CryptoWebSocketHandler cryptoWebSocketHandler) {
        this.tokenService = tokenService;
        this.currencyService = currencyService;
        this.stockTwService = stockTwService;
        this.cryptoService = cryptoService;
        this.cryptoWebSocketHandler = cryptoWebSocketHandler;
    }

    private List<String> trackableStocks = new ArrayList<>();
    Logger logger = LoggerFactory.getLogger(Crontab.class);


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
        Map<String, List<String>> subscriptionValidity = stockTwService.CheckSubscriptionValidity();
        if (subscriptionValidity != null) {
            logger.debug("已經獲取列表");
            trackableStocks = subscriptionValidity.get("inquiry");
        } else {
            logger.debug("無法獲取列表，重新獲取");
            trackableStocks = stockTwService.CheckSubscriptionValidity().get("inquiry");
            if (trackableStocks != null && !trackableStocks.isEmpty()) {
                stockTwService.trackStockPrices(trackableStocks);
            } else {
                logger.warn("沒有可以訂閱的股票");

            }
        }

    }

    @Scheduled(cron = "*/5 * 9-13 * * ? ", zone = "Asia/Taipei")
    public void trackPricesPeriodically() throws JsonProcessingException {
        if (!trackableStocks.isEmpty()) {
            logger.debug("已經獲取列表");
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Taipei"));
            if (now.isAfter(LocalTime.of(13,30)) && now.getMinute() % 10 == 0) {
                logger.debug("收盤時間:更新速度為10分鐘");
                stockTwService.trackStockPrices(trackableStocks);
            } else {
                logger.debug("開盤時間:更新速度為5秒");
                stockTwService.trackStockPrices(trackableStocks);
            }
        } else {
            checkSubscriptions();
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndReconnectWebSocket() {
        if (cryptoService.isNeedToCheckConnection() && !cryptoWebSocketHandler.isRunning()) {
            cryptoService.checkAndReconnectWebSocket();
        }
    }

}
