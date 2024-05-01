package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;
import xyz.dowob.stockweb.Dto.Subscription.SubscriptionStockDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yuan
 */
@Controller
@RequestMapping("/api/user/stock/tw")
public class ApiStockTwController {
    private final StockTwService stockTwService;
    private final UserService userService;
    private final CrontabMethod crontabMethod;


    @Autowired
    public ApiStockTwController(StockTwService stockTwService, UserService userService, CrontabMethod crontabMethod) {
        this.stockTwService = stockTwService;
        this.userService = userService;
        this.crontabMethod = crontabMethod;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> addNewStock(
            @RequestBody SubscriptionStockDto subscriptionStockDto, HttpSession session) {
        if (session.getAttribute("currentUserId") == null) {
            return ResponseEntity.status(401).body("請先登入");
        }
        Long userId = (Long) session.getAttribute("currentUserId");
        User user = userService.getUserById(userId);

        Map<String, String> failedSubscribes = new HashMap<>();
        for (String stockId : subscriptionStockDto.getSubscriptions()) {
            try {
                stockTwService.addStockSubscribeToUser(stockId, user);
            } catch (Exception e) {
                failedSubscribes.put(stockId, e.getMessage());
            }
        }
        if (failedSubscribes.isEmpty()) {
            return ResponseEntity.ok().body("股票訂閱成功");
        } else {
            return ResponseEntity.status(500).body("以下股票訂閱失敗: " + failedSubscribes);
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> removeStock(
            @RequestBody SubscriptionStockDto subscriptionStockDto, HttpSession session) {
        if (session.getAttribute("currentUserId") == null) {
            return ResponseEntity.status(401).body("請先登入");
        }
        Long userId = (Long) session.getAttribute("currentUserId");
        User user = userService.getUserById(userId);

        Map<String, String> failedSubscribes = new HashMap<>();
        for (String stockId : subscriptionStockDto.getSubscriptions()) {
            try {
                stockTwService.removeStockSubscribeToUser(stockId, user);
            } catch (Exception e) {
                failedSubscribes.put(stockId, e.getMessage());
            }
        }
        if (failedSubscribes.isEmpty()) {
            return ResponseEntity.ok().body("股票退訂成功");
        } else {
            return ResponseEntity.status(500).body("以下股票退訂失敗: " + failedSubscribes);
        }
    }

    @GetMapping("/getAllStock")
    public ResponseEntity<?> getAllStockData() {
        try {
            List<Object[]> stockData = stockTwService.getAllStockData();
            Map<String, String> result = new LinkedHashMap<>();
            for (Object[] data : stockData) {
                String stockId = (String) data[0];
                String stockName = (String) data[1];
                result.put(stockName, stockId);
            }
            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/getTrackImmediateStatus")
    public ResponseEntity<?> getTrackImmediateStatus() {
        try {
            return ResponseEntity.ok().body(crontabMethod.isStockTwAutoStart());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
