package xyz.dowob.stockweb.Controller.Api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Model.Currency;
import xyz.dowob.stockweb.Model.CurrencyHistory;
import xyz.dowob.stockweb.Service.CurrencyService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/user/currency")
public class ApiCurrencyController {

    private final CurrencyService currencyService;
    @Autowired
    public ApiCurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }
    @GetMapping("/getCurrencyExchangeRates")
    public ResponseEntity<Map<String, BigDecimal>> getCurrencyExchangeRates(@RequestBody List<String> currencyCodes) {
        Map<String, BigDecimal> exchangeRates = currencyService.getExchangeRates(currencyCodes);
        return ResponseEntity.ok().body(exchangeRates);
    }

    @GetMapping("/getAllCurrency")
    public ResponseEntity<?> getAllCurrency() {
        List<String> currencyList = currencyService.getCurrencyList();
        return ResponseEntity.ok().body(currencyList);
    }

    @GetMapping("/convertCurrency")
    public ResponseEntity<?> convertCurrency(@RequestParam String from, @RequestParam String to, @RequestParam(defaultValue = "1") String amount) {
        BigDecimal result = currencyService.convertCurrency(from, to, amount);
        return ResponseEntity.ok().body(result);
    }

    @GetMapping("/getCurrencyHistory")
    public ResponseEntity<?> getCurrencyHistory(@RequestParam String currency) {
        List<CurrencyHistory> currencyHistoryList = currencyService.getCurrencyHistory(currency);
        return ResponseEntity.ok().body(currencyHistoryList);

    }

}
