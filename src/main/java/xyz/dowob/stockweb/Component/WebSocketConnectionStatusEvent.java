package xyz.dowob.stockweb.Component;

import org.springframework.context.ApplicationEvent;
import org.springframework.web.socket.WebSocketSession;
public class WebSocketConnectionStatusEvent extends ApplicationEvent {
    private final boolean connected;
    private final WebSocketSession session;

    public WebSocketConnectionStatusEvent(Object source, boolean connected, WebSocketSession session) {
        super(source);
        this.connected = connected;
        this.session = session;
    }

    public boolean isConnected() {
        return connected;
    }

    public WebSocketSession getSession() {
        return session;
    }
}