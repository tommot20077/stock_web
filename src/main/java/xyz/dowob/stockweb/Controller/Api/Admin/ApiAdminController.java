package xyz.dowob.stockweb.Controller.Api.Admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;

@Controller
@RequestMapping("/api/admin")
public class ApiAdminController {

    private final CurrencyService currencyService;
    @Autowired
    public ApiAdminController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }
    @PostMapping("/updateCurrencyData")
    public ResponseEntity<?> updateCurrencyData() {
        try {
            currencyService.updateCurrencyData();
            return ResponseEntity.ok().body("匯率更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
