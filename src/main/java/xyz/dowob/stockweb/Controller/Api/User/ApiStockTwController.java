package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.Subscription.SubscriptionStockDto;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/user/stock/tw")
public class ApiStockTwController {
    private final StockTwService stockTwService;
    private final UserService userService;


    @Autowired
    public ApiStockTwController(StockTwService stockTwService, UserService userService) {
        this.stockTwService = stockTwService;
        this.userService = userService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> addNewStock(@RequestBody SubscriptionStockDto subscriptionStockDto, HttpSession session) {
        if(session.getAttribute("currentUserId") == null){
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
    public ResponseEntity<?> removeStock(@RequestBody SubscriptionStockDto subscriptionStockDto, HttpSession session) {
        if(session.getAttribute("currentUserId") == null){
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

    @PostMapping("/updateStockList")
    public ResponseEntity<?> updateStockData() {
        try {
            stockTwService.updateStockList();
            return ResponseEntity.ok().body("股票列表更新成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
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

    @GetMapping("/getSubscriptionCurrentPrice")
    public ResponseEntity<?> getSubscriptionStocksCurrentPrice() {
        try {
            Map<String, List<String>> result = stockTwService.checkSubscriptionValidity();
            stockTwService.trackStockNowPrices(result.get("inquiry"));
            result.remove("inquiry");
            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }

    }

    @GetMapping("/getSpecificStocksHistoryPriceByStockCode")
    public ResponseEntity<?> getSpecificStocksHistoryPriceByStockCode(@RequestParam String stockCode) {
        try {
            StockTw stockTw = stockTwService.getStockTwByStockCode(stockCode);
            stockTwService.trackStockTwHistoryPrices(stockTw);
            return ResponseEntity.ok().body("ok");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }



}
