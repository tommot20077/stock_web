package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.Beta;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName ChatWebSocketHandler
 * @description
 * @create 2024-08-24 11:44
 * @Version 1.0
 **/
@Log4j2
@Beta
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Map<String, WebSocketSession> CONNECTIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        log.info("新的WebSocket連線建立: {} ,用戶: {}", session.getId(), session.getAttributes().get("userId"));
        CONNECTIONS.put(session.getId(), session);
        log.info("目前WebSocket連線數量: {}", CONNECTIONS.size());
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws IOException {
        log.info("WebSocket連線關閉: {} ,用戶: {}", session.getId(), session.getAttributes().get("userId"));
        WebSocketSession webSocketSession = Objects.requireNonNull(CONNECTIONS.remove(session.getId()));
        webSocketSession.close();
        log.info("目前WebSocket連線數量: {}", CONNECTIONS.size());
    }

    @Override
    public boolean supportsPartialMessages() {
        return super.supportsPartialMessages();
    }

    private void sendMessage(String clientUsername, String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, String> data = new HashMap<>();
        for (String client : CONNECTIONS.keySet()) {
            try {
                WebSocketSession session = CONNECTIONS.get(client);
                if (session.isOpen()) {
                    data.put("sender", clientUsername);
                    data.put("message", message);
                    String json = objectMapper.writeValueAsString(data);
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.error("發送消息失敗: {}", e.getMessage());
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("收到消息: {}", message.getPayload());
        this.sendMessage(session.getAttributes().get("username").toString(), message.getPayload());
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket連線出現錯誤: {} ,用戶: {}", session.getId(), session.getAttributes().get("userId"));
        log.error("錯誤訊息: {}", exception.getMessage());
        super.handleTransportError(session, exception);
    }


}
