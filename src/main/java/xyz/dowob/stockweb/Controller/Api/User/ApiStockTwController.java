package xyz.dowob.stockweb.Controller.Api.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.StockSubscriptionDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.*;

@Controller
@RequestMapping("/api/stock/tw")
public class ApiStockTwController {
    private final StockTwService stockTwService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApiStockTwController(StockTwService stockTwService, UserService userService, ObjectMapper objectMapper) {
        this.stockTwService = stockTwService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> addNewStock(@RequestBody StockSubscriptionDto stockIds, HttpSession session) {
        if(session.getAttribute("currentUserId") == null){
            return ResponseEntity.status(401).body("請先登入");
        }
        Long userId = (Long) session.getAttribute("currentUserId");
        User user = userService.getUserById(userId);

        Map<String, String> failedSubscribes = new HashMap<>();
        for (String stockId : stockIds.getStockId()) {
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
    public ResponseEntity<?> removeStock(@RequestBody StockSubscriptionDto stockIds, HttpSession session) {
        if(session.getAttribute("currentUserId") == null){
            return ResponseEntity.status(401).body("請先登入");
        }
        Long userId = (Long) session.getAttribute("currentUserId");
        User user = userService.getUserById(userId);

        Map<String, String> failedSubscribes = new HashMap<>();
        for (String stockId : stockIds.getStockId()) {
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


}
