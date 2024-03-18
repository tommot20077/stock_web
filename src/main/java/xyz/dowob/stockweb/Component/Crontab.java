package xyz.dowob.stockweb.Component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Service.CurrencyService;
import xyz.dowob.stockweb.Service.TokenService;

@Component
public class Crontab {
    private final TokenService tokenService;
    private final CurrencyService currencyService;
    @Autowired
    public Crontab(TokenService tokenService, CurrencyService currencyService) {
        this.tokenService = tokenService;
        this.currencyService = currencyService;
    }


    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanExpiredTokens(){
        tokenService.removeExpiredTokens();
    }

    @Scheduled(cron = "0 30 */2 * * ?")
    public void updateCurrencyData() {
        currencyService.updateCurrencyData();
    }

}
