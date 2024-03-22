package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import xyz.dowob.stockweb.Component.WebSocketConnectionStatusEvent;
import xyz.dowob.stockweb.Model.Crypto.Crypto;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoInfluxDBService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor(force = true)
@Component
public class CryptoWebSocketHandler extends TextWebSocketHandler {

    private final CryptoInfluxDBService cryptoInfluxDBService;
    private final CryptoRepository cryptoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ThreadPoolTaskScheduler taskScheduler;
    Logger logger = LoggerFactory.getLogger(CryptoWebSocketHandler.class);
    private WebSocketSession webSocketSession;

    @Autowired
    public CryptoWebSocketHandler(CryptoInfluxDBService cryptoInfluxDBService, CryptoRepository cryptoRepository, ApplicationEventPublisher eventPublisher) {
        this.cryptoInfluxDBService = cryptoInfluxDBService;
        this.cryptoRepository = cryptoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setTaskScheduler(ThreadPoolTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }






    private int retryCount = 0;
    int maxRetryCount = 5;
    int retryDelay = 10;//秒
    int connectMaxLiftTime = 24 * 60 * 60; //秒
    Date connectionTime;

    boolean isRunning = false;




    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        if (eventPublisher != null) {
            this.webSocketSession = session;
            logger.info("WebSocket連線成功");
            this.connectionTime = new Date();
            this.retryCount = 0;
            isRunning = true;
            scheduleReconnection();
            eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, true, session));
            subscribeAllPreviousSymbols();
        } else {
            logger.warn("ApplicationEventPublisher未初始化");
            throw new Exception("ApplicationEventPublisher未初始化");
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, false, null));
            logger.info("WebSocket連線關閉: Session " + session.getId());
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    logger.info("WebSocket連線關閉: 已強制關閉");
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                logger.info("WebSocket連線關閉: 已強制關閉");
            } finally {
                releaseResources();
                this.retryCount = 0;
                isRunning = false;
                this.scheduler = Executors.newSingleThreadScheduledExecutor();   // 用于重连的新的任务调度器
                scheduleReconnection();
            }
        } else {
            logger.warn("ApplicationEventPublisher未初始化");
            throw new Exception("ApplicationEventPublisher未初始化");
        }

    }

    private void scheduleReconnection() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }

        scheduler.scheduleAtFixedRate(() -> {
            long lifeTime = (new Date().getTime() - connectionTime.getTime()) / 1000;
            if (lifeTime > connectMaxLiftTime) {
                reconnectAndResubscribe();
            } else {
                logger.info("WebSocket連線正常，目前已存活時間: " + lifeTime + "秒");
            }

        }, 5, 5, TimeUnit.MINUTES);
    }




    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {

        logger.error("WebSocket 傳輸錯誤: ", exception);
        if (retryCount < maxRetryCount && !isRunning) {
            logger.info("嘗試重新連接WebSocket");
            reconnectAndResubscribe();
        } else if (isRunning) {
            logger.info("WebSocket連接已重新建立");
        } else {
            logger.error("WebSocket連接失敗，已達最大重試次數");
            releaseResources();
            isRunning = false;
        }

    }



    @Override
    @Async
    public void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws JsonProcessingException {
        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        logger.debug("收到的消息: " + message.getPayload());
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(message.getPayload(), typeRef);
        logger.debug("轉換的JSON: " + jsonMap);

        try {
            if (jsonMap.containsKey("data")) {
                Map<String, Object> dataMap = (Map<String, Object>) jsonMap.get("data");
                logger.debug("WebSocket收到資料: " + dataMap);
                if (dataMap != null && dataMap.containsKey("k")) {
                    Map<String, Object> kline = (Map<String, Object>) dataMap.get("k");
                    logger.debug("轉換的kline: " + kline);
                    if (kline != null) {
                        logger.debug("開始寫入InfluxDB");
                        if (cryptoInfluxDBService != null) {
                            cryptoInfluxDBService.writeToInflux(kline);
                            logger.debug("寫入InfluxDB成功");
                        } else {
                            logger.debug("InfluxDB服務未初始化");
                        }
                    } else {
                        logger.debug("kline為null");
                    }
                } else {
                    logger.debug("dataMap為null");
                }
            } else if (jsonMap.containsKey("result")) {
                logger.debug("WebSocket收到"+ jsonMap);
                logger.debug("訂閱結果: " + jsonMap.get("result"));
            } else {
                logger.debug("未知的消息: " + jsonMap);
            }
        } catch (Exception e) {
            logger.error("處理ws的消息時發生錯誤", e);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }


    public void subscribeToSymbol(String symbol, String channel) throws Exception {
        Crypto CryptoSymbol = getSymbol(symbol, channel);
        if (CryptoSymbol != null) {
            logger.warn("已訂閱過" + symbol);
        } else {
            if (this.webSocketSession != null) {
                String id = generateRequestId();
                String message = "{\"method\": \"SUBSCRIBE\", \"params\": [\"" + symbol.toLowerCase() + "@" + channel.toLowerCase() +"\"], \"id\":" + null + "}";
                this.webSocketSession.sendMessage(new TextMessage(message));
                logger.info("已訂閱" + symbol);
            }
            Crypto crypto = new Crypto();
            crypto.setSymbol(symbol);
            crypto.setChannel(channel);
            if (cryptoRepository != null) {
                cryptoRepository.save(crypto);
            } else {
                logger.warn("CryptoRepository未初始化");
            }
        }
    }

    public void unsubscribeFromSymbol(String symbol, String channel) throws Exception {
        Crypto CryptoSymbol = getSymbol(symbol, channel);
        if (CryptoSymbol == null) {
            logger.warn("尚未訂閱過" + symbol);
        }else {
            if (this.webSocketSession != null) {
                String id = generateRequestId();

                String message = "{\"method\": \"UNSUBSCRIBE\", \"params\": [\"" + symbol.toLowerCase() + "@" + channel.toLowerCase() +"\"], \"id\":" + null + "}";
                webSocketSession.sendMessage(new TextMessage(message));
                logger.warn("已取消訂閱" + symbol);
            }
            if (cryptoRepository != null) {
                cryptoRepository.delete(CryptoSymbol);
            } else {
                logger.warn("CryptoRepository未初始化");
            }
        }
    }
    /*
    public void tooManyRequest(HttpServletResponse response) throws InterruptedException {
        if (response.getStatus() == 429) {
            logger.warn("WebSocket連線過多");
            String retryAfter = response.getHeaders("Retry-After").toString();
            if (retryAfter != null) {
                logger.warn("請求過於頻繁，請在" + retryAfter + "秒後再試");
                int waitSeconds = Integer.parseInt(retryAfter);
                Thread.sleep(waitSeconds * 1000L);
            }
        }
    }
    */

    public Crypto getSymbol (String symbol, String channel) {
        if (cryptoRepository != null) {
            return cryptoRepository.findBySymbolAndChannel(symbol, channel).orElse(null);
        } else {
            logger.warn("CryptoRepository未初始化");
            return null;
        }
    }

    public Set<Crypto> getSymbolWithChannel(String symbol) {
        if (cryptoRepository != null) {
            return cryptoRepository.findBySymbol(symbol);
        } else {
            logger.warn("CryptoRepository未初始化");
            return null;
        }
    }

    public List<Crypto> getAllSymbolsList() {
        if (cryptoRepository != null) {
            return cryptoRepository.findAll();
        } else {
            logger.warn("CryptoRepository未初始化");
            return null;
        }
    }

    private void reconnectAndResubscribe() {
        try {
            taskScheduler.schedule(() -> {

                if (!scheduler.isShutdown() && !scheduler.isTerminated()) {
                    scheduler.shutdown();
                    logger.info("WebSocket連線已停止");
                    try {
                        if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                            scheduler.shutdownNow();
                            logger.info("WebSocket連線關閉: 已強制關閉");
                        }
                    } catch (InterruptedException e) {
                        scheduler.shutdownNow();
                        logger.info("WebSocket連線關閉: 已強制關閉");
                    }
                }
                if (webSocketSession != null && webSocketSession.isOpen()) {
                    try {
                        webSocketSession.close(CloseStatus.GOING_AWAY);
                        webSocketSession = null;
                        logger.info("WebSocket連線已關閉");
                        isRunning = false;


                    } catch (IOException e) {
                        logger.error("嘗試關閉WebSocket連接時發生錯誤", e);
                    }
                }


                WebSocketConnectionManager connectionManager = new WebSocketConnectionManager((WebSocketClient) taskScheduler, this, "wss://stream.binance.com:9443/stream?streams=");
                connectionManager.setAutoStartup(true);
                connectionManager.start();
                logger.info("WebSocket重新連線成功");
                this.connectionTime = new Date();
                this.retryCount = 0;
                subscribeAllPreviousSymbols();
            }, new Date(System.currentTimeMillis() + retryDelay * 1000L));
        } catch (Exception e) {
            logger.error("嘗試重新連接WebSocket時發生錯誤", e);
            retryCount++;
        }
    }

    private void subscribeAllPreviousSymbols() {
        try {
            logger.info("嘗試重新訂閱");
            List<Crypto> symbolsList = getAllSymbolsList();
            if (!symbolsList.isEmpty()) {
                logger.info("重新訂閱之前的所有記錄");
                List<String> params = symbolsList.stream()
                        .map(symbol -> "\"" + symbol.getSymbol().toLowerCase() + "@" + symbol.getChannel().toLowerCase() + "\"")
                        .toList();
                logger.debug("訂閱參數: " + params);
                String message = "{\"method\":\"SUBSCRIBE\", \"params\":" + params + ", \"id\": null}";
                logger.debug("訂閱訊息: " + message);
                webSocketSession.sendMessage(new TextMessage(message));
                logger.info("重新訂閱成功");
                isRunning = true;
            } else {
                logger.info("沒有之前的訂閱記錄");
            }

        } catch (Exception e) {
            logger.error("重新訂閱時發生錯誤", e);
        }
    }


    private void releaseResources() {
        try {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                webSocketSession.close(CloseStatus.GOING_AWAY);
                webSocketSession = null;
                logger.info("WebSocket連線已關閉");
            }
            scheduler.shutdownNow();
            logger.info("Schedule 已停止");
        } catch (IOException e) {
            logger.error("釋放資源時發生錯誤", e);
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }


}

