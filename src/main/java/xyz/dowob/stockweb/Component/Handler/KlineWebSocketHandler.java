package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.query.FluxTable;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.RedisService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName KlineWebSocketHandler
 * @description
 * @create 2024-08-24 14:25
 * @Version 1.0
 **/
@Log4j2
@Component
public class KlineWebSocketHandler extends TextWebSocketHandler {
    private static final Map<User, Map<Long, String>> CONNECTIONS = new ConcurrentHashMap<>();

    private static final Map<String, User> USER_MAP = new ConcurrentHashMap<>();

    private static final Map<Long, Set<String>> KLINE_SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private static final Map<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    private static final String CURRENT_TYPE = "current";

    private static final String HISTORY_TYPE = "history";

    private static final String KLINE_PREFIX = "kline";

    private static final String STATUS_SUFFIX = "status";

    private static final String LAST_TIMESTAMP_SUFFIX = "last_timestamp";


    @Autowired
    private RedisService redisService;

    @Autowired
    private AssetService assetService;

    @Scheduled(cron = "0 */1 * * * *")
    public void updateCurrentKlineData() {
        log.info("開始更新current Kline資料");
        // cronUpdateData(CURRENT_TYPE);
    }

    @Scheduled(cron = "0 0 */24 * * *")
    public void updateHistoryKlineData() {
        log.info("開始更新history Kline資料");
        // cronUpdateData(HISTORY_TYPE);
    }


    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        User user = (User) session.getAttributes().get("user");
        log.info("新的WebSocket連線建立: " + session.getId() + " ,用戶: " + user.getEmail());

