package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;
import xyz.dowob.stockweb.Exception.ServiceExceptions;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.dowob.stockweb.Exception.ServiceExceptions.ErrorEnum.WEBSOCKET_SEND_MESSAGE_ERROR;

/**
 * 用於處理伺服器即時數據WebSocket狀態的處理器。
 * 繼承自TextWebSocketHandler，用於處理文本消息。
 * 實現ApplicationListener接口，用於監聽ImmediateDataUpdateEvent事件。
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName ImmediateDataStatusHandler
 * @description
 * @create 2024-09-04 17:25
 * @Version 1.0
 **/
@Log4j2
@Component
@DependsOn({"crontabMethod", "cryptoService"})
public class ImmediateDataStatusHandler extends TextWebSocketHandler {
    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private CrontabMethod crontabMethod;

    public static final Map<String, Boolean> STATUS = new ConcurrentHashMap<>();

    public static final Map<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String STOCK_TW_STATUS = "isStockTwOpen";

    public static final String CRYPTO_STATUS = "isCryptoOpen";

    /**
     * 初始化方法，用於初始化STATUS。
     * 在初始化時，將加密貨幣和台股的即時數據狀態加入STATUS。
     */
    @PostConstruct
    public void init() {
        try {
            STATUS.put(CRYPTO_STATUS, cryptoService.isConnectionOpen());
            STATUS.put(STOCK_TW_STATUS, crontabMethod.isStockTwAutoStart());
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }

    /**
     * 當WebSocket連接成功時，此方法將被調用。
     * 加入SESSION_MAP中，並發送STATUS給用戶。
     *
     * @param session WebSocketSession對象
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws IOException {
        SESSION_MAP.put(session.getId(), session);
        sendMessage(session);
    }

    /**
     * 關閉WebSocket連接時，此方法將被調用。
     * 從SESSION_MAP中刪除對應的WebSocketSession，並關閉WebSocketSession。
     *
     * @param session WebSocketSession對象
     * @param status  CloseStatus對象
     *
     * @throws IOException 如果關閉異常，則拋出異常
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws IOException {
        WebSocketSession mapSession = SESSION_MAP.get(session.getId());
        mapSession.close();
        session.close();
        SESSION_MAP.remove(session.getId());
    }

    /**
     * 處理傳輸錯誤訊息
     *
     * @param session   WebSocketSession對象
     * @param exception Throwable對象
     *
     * @throws Exception 如果處理錯誤，則拋出異常
     */
    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {
        super.handleTransportError(session, exception);
    }

    /**
     * 用於發送伺服器STATUS給用戶。
     *
     * @param session WebSocketSession對象
     */
    public static void sendMessage(WebSocketSession session) throws RuntimeException {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(STATUS)));
            } else {
                SESSION_MAP.get(session.getId()).close();
                SESSION_MAP.remove(session.getId());
            }
        } catch (IOException e) {
            throw new RuntimeException(new ServiceExceptions(WEBSOCKET_SEND_MESSAGE_ERROR, e));
        }
    }
}
