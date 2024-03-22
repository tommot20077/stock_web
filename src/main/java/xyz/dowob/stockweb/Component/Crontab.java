package xyz.dowob.stockweb.Component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.TokenService;

@Component
public class Crontab {
    private final TokenService tokenService;
    private final CurrencyService currencyService;
    private final StockTwService stockTwService;
    @Autowired
    public Crontab(TokenService tokenService, CurrencyService currencyService, StockTwService stockTwService) {
        this.tokenService = tokenService;
        this.currencyService = currencyService;
        this.stockTwService = stockTwService;
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

}
