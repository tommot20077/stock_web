package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.dowob.stockweb.Component.Event.Crypto.WebSocketConnectionStatusEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoInfluxService;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor(force = true)
@Component
public class CryptoWebSocketHandler extends TextWebSocketHandler {

    private final CryptoInfluxService cryptoInfluxService;

    private final CryptoRepository cryptoRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final SubscribeRepository subscribeRepository;

    Logger logger = LoggerFactory.getLogger(CryptoWebSocketHandler.class);

    int maxRetryCount = 5;

    int retryDelay = 10;

    int connectMaxLiftTime = 24 * 60 * 60;

    Date connectionTime;

    @Getter
    boolean isRunning = false;

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ThreadPoolTaskScheduler taskScheduler;

    private WebSocketSession webSocketSession;

    private int retryCount = 0;

    @Autowired
    public CryptoWebSocketHandler(CryptoInfluxService cryptoInfluxService, CryptoRepository cryptoRepository, ApplicationEventPublisher eventPublisher, SubscribeRepository subscribeRepository) {
        this.cryptoInfluxService = cryptoInfluxService;
        this.cryptoRepository = cryptoRepository;
        this.eventPublisher = eventPublisher;
        this.subscribeRepository = subscribeRepository;
    }

    @Autowired
    public void setTaskScheduler(ThreadPoolTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * 當WebSocket連線建立後，此方法會被調用。它將設定WebSocket會話，並開始重新連接的計劃。
     *
     * @param session WebSocket會話
     *
     * @throws Exception 如果ApplicationEventPublisher未初始化
     */
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
            subscribeAllPreviousTradingPair();
        } else {
            logger.warn("ApplicationEventPublisher未初始化");
            throw new Exception("ApplicationEventPublisher未初始化");
        }
    }

    /**
     * 當WebSocket連線關閉後，此方法會被調用。它將關閉計劃，釋放資源，並重新連接WebSocket。
     *
     * @param session WebSocket會話
     * @param status  關閉狀態
     *
     * @throws Exception 如果ApplicationEventPublisher未初始化
     */
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
                this.scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduleReconnection();
            }
        } else {
            logger.warn("ApplicationEventPublisher未初始化");
            throw new Exception("ApplicationEventPublisher未初始化");
        }

    }

    /**
     * 這個方法用於安排重新連接的任務。
     * 如果計劃器已經關閉或終止，它將重新初始化。
     */

    private void scheduleReconnection() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            logger.info("WebSocket連線重新建立: 已重新初始化");
        }

        scheduler.scheduleAtFixedRate(() -> {
            long lifeTime = (new Date().getTime() - connectionTime.getTime()) / 1000;
            if (lifeTime > connectMaxLiftTime) {
                reconnectAndResubscribe();
            } else {
                logger.info("WebSocket連線正常，目前已存活時間: " + lifeTime + "秒");
            }

        }, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 當WebSocket傳輸錯誤時，此方法會被調用。它將處理錯誤並重新連接WebSocket。
     *
     * @param session   WebSocket會話
     * @param exception 錯誤
     */

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
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


    /**
     * 當WebSocket收到文本消息時，此方法會被調用。它將處理消息並將其寫入InfluxDB。
     *
     * @param session WebSocket會話
     * @param message 文本消息
     *
     * @throws JsonProcessingException 如果處理消息時發生錯誤
     */
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
                        if (cryptoInfluxService != null) {
                            cryptoInfluxService.writeToInflux(kline);
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
                logger.debug("WebSocket收到" + jsonMap);
                logger.debug("訂閱結果: " + jsonMap.get("result"));
            } else {
                logger.warn("未知的消息: " + jsonMap);
            }
        } catch (Exception e) {
            logger.error("處理ws的消息時發生錯誤", e);
        }
    }

    /**
     * 這個方法返回false，表示不支援部分訊息。
     *
     * @return false
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }


    /**
     * 這個方法用於訂閱特定的交易對。
     *
     * @param tradingPair 交易對
     * @param channel     頻道
     * @param user        用戶
     *
     * @throws Exception 如果找不到交易對、用戶已訂閱過、SubscribeRepository未初始化、CryptoRepository未初始化
     */
    public void subscribeTradingPair(String tradingPair, String channel, User user) throws Exception {
        CryptoTradingPair cryptoTradingPairSymbol = findTradingPair(tradingPair);
        if (cryptoTradingPairSymbol == null) {
            logger.warn("沒有找到" + tradingPair + "的交易對");
            throw new Exception("沒有找到" + tradingPair + "的交易對");
        } else {
            if (subscribeRepository != null) {
                if (subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), cryptoTradingPairSymbol.getId(), channel)
                                       .isPresent()) {
                    logger.warn("已訂閱過" + tradingPair + channel + "交易對");
                    throw new Exception("已訂閱過" + tradingPair + channel + "交易對");
                } else {
                    logger.debug("用戶主動訂閱，此訂閱設定可刪除");
                    Subscribe subscribe = new Subscribe();
                    subscribe.setUser(user);
                    subscribe.setAsset(cryptoTradingPairSymbol);
                    subscribe.setChannel(channel);
                    subscribe.setUserSubscribed(true);
                    subscribe.setRemoveAble(true);
                    subscribeRepository.save(subscribe);
                    logger.info("已訂閱" + tradingPair + channel + "交易對");

                    if (cryptoTradingPairSymbol.checkUserIsSubscriber(user)) {
                        logger.warn("已訂閱過" + tradingPair + channel + "交易對，不進行訂閱");
                    } else {
                        cryptoTradingPairSymbol.getSubscribers().add(user.getId());
                        logger.info("已訂閱" + tradingPair + channel + "交易對，進行訂閱");
                    }
                    if (cryptoRepository != null) {
                        cryptoRepository.save(cryptoTradingPairSymbol);
                    } else {
                        logger.warn("CryptoRepository未初始化");
                        throw new Exception("CryptoRepository未初始化");
                    }
                }
            } else {
                logger.warn("SubscribeRepository未初始化");
                throw new Exception("SubscribeRepository未初始化");
            }
        }
    }

    /**
     * 這個方法用於取消訂閱特定的交易對。
     *
     * @param tradingPair 交易對
     * @param channel     頻道
     * @param user        用戶
     *
     * @throws Exception 如果找不到交易對、用戶尚未訂閱過、此訂閱為用戶現在所持有的資產、CryptoRepository未初始化
     */

    public void unsubscribeTradingPair(String tradingPair, String channel, User user) throws Exception {
        CryptoTradingPair cryptoTradingPairSymbol = findTradingPair(tradingPair);
        if (cryptoTradingPairSymbol == null) {
            logger.warn("沒有找到" + tradingPair + "的交易對");
            throw new Exception("沒有找到" + tradingPair + "的交易對");
        } else {
            if (subscribeRepository != null) {
                Subscribe subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                                           cryptoTradingPairSymbol.getId(),
                                                                                           channel).orElse(null);
                if (subscribe == null) {
                    logger.warn("尚未訂閱過" + tradingPair + channel + "交易對");
                    throw new Exception("尚未訂閱過" + tradingPair + channel + "交易對");
                } else if (subscribe.isRemoveAble()) {
                    subscribeRepository.delete(subscribe);
                    if (cryptoTradingPairSymbol.checkUserIsSubscriber(user)) {
                        cryptoTradingPairSymbol.getSubscribers().remove(user.getId());
                        logger.info("已訂閱" + tradingPair + channel + "交易對，進行取消訂閱");
                    } else {
                        logger.info("沒有訂閱" + tradingPair + channel + "交易對，不進行取消訂閱");
                    }
                    logger.info("已取消訂閱" + tradingPair + channel + "交易對");

                    if (cryptoRepository != null && eventPublisher != null) {
                        cryptoRepository.save(cryptoTradingPairSymbol);
                        if (cryptoRepository.countCryptoSubscribersNumber(cryptoTradingPairSymbol) == 0 && webSocketSession != null && webSocketSession.isOpen()) {
                            String message = "{\"method\":\"UNSUBSCRIBE\", \"params\":[" + "\"" + tradingPair.toLowerCase() + channel.toLowerCase() + "\"]" + ", \"id\": null}";
                            logger.debug("取消訂閱訊息: " + message);
                            webSocketSession.sendMessage(new TextMessage(message));
                            logger.info("已取消訂閱" + tradingPair + channel + "交易對");
                            int allSubscribeNumber = cryptoRepository.countAllSubscribeNumber();
                            if (allSubscribeNumber == 0) {
                                logger.warn("目前沒有交易對的訂閱");
                                webSocketSession.close(CloseStatus.GOING_AWAY);
                                eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, false, null));
                                webSocketSession = null;
                                logger.info("WebSocket連線已關閉");
                                isRunning = false;
                            }
                        }
                    } else {
                        logger.warn("CryptoRepository未初始化");
                        throw new Exception("CryptoRepository未初始化");
                    }
                } else {
                    logger.warn("此訂閱: " + tradingPair + "@kline_1m 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
                    throw new Exception("此資產: " + tradingPair + "@kline_1m 為用戶: " + user.getUsername() + "現在所持有的資產，不可刪除訂閱");
                }
            }
        }
    }

    /**
     * 這個方法用於查找特定的交易對。
     *
     * @param tradingPair 交易對
     *
     * @return CryptoTradingPair
     */

    public CryptoTradingPair findTradingPair(String tradingPair) {
        if (cryptoRepository != null) {
            return cryptoRepository.findByTradingPair(tradingPair).orElse(null);
        } else {
            logger.warn("CryptoRepository未初始化");
            return null;
        }
    }

    /**
     * 這個方法用於重新連接WebSocket並重新訂閱之前的所有交易對。
     * 如果重新連接失敗，將計劃下一次重新連接。
     */
    public void reconnectAndResubscribe() {
        try {
            Instant scheduledTime = Instant.now().plusSeconds(retryDelay);
            taskScheduler.schedule(() -> {
                if (!scheduler.isShutdown() && !scheduler.isTerminated()) {
                    scheduler.shutdown();
                    isRunning = false;
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
                if (webSocketSession != null && webSocketSession.isOpen() && eventPublisher != null) {
                    try {
                        webSocketSession.close(CloseStatus.GOING_AWAY);
                        eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, false, null));
                        webSocketSession = null;
                        logger.info("WebSocket連線已關閉");
                        isRunning = false;
                    } catch (IOException e) {
                        logger.error("嘗試關閉WebSocket連接時發生錯誤", e);
                    }
                }


                WebSocketClient webSocketClient = new StandardWebSocketClient();
                WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(webSocketClient,
                                                                                              this,
                                                                                              "wss://stream.binance.com:9443/stream?streams=");
                connectionManager.setAutoStartup(true);
                connectionManager.start();
                logger.info("WebSocket重新連線成功");
                this.connectionTime = new Date();
                this.retryCount = 0;
                this.isRunning = true;
                subscribeAllPreviousTradingPair();
            }, scheduledTime);
        } catch (Exception e) {
            logger.error("嘗試重新連接WebSocket時發生錯誤", e);
            retryCount++;
        }
    }

    /**
     * 這個方法用於訂閱之前的所有交易對。
     * 如果訂閱失敗，將計劃下一次訂閱。
     */
    private void subscribeAllPreviousTradingPair() {
        try {
            logger.info("嘗試重新訂閱");
            List<String> tradingPairList = findSubscribedTradingPairList();
            if (tradingPairList == null || tradingPairList.isEmpty()) {
                logger.info("沒有訂閱記錄");
            } else {
                logger.info("重新訂閱之前的所有記錄");
                logger.debug("訂閱參數: " + tradingPairList);
                String message = "{\"method\":\"SUBSCRIBE\", \"params\":" + tradingPairList + ", \"id\": null}";
                logger.debug("訂閱訊息: " + message);
                webSocketSession.sendMessage(new TextMessage(message));
                logger.info("重新訂閱成功");
                isRunning = true;
            }
        } catch (Exception e) {
            logger.error("重新訂閱時發生錯誤", e);
        }
    }

    /**
     * 這個方法用於查找所有已訂閱的交易對。
     * @return List<String>
     */
    private List<String> findSubscribedTradingPairList() {
        if (cryptoRepository != null) {
            return cryptoRepository.findAllByHadSubscribed()
                                   .stream()
                                   .map(tradingPair -> "\"" + tradingPair.getTradingPair().toLowerCase() + "@kline_1m\"")
                                   .toList();
        } else {
            logger.warn("CryptoRepository未初始化");
            return null;
        }
    }

    /**
     * 這個方法用於釋放資源。
     * 如果WebSocket連接仍然打開，它將關閉WebSocket連接。
     */

    private void releaseResources() {
        try {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                webSocketSession.close(CloseStatus.GOING_AWAY);
                webSocketSession = null;
                logger.info("WebSocket連線已關閉");
            }
            scheduler.shutdownNow();
            isRunning = false;
            logger.info("Schedule 已停止");
        } catch (IOException e) {
            logger.error("釋放資源時發生錯誤", e);
        }
    }
}

