package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.dowob.stockweb.Component.Event.Crypto.WebSocketConnectionStatusEvent;
import xyz.dowob.stockweb.Component.Method.Kafka.KafkaProducerMethod;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Exception.SubscriptionExceptions;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoInfluxService;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static xyz.dowob.stockweb.Exception.AssetExceptions.ErrorEnum.CRYPTO_NOT_FOUND;

/**
 * 當WebSocket連線建立後，此類別將被調用。
 * 實現TextWebSocketHandler接口。
 * 此類別用於處理WebSocket連接，並將數據寫入InfluxDB。
 * 此類別包含訂閱和取消訂閱特定交易對的方法。
 *
 * @author yuan
 */
@NoArgsConstructor(force = true)
@Component
public class CryptoWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private CryptoInfluxService cryptoInfluxService;

    @Autowired
    private SubscribeMethod subscribeMethod;

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private SubscribeRepository subscribeRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private Optional<KafkaProducerMethod> kafkaProducerMethod;

    @Autowired
    private RetryTemplate retryTemplate;

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * -- SETTER --
     * 設定ThreadPoolTaskScheduler。
     */
    @Setter
    private ThreadPoolTaskScheduler taskScheduler;

    private WebSocketSession webSocketSession;

    int connectMaxLiftTime = 24 * 60 * 60;

    Date connectionTime;

    @Getter
    boolean isRunning = false;

    /**
     * 當WebSocket連線建立後，此方法會被調用。它將設定WebSocket會話，並開始重新連接的計劃。
     *
     * @param session WebSocket會話
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws IOException {
        this.webSocketSession = session;
        this.connectionTime = new Date();
        isRunning = true;
        scheduleReconnection();
        eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, true, session));
        subscribeAllPreviousTradingPair();
    }

    /**
     * 當WebSocket連線關閉後，此方法會被調用。它將關閉計劃，釋放資源，並重新連接WebSocket。
     *
     * @param session WebSocket會話
     * @param status  關閉狀態
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws IOException {
        eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, false, null));
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        } finally {
            releaseResources();
            isRunning = false;
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduleReconnection();
        }
    }

    /**
     * 這個方法用於安排重新連接的任務。
     * 如果計劃器已經關閉或終止，它將重新初始化。
     */
    private void scheduleReconnection() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        scheduler.scheduleAtFixedRate(() -> {
            long lifeTime = (new Date().getTime() - connectionTime.getTime()) / 1000;
            if (lifeTime > connectMaxLiftTime) {
                reconnectAndResubscribe();
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
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws IOException {
        try {
            retryTemplate.doWithRetry(this::reconnectAndResubscribe);
        } catch (RetryException e) {
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
    @SuppressWarnings("unchecked")
    public void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws JsonProcessingException {
        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(message.getPayload(), typeRef);
        if (jsonMap.containsKey("data")) {
            Map<String, Object> dataMap = (Map<String, Object>) jsonMap.get("data");
            if (dataMap != null && dataMap.containsKey("k")) {
                Map<String, Object> kline = (Map<String, Object>) dataMap.get("k");
                if (kline != null) {
                    Map<String, Map<String, String>> formattedKline = formatCryptoDataToKline(kline);
                    if (kafkaProducerMethod != null && kafkaProducerMethod.isPresent()) {
                        kafkaProducerMethod.get().sendMessage("crypto_kline", formattedKline);
                    } else {
                        cryptoInfluxService.writeToInflux(formattedKline);
                    }
                }
            }
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
     * 這個方法用於訂閱特定的交易對，並利用subscribeMethod添加訂閱者和發布事件。
     *
     * @param tradingPair 交易對
     * @param channel     頻道
     * @param user        用戶
     */
    public void subscribeTradingPair(String tradingPair, String channel, User user) {
        CryptoTradingPair cryptoTradingPairSymbol = findTradingPair(tradingPair);
        if (cryptoTradingPairSymbol == null) {
            throw new AssetExceptions(CRYPTO_NOT_FOUND, tradingPair);
        } else {
            if (subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(), cryptoTradingPairSymbol.getId(), channel).isPresent()) {
                throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_ALREADY_EXIST, tradingPair);
            } else {
                if (!cryptoTradingPairSymbol.checkUserIsSubscriber(user)) {
                    Subscribe subscribe = new Subscribe();
                    subscribe.setUser(user);
                    subscribe.setAsset(cryptoTradingPairSymbol);
                    subscribe.setChannel(channel);
                    subscribe.setUserSubscribed(true);
                    subscribe.setRemoveAble(true);
                    subscribeRepository.save(subscribe);
                    subscribeMethod.addSubscriberToCryptoTradingPair(cryptoTradingPairSymbol, user.getId());
                }
            }
        }
    }

    /**
     * 這個方法用於取消訂閱特定的交易對, 並利用subscribeMethod刪除訂閱者與發佈事件。
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
            throw new AssetExceptions(CRYPTO_NOT_FOUND, tradingPair);
        } else {
            Subscribe subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                                       cryptoTradingPairSymbol.getId(),
                                                                                       channel).orElse(null);
            if (subscribe == null) {
                throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_NOT_FOUND, tradingPair);
            } else if (subscribe.isRemoveAble()) {
                subscribeRepository.delete(subscribe);
                if (cryptoTradingPairSymbol.checkUserIsSubscriber(user)) {
                    subscribeMethod.removeSubscriberFromTradingPair(cryptoTradingPairSymbol, user.getId());
                }
                if (cryptoRepository.countCryptoSubscribersNumber(cryptoTradingPairSymbol) == 0 && webSocketSession != null && webSocketSession.isOpen()) {
                    String message = "{\"method\":\"UNSUBSCRIBE\", \"params\":[" + "\"" + tradingPair.toLowerCase() + channel.toLowerCase() + "\"]" + ", \"id\": null}";
                    webSocketSession.sendMessage(new TextMessage(message));
                    int allSubscribeNumber = cryptoRepository.countAllSubscribeNumber();
                    if (allSubscribeNumber == 0) {
                        webSocketSession.close(CloseStatus.GOING_AWAY);
                        eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, false, null));
                        webSocketSession = null;
                        isRunning = false;
                    }
                }
            } else {
                throw new SubscriptionExceptions(SubscriptionExceptions.ErrorEnum.SUBSCRIPTION_CANNOT_UNSUBSCRIBE,
                                                 String.format("%s@kline_1m", tradingPair),
                                                 user.getUsername());
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
    private CryptoTradingPair findTradingPair(String tradingPair) {
        return cryptoRepository.findByTradingPair(tradingPair).orElse(null);
    }

    /**
     * 這個方法用於重新連接WebSocket並重新訂閱之前的所有交易對。
     * 如果重新連接失敗，將計劃下一次重新連接。
     */
    public void reconnectAndResubscribe() {
        try {
            Instant scheduledTime = Instant.now().plusSeconds(10);
            taskScheduler.schedule(() -> {
                if (!scheduler.isShutdown() && !scheduler.isTerminated()) {
                    scheduler.shutdown();
                    isRunning = false;
                    try {
                        if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                            scheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        scheduler.shutdownNow();
                    }
                }
                if (webSocketSession != null && webSocketSession.isOpen()) {
                    try {
                        webSocketSession.close(CloseStatus.GOING_AWAY);
                        eventPublisher.publishEvent(new WebSocketConnectionStatusEvent(this, false, null));
                        webSocketSession = null;
                        isRunning = false;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(new StandardWebSocketClient(),
                                                                                              this,
                                                                                              "wss://stream.binance.com:9443/stream?streams=");
                connectionManager.setAutoStartup(true);
                connectionManager.start();
                this.connectionTime = new Date();
                this.isRunning = true;
                try {
                    subscribeAllPreviousTradingPair();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, scheduledTime);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 這個方法用於訂閱之前的所有交易對。
     * 如果訂閱失敗，將計劃下一次訂閱。
     */
    private void subscribeAllPreviousTradingPair() throws IOException {
        List<String> tradingPairList = findSubscribedTradingPairList();
        if (tradingPairList != null && !tradingPairList.isEmpty()) {
            String message = "{\"method\":\"SUBSCRIBE\", \"params\":" + tradingPairList + ", \"id\": null}";
            webSocketSession.sendMessage(new TextMessage(message));
            isRunning = true;
        }
    }

    /**
     * 這個方法用於查找所有已訂閱的交易對。
     *
     * @return List<String>
     */
    private List<String> findSubscribedTradingPairList() {
        return cryptoRepository.findAllByHasAnySubscribed(true)
                               .stream()
                               .map(tradingPair -> "\"" + tradingPair.getTradingPair().toLowerCase() + "@kline_1m\"")
                               .toList();
    }

    /**
     * 這個方法用於釋放資源。
     * 如果WebSocket連接仍然打開，它將關閉WebSocket連接。
     */
    private void releaseResources() throws IOException {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            webSocketSession.close(CloseStatus.GOING_AWAY);
            webSocketSession = null;
        }
        scheduler.shutdownNow();
        isRunning = false;
    }

    /**
     * 這個方法用於將加密貨幣數據格式化為K線數據。
     * 以虛擬貨幣名稱為key，K線數據為value。
     *
     * @param dataMap 加密貨幣數據
     *
     * @return Map<String, Map < String, String>>
     */
    private Map<String, Map<String, String>> formatCryptoDataToKline(Map<String, Object> dataMap) {
        HashMap<String, String> kline = new HashMap<>();
        HashMap<String, Map<String, String>> result = new HashMap<>();
        kline.put("time", dataMap.get("t").toString());
        kline.put("open", dataMap.get("o").toString());
        kline.put("high", dataMap.get("h").toString());
        kline.put("low", dataMap.get("l").toString());
        kline.put("close", dataMap.get("c").toString());
        kline.put("volume", dataMap.get("v").toString());
        result.put(dataMap.get("s").toString(), kline);
        return result;
    }
}
