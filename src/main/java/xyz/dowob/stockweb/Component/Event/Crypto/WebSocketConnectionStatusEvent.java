package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author yuan
 */
@Getter
public class WebSocketConnectionStatusEvent extends ApplicationEvent {
    private final boolean connected;
    private final WebSocketSession session;

    public WebSocketConnectionStatusEvent(Object source, boolean connected, WebSocketSession session) {
        super(source);
        this.connected = connected;
        this.session = session;
    }
}