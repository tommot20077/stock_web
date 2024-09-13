package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
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
 * 這是一個用於處理用戶與台灣股票相關的控制器
 *
 * @author yuan
 */
@Controller
@RequestMapping("/api/user/stock/tw")
public class ApiStockTwController {
    private final StockTwService stockTwService;

    private final UserService userService;

    private final CrontabMethod crontabMethod;

    /**
     * 這是一個構造函數，用於注入StockTwService和UserService
     *
     * @param stockTwService 台灣股票服務
     * @param userService    用戶服務
     * @param crontabMethod  CrontabMethod
     */
    public ApiStockTwController(StockTwService stockTwService, UserService userService, CrontabMethod crontabMethod) {
        this.stockTwService = stockTwService;
        this.userService = userService;
        this.crontabMethod = crontabMethod;
    }

    /**
     * 用戶訂閱台灣股票
     *
     * @param subscriptionStockDto 訂閱請求, 包含要訂閱的股票清單SubscriptionStockDto
     * @param session              用戶session
     *
     * @return ResponseEntity
     */
    @PostMapping("/subscribe")
    public ResponseEntity<?> addNewStock(
            @RequestBody SubscriptionStockDto subscriptionStockDto, HttpSession session) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 用戶取消訂閱台灣股票
     *
     * @param subscriptionStockDto 訂閱請求, 包含要取消訂閱的股票清單SubscriptionStockDto
     * @param session              用戶session
     *
     * @return ResponseEntity
     * 如果所有股票退訂成功, 則回傳200, 並回傳"股票退訂成功"
     * 如果有任何一支股票退訂失敗, 則回傳500, 並列出失敗的股票 Map<String, String> failedSubscribes
     * Map的key為股票代碼, value為失敗原因
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<?> removeStock(
            @RequestBody SubscriptionStockDto subscriptionStockDto, HttpSession session) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 取得所有台灣股票資料
     *
     * @return ResponseEntity
     */
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

    /**
     * 取得台灣股票目前更新狀態
     *
     * @return ResponseEntity
     */
    @GetMapping("/getTrackImmediateStatus")
    public ResponseEntity<?> getTrackImmediateStatus() {
        try {
            return ResponseEntity.ok().body(crontabMethod.isStockTwAutoStart());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
