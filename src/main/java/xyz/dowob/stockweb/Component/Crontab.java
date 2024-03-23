package xyz.dowob.stockweb.Component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.TokenService;

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

    @Scheduled(fixedRate = 60000)
    public void checkAndReconnectWebSocket() {
        if (cryptoService.isNeedToCheckConnection() && !cryptoWebSocketHandler.isRunning()) {
            cryptoService.checkAndReconnectWebSocket();
        }
    }

}
