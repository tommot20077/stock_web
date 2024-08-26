package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Map<Long, Map<User, String>> KLINE_SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private static final Map<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();


    @Autowired
    private RedisService redisService;

    @Autowired
    private AssetService assetService;

    @Scheduled(cron = "0 */1 * * * *")
    public void updateCurrentKlineData() {
        log.info("開始更新current Kline資料");
        for (Map.Entry<Long, Map<User, String>> entry : KLINE_SUBSCRIPTIONS.entrySet()) {
            Long assetId = entry.getKey();
            Set<String> contactSessions = new HashSet<>(entry.getValue().values());
            CompletableFuture.runAsync(() -> {
                handleKlineData(assetId, "current", contactSessions);
                sendData(assetId, "current", contactSessions);
            });
        }
    }

    @Scheduled(cron = "0 0 */24 * * *")
    public void updateHistoryKlineData() {
        log.info("開始更新history Kline資料");
        for (Map.Entry<Long, Map<User, String>> entry : KLINE_SUBSCRIPTIONS.entrySet()) {
            Long assetId = entry.getKey();
            Set<String> contactSessions = new HashSet<>(entry.getValue().values());
            CompletableFuture.runAsync(() -> {
                handleKlineData(assetId, "history", contactSessions);
                sendData(assetId, "history", contactSessions);
            });
        }
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
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws IOException {
        log.info("WebSocket連線關閉: " + session.getId() + " ,用戶: " + USER_MAP.get(session.getId()).getEmail());
        User user = USER_MAP.get(session.getId());
        Long assetId = CONNECTIONS.get(user)
                                  .entrySet()
                                  .stream()
                                  .filter(entry -> entry.getValue().equals(session.getId()))
                                  .map(Map.Entry::getKey)
                                  .findFirst()
                                  .orElse(null);
        if (assetId != null) {
            CONNECTIONS.get(user).remove(assetId);
            KLINE_SUBSCRIPTIONS.get(assetId).remove(user);
        }
        USER_MAP.remove(session.getId());
        SESSION_MAP.remove(session.getId()).close();
        log.info("目前WebSocket連線數量: " + getTotalActiveSessions(CONNECTIONS));
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
        USER_MAP.put(session.getId(), user);
        KLINE_SUBSCRIPTIONS.computeIfAbsent(assetId, k -> new ConcurrentHashMap<>()).put(user, session.getId());
        log.info("目前用戶: " + user.getEmail() + " 的訂閱數量: " + CONNECTIONS.get(user).size());

        sendData(assetId, "current", session.getId());
        sendData(assetId, "history", session.getId());
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket連線出現錯誤: " + session.getId() + " ,用戶: " + session.getAttributes().get("userId"));
        log.error("錯誤訊息: " + exception.getMessage());
        super.handleTransportError(session, exception);
    }

    private void handleKlineData(Long assetId, String type, Set<String> contactSessions) {
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("kline_%s", hashInnerKey);
        try {
            Asset asset = assetService.getAssetById(assetId);
            if ((asset instanceof CryptoTradingPair cryptoTradingPair && !cryptoTradingPair.isHasAnySubscribed()) || (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed())) {
                sendMessage(contactSessions, "此資產尚未有任何訂閱，請先訂閱後再做請求");
            }

            List<String> dataList = redisService.getCacheListValueFromKey(listKey + "data");
            if ("processing".equals(redisService.getHashValueFromKey("kline", hashInnerKey + "status"))) {
                sendMessage(contactSessions, "資產資料已經在處理中");
            }
            if (!dataList.isEmpty()) {
                String lastTimestamp = redisService.getHashValueFromKey("kline", hashInnerKey + "last_timestamp");
                Instant lastInstant = Instant.parse(lastTimestamp);
                Instant offsetInstant = lastInstant.plus(Duration.ofMillis(1));
                String offsetTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                                                          .format(offsetInstant.atZone(ZoneOffset.UTC));
                assetService.getAssetHistoryInfo(asset, type, offsetTimestamp);
            } else {
                assetService.getAssetHistoryInfo(asset, type, null);
            }
        } catch (Exception e) {
            log.error("處理資產價格圖資料失敗: " + e.getMessage());
            sendMessage(contactSessions, "請求處理資料失敗" + e.getMessage());
        }
    }



    private void sendData(Long assetId, String type, String sessionId) {
        sendData(assetId, type, Set.of(sessionId));
    }

    private void sendData(Long assetId, String type, Set<String> sessionIdSet) {
        Map<String, String> klineData = getKlineData(assetId.toString(), type, sessionIdSet);
        if (klineData != null) {
            for (Map.Entry<String, String> entry : klineData.entrySet()) {
                sendMessage(Set.of(entry.getKey()), entry.getValue());
            }
        }
        handleKlineData(assetId, type, sessionIdSet);
    }

    private Map<String, String> getKlineData(String assetId, String type, Set<String> contactSessions) {
        ObjectMapper objectMapper = new ObjectMapper();
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("kline_%s", hashInnerKey);
        try {
            String status = redisService.getHashValueFromKey("kline", hashInnerKey + "status");
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
        for (String sessionId : contactSessions) {
            try {
                SESSION_MAP.get(sessionId).sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("發送消息失敗: " + e.getMessage());
            }
        }
    }
}
