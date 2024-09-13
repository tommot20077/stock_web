package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.Beta;
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
@Beta
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Map<String, WebSocketSession> CONNECTIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        CONNECTIONS.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws IOException {
        WebSocketSession webSocketSession = Objects.requireNonNull(CONNECTIONS.remove(session.getId()));
        webSocketSession.close();
    }

    @Override
    public boolean supportsPartialMessages() {
        return super.supportsPartialMessages();
    }

    private void sendMessage(String clientUsername, String message) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, String> data = new HashMap<>();
        for (String client : CONNECTIONS.keySet()) {
            WebSocketSession session = CONNECTIONS.get(client);
            if (session.isOpen()) {
                data.put("sender", clientUsername);
                data.put("message", message);
                String json = objectMapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        this.sendMessage(session.getAttributes().get("username").toString(), message.getPayload());
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {
        super.handleTransportError(session, exception);
    }
}
