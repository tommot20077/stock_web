package xyz.dowob.stockweb.Controller.Api.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.dowob.stockweb.Service.Crypto.WebSocketService;

@Controller
@RequestMapping("/api/user/crypto")
public class ApiCryptoController {

    private final WebSocketService webSocketService;
    @Autowired
    public ApiCryptoController(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @PostMapping("/subscribe/{symbol}")//未來支援多個symbol
    public ResponseEntity<?> subscribeSymbol(@PathVariable String symbol, @RequestParam(required = false, defaultValue = "kline_1m") String channel) {
        try {
            webSocketService.subscribeToSymbol(symbol, channel);
            return ResponseEntity.ok().body("訂閱: " + symbol + "成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("訂閱" + symbol + "失敗: " + e.getMessage());
        }
    }

    @PostMapping("/unsubscribe/{symbol}")
    public ResponseEntity<?> unsubscribeSymbol(@PathVariable String symbol, @RequestParam(required = false, defaultValue = "kline_1m") String channel) {
        try {
            webSocketService.unsubscribeToSymbol(symbol, channel);
            return ResponseEntity.ok().body("取消訂閱" + symbol + "成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取消訂閱" + symbol + "失敗: " + e.getMessage());
        }
    }

    @PostMapping("/ws/start")
    public ResponseEntity<?> startWebSocket() {
        try {
            webSocketService.openConnection();
            return ResponseEntity.ok().body("WebSocket已啟動");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("啟動WebSocket失敗: " + e.getMessage());
        }
    }


    @PostMapping("/ws/stop")
    public ResponseEntity<?> stopWebSocket() {
        try {
            webSocketService.closeConnection();
            return ResponseEntity.ok().body("WebSocket已停止");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("停止WebSocket失敗: " + e.getMessage());
        }
    }

    @PostMapping("/ws/status")
    public ResponseEntity<?> webSocketStatus() {
        if (webSocketService.isConnectionOpen()) {
            return ResponseEntity.ok().body("WebSocket已連線");
        } else {
            return ResponseEntity.ok().body("WebSocket尚未連線");
        }
    }
    @PostMapping("/ws/restart")
    public ResponseEntity<?> restartWebSocket() {
        try {
            webSocketService.closeConnection();
            webSocketService.openConnection();
            return ResponseEntity.ok().body("WebSocket已重啟");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("重啟WebSocket失敗: " + e.getMessage());
        }
    }

}

