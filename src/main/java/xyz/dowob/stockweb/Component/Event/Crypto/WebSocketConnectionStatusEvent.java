package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.socket.WebSocketSession;

/**
 * 此類別代表WebSocket連接狀態變更時發布的事件。
 * 此事件包含一個布林值，表示連接狀態，以及一個WebSocketSession對象，表示當前的WebSocket會話。
 *
 * @author yuan
 */
@Getter
public class WebSocketConnectionStatusEvent extends ApplicationEvent {
    private final boolean connected;

    private final WebSocketSession session;

    /**
     * WebSocketConnectionStatusEvent類別的構造函數。
     *
     * @param source    事件最初發生的對象。
     * @param connected 連接狀態。
     * @param session   當前的WebSocket會話。
     */
    public WebSocketConnectionStatusEvent(Object source, boolean connected, WebSocketSession session) {
        super(source);
        this.connected = connected;
        this.session = session;
    }
}