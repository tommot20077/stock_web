package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.dowob.stockweb.Dto.Subscription.SubscriptionCryptoDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/user/crypto")
public class ApiCryptoController {

    private final CryptoService cryptoService;
    private final UserService userService;
    @Autowired
    public ApiCryptoController(CryptoService cryptoService, UserService userService) {
        this.cryptoService = cryptoService;
        this.userService = userService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeSymbol(@RequestBody SubscriptionCryptoDto request, HttpSession session) {
        try {
            if (session.getAttribute("currentUserId") == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            List<SubscriptionCryptoDto.Subscription> subscriptions = request.getSubscriptions();
            Long userId = (Long) session.getAttribute("currentUserId");
            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.badRequest().body("使用者不存在");
            }

            Map<String, String> failedSubscribes = new HashMap<>();


            if (subscriptions.isEmpty()) {
                return ResponseEntity.badRequest().body("請選擇要訂閱的加密貨幣和通知通道");
            } else {
                for (SubscriptionCryptoDto.Subscription subscription : subscriptions) {
                    String tradingPair = subscription.getTradingPair().toUpperCase();
                    String channel = subscription.getChannel().toLowerCase();
                    try {
                        cryptoService.subscribeTradingPair(tradingPair, channel, user);
                    } catch (Exception e) {
                        failedSubscribes.put(tradingPair + channel, e.getMessage());
                    }
                }
            }
            if (failedSubscribes.isEmpty()) {
                return ResponseEntity.ok().body("訂閱成功");
            } else {
                return ResponseEntity.badRequest().body("部分訂閱失敗" + failedSubscribes);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("訂閱加密貨幣失敗: " + e.getMessage());
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribeSymbol(@RequestBody SubscriptionCryptoDto request, HttpSession session) {
        try {
            if (session.getAttribute("currentUserId") == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            List<SubscriptionCryptoDto.Subscription> Subscriptions = request.getSubscriptions();
            Long userId = (Long) session.getAttribute("currentUserId");
            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.badRequest().body("使用者不存在");
            }

            Map<String, String> failedSubscribes = new HashMap<>();

            if (Subscriptions.isEmpty()) {
                return ResponseEntity.badRequest().body("請選擇要取消訂閱的加密貨幣和通知通道");
            } else {
                for (SubscriptionCryptoDto.Subscription subscription : Subscriptions) {
                    String tradingPair = subscription.getTradingPair().toUpperCase();
                    String channel = subscription.getChannel().toLowerCase();
                    try {
                        cryptoService.unsubscribeTradingPair(tradingPair, channel, user);
                    } catch (Exception e) {
                        failedSubscribes.put(tradingPair + channel, e.getMessage());
                    }
                }
            }
            if (failedSubscribes.isEmpty()) {
                return ResponseEntity.ok().body("取消訂閱成功");
            } else {
                return ResponseEntity.badRequest().body("部分取消訂閱失敗" + failedSubscribes);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取消訂閱加密貨幣失敗: " + e.getMessage());
        }
    }

    @PostMapping("/updateCryptoList")//admin
    public ResponseEntity<?> updateCryptoList(HttpServletResponse response) {
        try {
            cryptoService.tooManyRequest(response);
            cryptoService.updateSymbolList();
            return ResponseEntity.ok().body("更新加密貨幣清單成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("更新加密貨幣清單失敗: " + e.getMessage());
        }
    }

    @GetMapping("/getAllTradingPairs")
    public ResponseEntity<?> getAllTradingPairs() {
        try {
            List<String> subscriptions = cryptoService.getAllTradingPairs();
            return ResponseEntity.ok().body(subscriptions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取得所有加密貨幣清單失敗: " + e.getMessage());
        }
    }

    @GetMapping("/getServerTradingPairs")//admin
    public ResponseEntity<?> getServerTradingPairs() {
        try {
            String subscriptions = cryptoService.getServerTradingPairs();
            return ResponseEntity.ok().body(subscriptions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取得伺服器加密貨幣清單失敗: " + e.getMessage());
        }
    }





    @PostMapping("/ws/start")//admin
    public ResponseEntity<?> startWebSocket() {
        try {
            cryptoService.openConnection();
            return ResponseEntity.ok().body("WebSocket已啟動");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("啟動WebSocket失敗: " + e.getMessage());
        }
    }


    @PostMapping("/ws/stop")//admin
    public ResponseEntity<?> stopWebSocket() {
        try {
            cryptoService.closeConnection();
            return ResponseEntity.ok().body("WebSocket已停止");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("停止WebSocket失敗: " + e.getMessage());
        }
    }

    @PostMapping("/ws/status")
    public ResponseEntity<?> webSocketStatus() {
        try {
            if (cryptoService.isConnectionOpen()) {
                return ResponseEntity.ok().body("WebSocket已連線");
            } else {
                return ResponseEntity.ok().body("WebSocket尚未連線");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取得WebSocket狀態失敗: " + e.getMessage());
        }
    }
    @PostMapping("/ws/restart")//admin
    public ResponseEntity<?> restartWebSocket() {
        try {
            cryptoService.closeConnection();
            cryptoService.openConnection();
            return ResponseEntity.ok().body("WebSocket已重啟");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("重啟WebSocket失敗: " + e.getMessage());
        }
    }

}

