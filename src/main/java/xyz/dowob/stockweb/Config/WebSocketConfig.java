package xyz.dowob.stockweb.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoInfluxService;

/**
 * @author yuan
 */
@Configuration
public class WebSocketConfig {
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
     * @param cryptoInfluxService CryptoInfluxService
     * @param cryptoRepository    CryptoRepository
     * @param eventPublisher      ApplicationEventPublisher
     * @param subscribeRepository SubscribeRepository
     *
     * @return CryptoWebSocketHandler
     */
    @Bean
    public CryptoWebSocketHandler cryptoWebSocketHandler(
            CryptoInfluxService cryptoInfluxService, SubscribeMethod subscribeMethod, CryptoRepository cryptoRepository, ApplicationEventPublisher eventPublisher, SubscribeRepository subscribeRepository) {
        return new CryptoWebSocketHandler(cryptoInfluxService, subscribeMethod, cryptoRepository, eventPublisher, subscribeRepository);
    }
}
