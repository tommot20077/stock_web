package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.Subscription.SubscriptionCurrencyDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/user/currency")
public class ApiCurrencyController {

    private final CurrencyService currencyService;
    private final UserService userService;
    @Autowired
    public ApiCurrencyController(CurrencyService currencyService, UserService userService) {
        this.currencyService = currencyService;
        this.userService = userService;
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


    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody SubscriptionCurrencyDto request, HttpSession session) {
        try {
            Long userId = (Long) session.getAttribute("currentUserId");
            if (userId == null) {
                return ResponseEntity.badRequest().body("請先登入");
            }
            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.badRequest().body("使用者不存在");
            }

            List<SubscriptionCurrencyDto.Subscription> subscriptions = request.getSubscriptions();
            Map<String, String> failedSubscribes = new HashMap<>();

            if (subscriptions.isEmpty()) {
                return ResponseEntity.badRequest().body("請選擇要訂閱的貨幣");
            } else {
                for (SubscriptionCurrencyDto.Subscription subscription : subscriptions) {
                    String from = subscription.getFrom().toUpperCase();
                    String to = subscription.getTo().toUpperCase();
                    try {
                        currencyService.subscribeCurrency(from, to, user);
                    } catch (Exception e) {
                        failedSubscribes.put(from + " ⇄ " + to, e.getMessage());
                    }
                }
                if (!failedSubscribes.isEmpty()) {
                    return ResponseEntity.badRequest().body("部分訂閱失敗: "+failedSubscribes);
                }
            }
            return ResponseEntity.ok().body("訂閱成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("訂閱失敗: "+e.getMessage());
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody SubscriptionCurrencyDto request, HttpSession session) {
        try {
            Long userId = (Long) session.getAttribute("currentUserId");
            if (userId == null) {
                return ResponseEntity.badRequest().body("請先登入");
            }
            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.badRequest().body("使用者不存在");
            }

            List<SubscriptionCurrencyDto.Subscription> subscriptions = request.getSubscriptions();
            Map<String, String> failedSubscribes = new HashMap<>();

            if (subscriptions.isEmpty()) {
                return ResponseEntity.badRequest().body("請選擇要取消訂閱的貨幣");
            } else {
                for (SubscriptionCurrencyDto.Subscription subscription : subscriptions) {
                    String from = subscription.getFrom().toUpperCase();
                    String to = subscription.getTo().toUpperCase();
                    try {
                        currencyService.unsubscribeCurrency(from, to, user);
                    } catch (Exception e) {
                        failedSubscribes.put(from + "  ⇄  " + to, e.getMessage());
                    }
                }
                if (!failedSubscribes.isEmpty()) {
                    return ResponseEntity.badRequest().body("部分取消訂閱失敗: "+failedSubscribes);
                }
            }
            return ResponseEntity.ok().body("取消訂閱成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取消訂閱失敗: "+e.getMessage());
        }
    }

}
