package xyz.dowob.stockweb.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Component.Handler.ImmediateDataStatusHandler;
import xyz.dowob.stockweb.Component.Handler.KlineWebSocketHandler;
import xyz.dowob.stockweb.Interceptor.WebSocketHandleInterceptor;

/**
 * 虛擬貨幣WebSocket配置
 *
 * @author yuan
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    /**
     * 初始化WebSocketConfig
     * 創建ThreadPoolTaskScheduler
     */
    public WebSocketConfig() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
    }


    /**
     * 創建WebSocketConnectionManager
     *
     * @param cryptoWebSocketHandler CryptoWebSocketHandler
     *
     * @return WebSocketConnectionManager
     */
    @Bean
    public WebSocketConnectionManager wsConnectionManager(CryptoWebSocketHandler cryptoWebSocketHandler) {
        logger.info("WebSocket連線中");
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        String url = "wss://stream.binance.com:9443/stream?streams=";

        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(webSocketClient, cryptoWebSocketHandler, url);
        connectionManager.setAutoStartup(false);
        logger.info("WebSocket連線成功");
        return connectionManager;
    }

    /**
     * 創建ThreadPoolTaskScheduler
     *
     * @return ThreadPoolTaskScheduler
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 創建CryptoWebSocketHandler
     *
     * @return CryptoWebSocketHandler
     */
    @Bean
    public CryptoWebSocketHandler cryptoWebSocketHandler() {
        return new CryptoWebSocketHandler();
    }

    /**
     * 註冊WebSocket的處理並設定路徑以及跨域設定與攔截器
     * 提供2種WebSocket連接方式，一種是ws，一種是sockjs
     *
     * @param registry WebSocketHandlerRegistry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(klineWebSocketHandler(), "/ws/server/kline")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketHandleInterceptor());
        registry.addHandler(klineWebSocketHandler(), "/sockjs/server/kline")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketHandleInterceptor())
                .withSockJS();


        registry.addHandler(immediateDataHandler(), "/ws/server/immediate")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketHandleInterceptor());
        registry.addHandler(immediateDataHandler(), "/sockjs/server/immediate")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketHandleInterceptor())
                .withSockJS();
    }

    /**
     * 創建KlineWebSocketHandler
     *
     * @return KlineWebSocketHandler
     */
    @Bean
    public KlineWebSocketHandler klineWebSocketHandler() {
        return new KlineWebSocketHandler();
    }

    /**
     * 創建ImmediateDataHandler
     *
     * @return ImmediateDataStatusHandler
     */
    @Bean
    public ImmediateDataStatusHandler immediateDataHandler() {
        return new ImmediateDataStatusHandler();
    }

    /**
     * 創建WebSocketHandleInterceptor
     *
     * @return WebSocketHandleInterceptor
     */
    @Bean
    public WebSocketHandleInterceptor webSocketHandleInterceptor() {
        return new WebSocketHandleInterceptor();
    }
}
