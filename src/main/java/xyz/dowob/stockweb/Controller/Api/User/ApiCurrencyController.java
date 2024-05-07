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

/**
 * @author yuan
 */
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

    /**
     * 取得貨幣匯率
     *
     * @param currencyCodes 貨幣代碼清單
     *
     * @return ResponseEntity 包含貨幣匯率
     * Map<String, BigDecimal> exchangeRates string: 貨幣代碼, BigDecimal: 匯率
     */
    @GetMapping("/getCurrencyExchangeRates")
    public ResponseEntity<Map<String, BigDecimal>> getCurrencyExchangeRates(
            @RequestBody List<String> currencyCodes) {
        Map<String, BigDecimal> exchangeRates = currencyService.getExchangeRates(currencyCodes);
        return ResponseEntity.ok().body(exchangeRates);
    }

    /**
     * 取得所有貨幣代碼
     *
     * @return ResponseEntity 包含所有貨幣代碼
     */
    @GetMapping("/getAllCurrency")
    public ResponseEntity<?> getAllCurrency() {
        List<String> currencyList = currencyService.getCurrencyList();
        return ResponseEntity.ok().body(currencyList);
    }

    /**
     * 轉換貨幣
     *
     * @param from   轉換前貨幣代碼
     * @param to     轉換後貨幣代碼
     * @param amount 轉換金額
     *
     * @return ResponseEntity 包含轉換後金額
     */
    @GetMapping("/convertCurrency")
    public ResponseEntity<?> convertCurrency(
            @RequestParam String from, @RequestParam String to, @RequestParam(defaultValue = "1") String amount) {
        BigDecimal result = currencyService.convertCurrency(from, to, amount);
        return ResponseEntity.ok().body(result);
    }

    /**
     * @param request 訂閱請求, 包含要訂閱的貨幣清單SubscriptionCurrencyDto
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @RequestBody SubscriptionCurrencyDto request, HttpSession session) {
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
                    return ResponseEntity.badRequest().body("部分訂閱失敗: " + failedSubscribes);
                }
            }
            return ResponseEntity.ok().body("訂閱成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("訂閱失敗: " + e.getMessage());
        }
    }

    /**
     * 用戶取消訂閱貨幣
     *
     * @param request 訂閱請求, 包含要取消訂閱的貨幣清單SubscriptionCurrencyDto
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(
            @RequestBody SubscriptionCurrencyDto request, HttpSession session) {
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
                    return ResponseEntity.badRequest().body("部分取消訂閱失敗: " + failedSubscribes);
                }
            }
            return ResponseEntity.ok().body("取消訂閱成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取消訂閱失敗: " + e.getMessage());
        }
    }

}
