package xyz.dowob.stockweb.Service;

import com.influxdb.client.InfluxDBClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Component.WebSocketConnectionStatusEvent;

@Service
public class WebSocketService {

    private WebSocketSession session;
    private InfluxDBClient influxDBClient;
    private final String webSocketUrl = "wss://stream.binance.com:9443/stream?streams=";

    Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private volatile boolean isRunning = false;

    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    private final WebSocketConnectionManager webSocketConnectionManager;

    @Autowired
    public WebSocketService(CryptoWebSocketHandler webSocketHandler, WebSocketConnectionManager webSocketConnectionManager) {
        this.cryptoWebSocketHandler = webSocketHandler;
        this.webSocketConnectionManager = webSocketConnectionManager;
    }


    @EventListener
    public void handleWebSocketConnectionStatusEvent(WebSocketConnectionStatusEvent event) {
        isRunning = event.isConnected();
        if (isRunning) {
            this.session = event.getSession();
        } else {
            this.session = null;
        }
    }

    public void openConnection() throws Exception {
        if (isRunning) {
            throw new IllegalStateException("已經開啟連線");
        }
        webSocketConnectionManager.start();
    }

    public void closeConnection() throws Exception {
        if (!isRunning) {
            throw new IllegalStateException("目前沒有開啟的連線");
        } else {
            webSocketConnectionManager.stop();
        }
    }

    public boolean isConnectionOpen() {
        return isRunning;
    }

    public void unsubscribeToSymbol(String symbol, String channel) throws Exception {
        if (this.isConnectionOpen()) {
            cryptoWebSocketHandler.unsubscribeFromSymbol(symbol, channel);
        } else {
            throw new IllegalStateException("尚未開啟連線");
        }
    }

    public void subscribeToSymbol(String symbol, String channel) throws Exception {
        if (this.isConnectionOpen()) {
            cryptoWebSocketHandler.subscribeToSymbol(symbol, channel);
        } else {
            throw new IllegalStateException("尚未開啟連線");
        }
    }




}