        CONNECTIONS.computeIfAbsent(user, k -> new ConcurrentHashMap<>());
        USER_MAP.put(session.getId(), user);
        SESSION_MAP.put(session.getId(), session);
        log.info("目前WebSocket連線數量: " + (getTotalActiveSessions(CONNECTIONS) + 1));
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        try {
            User user = USER_MAP.get(session.getId());
            log.info("WebSocket連線關閉: " + session.getId() + " ,用戶: " + user.getEmail());
            Map<Long, String> userConnections = CONNECTIONS.get(user);
            if (userConnections != null) {
                userConnections.entrySet().removeIf(entry -> entry.getValue().equals(session.getId()));
                if (userConnections.isEmpty()) {
                    CONNECTIONS.remove(user);
                }
            }
            KLINE_SUBSCRIPTIONS.values().forEach(sessions -> sessions.remove(session.getId()));
            USER_MAP.remove(session.getId());
            SESSION_MAP.remove(session.getId()).close();
            session.close();
            log.info("目前WebSocket連線數量: " + getTotalActiveSessions(CONNECTIONS));
        } catch (IOException e) {
            log.error("關閉WebSocket連線時發生錯誤: " + e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws IOException {
        Long assetId = Long.valueOf(message.getPayload());
        User user = USER_MAP.get(session.getId());
        log.info("收到來自用戶: " + user.getEmail() + " 的消息: " + assetId);

        if (CONNECTIONS.get(user).containsKey(assetId)) {
            log.info("用戶: " + user.getEmail() + " 已經訂閱過: " + assetId + " 關閉先前訂閱");
            WebSocketSession preAssetSession = SESSION_MAP.get(CONNECTIONS.get(user).get(assetId));
            preAssetSession.sendMessage(new TextMessage("關閉先前重複訂閱"));
            USER_MAP.remove(preAssetSession.getId());
            preAssetSession.close();
            SESSION_MAP.remove(preAssetSession.getId()).close();
        }
        CONNECTIONS.get(user).put(assetId, session.getId());
        KLINE_SUBSCRIPTIONS.computeIfAbsent(assetId, k -> new HashSet<>()).add(session.getId());
        log.info("目前用戶: " + user.getEmail() + " 的訂閱數量: " + CONNECTIONS.get(user).size());

        if (!sendData(assetId, CURRENT_TYPE, session.getId(), null)) {
            CompletableFuture.runAsync(() -> handleInitialData(assetId, session.getId(), CURRENT_TYPE));
        }
        if (!sendData(assetId, HISTORY_TYPE, session.getId(), null)) {
            CompletableFuture.runAsync(() -> handleInitialData(assetId, session.getId(), HISTORY_TYPE));
        }
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket連線出現錯誤: " + session.getId() + " ,用戶: " + session.getAttributes().get("userId"));
        log.error("錯誤訊息: " + exception.getMessage());
        super.handleTransportError(session, exception);
    }

    protected void handleKlineData(Long assetId, String type, Set<String> contactSessions) {
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("%s_%s", KLINE_PREFIX, hashInnerKey);
        try {
            Asset asset = assetService.getAssetById(assetId);
            if ((asset instanceof CryptoTradingPair cryptoTradingPair && !cryptoTradingPair.isHasAnySubscribed()) || (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed())) {
                sendMessage(contactSessions, "此資產尚未有任何訂閱，請先訂閱後再做請求");
                return;
            }

            List<String> dataList = redisService.getCacheListValueFromKey(listKey + "data");
            if ("processing".equals(redisService.getHashValueFromKey(KLINE_PREFIX, hashInnerKey + STATUS_SUFFIX))) {
                sendMessage(contactSessions, "資產資料已經在處理中");
                return;
            }
            Map<String, List<FluxTable>> klineData = new HashMap<>();
            log.debug("dataList: " + dataList);
            if (!dataList.isEmpty()) {
                String lastTimestamp = redisService.getHashValueFromKey("kline", hashInnerKey + LAST_TIMESTAMP_SUFFIX);
                Instant lastInstant = Instant.parse(lastTimestamp);
                Instant offsetInstant = lastInstant.plus(Duration.ofMillis(1));
                String offsetTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                                                          .format(offsetInstant.atZone(ZoneOffset.UTC));
                klineData = assetService.getAssetKlineInfo(asset, type, offsetTimestamp);
            } else {
                klineData = assetService.getAssetKlineInfo(asset, type, null);
            }
            log.debug("klineData: " + klineData);
            if (klineData == null) {
                log.info("目前無新資料");
                return;
            }
            Object mapObject = assetService.formatTableToMap(klineData)[0];
            if (mapObject instanceof Map<?, ?>) {
                log.info("mapObject is a map: " + mapObject);
            }

            // sendData(assetId, type, contactSessions);
        } catch (Exception e) {
            log.error("處理資產價格圖資料失敗: " + e.getMessage());
            sendMessage(contactSessions, "請求處理資料失敗" + e.getMessage());
        }
    }


    private boolean sendData(Long assetId, String type, String sessionId, Map<String, String> appendData) {
        return sendData(assetId, type, Set.of(sessionId), appendData);
    }

    private boolean sendData(Long assetId, String type, Set<String> sessionIdSet, Map<String, String> appendData) {
        Map<String, String> klineData;
        if (appendData != null && !appendData.isEmpty()) {
            klineData = appendData;
        } else {
            klineData = getKlineData(assetId.toString(), type, sessionIdSet);
        }

        if (klineData != null) {
            for (Map.Entry<String, String> entry : klineData.entrySet()) {
                sendMessage(Set.of(entry.getKey()), entry.getValue());
            }
            return true;
        }
        return false;
    }

    private Map<String, String> getKlineData(String assetId, String type, Set<String> contactSessions) {
        ObjectMapper objectMapper = new ObjectMapper();
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("%s_%s", KLINE_PREFIX, hashInnerKey);
        try {
            String status = redisService.getHashValueFromKey(KLINE_PREFIX, hashInnerKey + STATUS_SUFFIX);
            if ("processing".equals(status)) {
                sendMessage(contactSessions, "資產資料處理中，請稍後再試");
                return null;
            } else if (status == null) {
                sendMessage(contactSessions, "沒有請求過資產資料，請過一段時間再試");
                return null;
            } else if ("error".equals(status)) {
                sendMessage(contactSessions, "資產資料處理錯誤，請過一段時間再試");
                return null;
            } else if ("no_data".equals(status)) {
                sendMessage(contactSessions, "無此資產的價格圖");
                return null;
            }
            Map<String, String> result = new HashMap<>();
            log.info("獲取資產價格圖資料: assetId = " + assetId + ", type = " + type);
            Map<String, Object> assetKlineData = assetService.formatRedisAssetKlineCacheToJson(type, listKey, hashInnerKey);
            for (String sessionId : contactSessions) {
                User user = USER_MAP.get(sessionId);
                assetKlineData.put("preferCurrencyExrate", user.getPreferredCurrency().getExchangeRate());
                result.put(sessionId, objectMapper.writeValueAsString(assetKlineData));
            }
            return result;
        } catch (Exception e) {
            log.error("獲取資產價格圖資料失敗: " + e.getMessage());
            return null;
        }
    }

    private <K1, K2, V2> int getTotalActiveSessions(Map<K1, Map<K2, V2>> map) {
        int count = 0;
        for (Map<K2, V2> innerMap : map.values()) {
            count += innerMap.size();
        }
        return count;
    }

    private void sendMessage(Set<String> contactSessions, String message) {
        TextMessage textMessage = new TextMessage(message);
        for (String sessionId : contactSessions) {
            try {
                WebSocketSession session = SESSION_MAP.get(sessionId);
                if (session != null && session.isOpen()) {
                    session.sendMessage(textMessage);
                } else {
                    log.error("發送消息失敗: 用戶: " + USER_MAP.get(sessionId).getEmail() + " 的WebSocket連線已經關閉");
                }
            } catch (IOException e) {
                log.error("發送消息失敗: " + e.getMessage());
            }
        }
    }

    private void cronUpdateData(String currentType) {
        for (Map.Entry<Long, Set<String>> entry : KLINE_SUBSCRIPTIONS.entrySet()) {
            Long assetId = entry.getKey();
            Set<String> contactSessions = entry.getValue();
            CompletableFuture.runAsync(() -> {
                handleKlineData(assetId, currentType, contactSessions);
            });
        }
    }

    private void handleInitialData(Long assetId, String sessionId, String type) {
        log.info("處理初始化歷史數據: assetId = " + assetId + ", sessionId = " + sessionId);
        String hashInnerKey = String.format("%s_%s:", type, assetId);

        Asset asset = assetService.getAssetById(assetId);
        if ((asset instanceof CryptoTradingPair cryptoTradingPair && !cryptoTradingPair.isHasAnySubscribed()) || (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed())) {
            sendMessage(Set.of(sessionId), "此資產尚未有任何訂閱，請先訂閱後再做請求");
            log.info("此資產尚未有任何訂閱，請先訂閱後再做請求");
            return;
        }

        String status = redisService.getHashValueFromKey(KLINE_PREFIX, hashInnerKey + STATUS_SUFFIX);
        if ("processing".equals(status)) {
            log.info("資產資料已經在處理中");
            sendMessage(Set.of(sessionId), "資產資料已經在處理中");
        } else {
            log.info("處理初始化數據: assetId = " + assetId + ", type = " + type);
            log.info("進入處理K線處理");
            assetService.getAssetKlineInfo(asset, type, null);
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String currentStatus = redisService.getHashValueFromKey(KLINE_PREFIX, hashInnerKey + STATUS_SUFFIX);
                log.info("currentStatus: " + currentStatus);
                if (!"processing".equals(currentStatus)) {
                    log.info("處理初始化數據完成: assetId = " + assetId + ", type = " + type);
                    sendData(assetId, type, sessionId, null);
                    scheduler.shutdown();
                }
            } catch (Exception e) {
                log.error("處理初始化數據時發生錯誤: " + e.getMessage());
                sendMessage(Set.of(sessionId), "處理初始化數據時發生錯誤: " + e.getMessage());
                scheduler.shutdown();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
}
