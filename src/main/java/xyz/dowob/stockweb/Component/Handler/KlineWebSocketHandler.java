package xyz.dowob.stockweb.Component.Handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.influxdb.query.FluxTable;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Dto.Common.KafkaWebsocketDto;
import xyz.dowob.stockweb.Enum.WebsocketAction;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.RedisService;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用於處理K線圖WebSocket狀態的處理器。
 * 繼承自TextWebSocketHandler，用於處理文本消息。
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName KlineWebSocketHandler
 * @description
 * @create 2024-08-24 14:25
 * @Version 1.0
 **/
@Log4j2
public class KlineWebSocketHandler extends TextWebSocketHandler {
    private static final Map<User, Map<Long, String>> CONNECTIONS = new ConcurrentHashMap<>();

    private static final Map<String, User> USER_MAP = new ConcurrentHashMap<>();

    private static final Map<Long, Set<String>> KLINE_SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private static final Map<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> INITIALIZATION_STATUS = new ConcurrentHashMap<>();

    private static final String CURRENT_TYPE = "current";

    private static final String HISTORY_TYPE = "history";

    private static final String KLINE_PREFIX = "kline";

    private static final String STATUS_SUFFIX = "status";

    private static final String LAST_TIMESTAMP_SUFFIX = "last_timestamp";


    @Autowired
    private RedisService redisService;

    @Autowired
    private AssetService assetService;

    @Value("${common.kafka.enable:false}")
    private boolean kafkaEnable;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每分鐘更新一次current Kline資料。
     * 若KLINE_SUBSCRIPTIONS為空，則不執行。
     * 僅在不開啟Kafka時執行。
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void updateCurrentKlineData() {
        if (KLINE_SUBSCRIPTIONS.isEmpty() || kafkaEnable) {
            return;
        }
        log.debug("開始更新current Kline資料");
        updateSubscription(CURRENT_TYPE);
    }

    /**
     * 每24小時更新一次history Kline資料。
     * 若KLINE_SUBSCRIPTIONS為空，則不執行。
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void updateHistoryKlineData() {
        if (KLINE_SUBSCRIPTIONS.isEmpty()) {
            return;
        }
        log.debug("開始更新history Kline資料");
        updateSubscription(HISTORY_TYPE);
    }


    /**
     * 當WebSocket連接成功時，此方法將被調用。
     * 將用戶加入CONNECTIONS中，並將用戶ID加入USER_MAP中，CONNECTIONS中的用戶ID對應的WebSocketSession對象。
     *
     * @param session WebSocketSession對象
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        User user = (User) session.getAttributes().get("user");
        log.debug("新的WebSocket連線建立: {} ,用戶: {}", session.getId(), user.getEmail());

        CONNECTIONS.computeIfAbsent(user, k -> new ConcurrentHashMap<>());
        USER_MAP.put(session.getId(), user);
        SESSION_MAP.put(session.getId(), session);
        INITIALIZATION_STATUS.put(session.getId(), false);
        log.debug("目前WebSocket連線數量: {}", getTotalActiveSessions(CONNECTIONS) + 1);
    }

    /**
     * 關閉WebSocket連接時，此方法將被調用。
     * 從CONNECTIONS中刪除用戶，並從KLINE_SUBSCRIPTIONS中刪除用戶ID。
     * USER_MAP中刪除用戶ID，SESSION_MAP中刪除WebSocketSession對象。
     * 並關閉WebSocketSession。
     *
     * @param session WebSocketSession對象
     * @param status  CloseStatus對象
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        try {
            User user = USER_MAP.get(session.getId());
            log.debug("WebSocket連線關閉: {} ,用戶: {}", session.getId(), user.getEmail());
            Map<Long, String> userConnections = CONNECTIONS.get(user);
            if (userConnections != null) {
                userConnections.entrySet().removeIf(entry -> entry.getValue().equals(session.getId()));
                if (userConnections.isEmpty()) {
                    CONNECTIONS.remove(user);
                }
            }
            KLINE_SUBSCRIPTIONS.values().forEach(sessions -> sessions.remove(session.getId()));
            USER_MAP.remove(session.getId());
            INITIALIZATION_STATUS.remove(session.getId());
            SESSION_MAP.remove(session.getId()).close();
            session.close();
            log.debug("目前WebSocket連線數量: {}", getTotalActiveSessions(CONNECTIONS));
        } catch (IOException e) {
            log.error("關閉WebSocket連線時發生錯誤: {}", e.getMessage());
        }
    }

    /**
     * 當接收到文本消息時，此方法將被調用。
     * 將接收到的消息轉換為Long型，並將其作為資產ID。
     * 從USER_MAP中獲取用戶對象，並從CONNECTIONS中獲取用戶對應的WebSocketSession對象。
     * 如果CONNECTIONS中的用戶已經訂閱過該資產ID，則關閉先前訂閱。
     * 將用戶ID與資產ID存入CONNECTIONS中。
     * 將資產ID與WebSocketSession對象存入KLINE_SUBSCRIPTIONS中。
     * 從資料庫獲取資產的current Kline資料和history Kline資料。
     * 如果資料庫中沒有資料，則開始更新資料，使用CompletableFuture異步處理。
     *
     * @param session WebSocketSession對象
     * @param message TextMessage對象
     *
     * @throws IOException 如果處理消息時發生錯誤，則拋出異常
     */
    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws IOException {
        String payload = message.getPayload();
        long assetId;
        String action;
        log.debug("收到來自用戶: {} 的消息: {}", USER_MAP.get(session.getId()).getEmail(), payload);
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            action = jsonNode.get("action").asText();
            assetId = jsonNode.get("assetId").asLong();
            switch (WebsocketAction.valueOf(action)) {
                case WebsocketAction.chartInitializedDone:
                    INITIALIZATION_STATUS.put(session.getId(), true);
                    break;
                case WebsocketAction.subscribe:
                    handleSubscribe(session, assetId);
                    break;
                default:
                    throw new IllegalStateException("無效的請求: " + payload);
            }
        } catch (NumberFormatException | IllegalStateException e) {
            log.error("無效的請求: {}", payload);
            session.sendMessage(new TextMessage("無效的請求: " + payload));
            session.close();
        }
    }

    private void handleSubscribe(WebSocketSession session, Long assetId) throws IOException {
        User user = USER_MAP.get(session.getId());

        if (CONNECTIONS.get(user).containsKey(assetId)) {
            log.debug("用戶: {} 已經訂閱過: {} 關閉先前訂閱", user.getEmail(), assetId);
            WebSocketSession preAssetSession = SESSION_MAP.get(CONNECTIONS.get(user).get(assetId));
            preAssetSession.sendMessage(new TextMessage("關閉先前重複訂閱"));
            USER_MAP.remove(preAssetSession.getId());
            preAssetSession.close();
            INITIALIZATION_STATUS.remove(preAssetSession.getId());
            SESSION_MAP.remove(preAssetSession.getId()).close();
        }
        CONNECTIONS.get(user).put(assetId, session.getId());
        KLINE_SUBSCRIPTIONS.computeIfAbsent(assetId, k -> new HashSet<>()).add(session.getId());
        log.debug("目前用戶: {} 的訂閱數量: {}", user.getEmail(), CONNECTIONS.get(user).size());

        String validCheck = validAsset(assetId);
        if (!validCheck.isEmpty()) {
            session.sendMessage(new TextMessage(validCheck));
            session.close();
            return;
        }

        CompletableFuture.runAsync(() -> {
            Map<String, Object> currentData = geKlineData(assetId, CURRENT_TYPE);
            Map<String, Object> historyData = geKlineData(assetId, HISTORY_TYPE);
            initialOrSendKlineData(currentData, session, assetId, CURRENT_TYPE);
            initialOrSendKlineData(historyData, session, assetId, HISTORY_TYPE);
        });
    }

    /**
     * 處理傳輸錯誤訊息。
     *
     * @param session   WebSocketSession對象
     * @param exception Throwable對象
     *
     * @throws Exception 如果處理錯誤，則拋出異常
     */
    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket連線出現錯誤: {} ,用戶: {}", session.getId(), session.getAttributes().get("userId"));
        log.error("錯誤訊息: {}", exception.getMessage());
        super.handleTransportError(session, exception);
    }

    /**
     * 從資料庫獲取資產的Kline資料，並轉換成FluxTable對象存入Redis緩存。
     *
     * @param assetId   資產ID
     * @param type      資產類型
     * @param timestamp 時間戳
     *
     * @return 資產的Kline資料
     */
    private Map<String, List<FluxTable>> handleKlineData(Long assetId, String type, String timestamp) {
        log.debug("開始處理資產 type: {} ,timestamp: {}", type, timestamp);
        String status = redisService.getHashValueFromKey(KLINE_PREFIX, String.format("%s_%s:%s", type, assetId, STATUS_SUFFIX));
        if ("progress".equals(status)) {
            log.debug("資產 type: {} ,assetId: {} 正在更新中，取消本次更新", type, assetId);
            return null;
        }
        Map<String, List<FluxTable>> tableMap = assetService.getAssetKlineData(assetId, type, timestamp);
        if (assetService.checkNewDataMethod(assetId, type, tableMap)) {
            String hashInnerKey = String.format("%s_%s:", type, assetId);
            String listKey = String.format("%s_%s", KLINE_PREFIX, hashInnerKey);
            assetService.saveAssetInfoToRedis(tableMap, listKey, hashInnerKey);
        }
        return tableMap;
    }

    /**
     * 從Redis獲取資產的Kline資料。並轉換成K線圖格式。
     *
     * @param assetId 資產ID
     * @param type    資產類型
     *
     * @return 資產的Kline資料
     */
    private Map<String, Object> geKlineData(Long assetId, String type) {
        try {
            String hashInnerKey = String.format("%s_%s:", type, assetId);
            String listKey = String.format("%s_%s", KLINE_PREFIX, hashInnerKey);
            List<String> dataList = redisService.getCacheListValueFromKey(listKey + "data");
            return formatKlineData(dataList, type);
        } catch (Exception e) {
            log.error("轉換格式時發生錯誤時發生錯誤: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 從Kline轉換格式成K線圖格式。
     *
     * @param dataList Redis資料列表字串
     * @param type     資產類型
     *
     * @return K線圖格式資料
     *
     * @throws JsonProcessingException 如果格式化錯誤，則拋出異常
     */
    private Map<String, Object> formatKlineData(List<String> dataList, String type) throws JsonProcessingException {
        ArrayNode dataArray = objectMapper.createArrayNode();
        Map<String, Object> resultMap = new HashMap<>();
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }
        for (String item : dataList) {
            JsonNode jsonNode = objectMapper.readTree(item);
            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    dataArray.add(node);
                }
            } else if (jsonNode.isObject()) {
                dataArray.add(jsonNode);
            }
        }
        resultMap.put("data", dataArray);
        resultMap.put("type", type);
        return resultMap;
    }

    /**
     * 獲取所有活動的連線數量。
     *
     * @param map  連線Map
     * @param <K1> 連線Map的Key1
     * @param <K2> 連線Map的Key2
     * @param <V2> 連線Map的Value2
     *
     * @return 連線數量
     */
    private <K1, K2, V2> int getTotalActiveSessions(Map<K1, Map<K2, V2>> map) {
        int count = 0;
        for (Map<K2, V2> innerMap : map.values()) {
            count += innerMap.size();
        }
        return count;
    }

    /**
     * 初始化以及發送K線圖格式資料。
     *
     * @param dataMap 　K線圖格式資料
     * @param session 　WebSocketSession對象
     * @param assetId 　資產ID
     * @param type    　資產類型
     */
    private void initialOrSendKlineData(Map<String, Object> dataMap, WebSocketSession session, Long assetId, String type) {
        if (dataMap == null) {
            log.debug("沒有緩存資料，開始更新資料");
            updateData(Set.of(session.getId()), assetId, type, null);
        } else {
            log.debug("發送緩存資料");
            User user = USER_MAP.get(session.getId());
            dataMap.put("preferCurrencyExrate", user.getPreferredCurrency().getExchangeRate());
            sendMessage(dataMap, session);
        }
    }

    /**
     * 用於更新後續的新資料
     * 對於每個連線ID，從資料庫獲取新資料，格式化後發送給用戶。
     * 並在過程中加入用戶的偏好幣種匯率，以便用戶在前端顯示轉換後價值。
     *
     * @param sessions      連線ID集合
     * @param assetId       資產ID
     * @param type          資產類型
     * @param lastTimestamp 最後更新時間
     */
    private void updateData(Set<String> sessions, Long assetId, String type, String lastTimestamp) {
        log.debug("開始更新資料 type: {} ,assetId: {} ,lastTimestamp: {}", type, assetId, lastTimestamp);
        CompletableFuture.runAsync(() -> {
            Map<String, List<FluxTable>> newAddData = handleKlineData(assetId, type, lastTimestamp);
            log.debug("新資料: {}", newAddData);
            if (newAddData != null) {
                try {
                    List<String> result = assetService.formatKlineTableByTime(newAddData);
                    List<String> dataList = List.of(result.getFirst());
                    Map<String, Object> data = formatKlineData(dataList, type);
                    if (data != null) {
                        sessions.forEach(sessionId -> {
                            User user = USER_MAP.get(sessionId);
                            if (user == null) {
                                KLINE_SUBSCRIPTIONS.get(assetId).remove(sessionId);
                            } else {
                                data.put("preferCurrencyExrate", user.getPreferredCurrency().getExchangeRate());
                                WebSocketSession session = SESSION_MAP.get(sessionId);
                                sendMessage(data, session);
                            }
                        });
                    } else {
                        throw new RuntimeException("資料格式化錯誤");
                    }
                } catch (Exception e) {
                    log.error("更新資料時發生錯誤: {}", e.getMessage());
                    throw new RuntimeException(e);
                }
            } else {
                log.debug("沒有新資料需要更新");
            }
        });
    }

    /**
     * 發送消息給用戶。
     * 此消息為Object類型，將其轉換為JSON格式。
     *
     * @param object  要發送的消息對象
     * @param session WebSocketSession對象
     * @param <T>     泛型類型
     */
    private <T> void sendMessage(T object, WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(object)));
            } else {
                log.error("發送消息時發生錯誤: 連線已經關閉");
            }
        } catch (IOException e) {
            log.error("發送消息時發生錯誤: {}", e.getMessage());
        }
    }

    /**
     * 驗證資產ID是否有效。
     *
     * @param assetId 資產ID
     *
     * @return 驗證結果
     */
    private String validAsset(Long assetId) {
        try {
            Asset asset = assetService.getAssetById(assetId);
            if ((asset instanceof CryptoTradingPair crypto && !crypto.isHasAnySubscribed()) || (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed())) {
                return "此資產ID尚未訂閱: " + assetId;
            }
            return "";
        } catch (RuntimeException e) {
            log.error("無效的資產ID: {}", assetId);
            return "無效的資產ID:" + assetId;
        }
    }

    /**
     * 定時更新訂閱者的資產資料。
     * 對於每個資產ID，從KLINE_SUBSCRIPTIONS中獲取對應的連線ID集合。
     * 如果連線ID集合為空，則從KLINE_SUBSCRIPTIONS中刪除該資產ID。
     * 從Redis獲取最後更新時間。
     * 更新資料。
     *
     * @param historyType 歷史類型
     */
    private void updateSubscription(String historyType) {
        KLINE_SUBSCRIPTIONS.forEach((assetId, sessions) -> {
            if (sessions.isEmpty()) {
                KLINE_SUBSCRIPTIONS.remove(assetId);
                return;
            }
            String listKey = String.format("%s_%s:", CURRENT_TYPE, assetId);
            String lastTimestamp = redisService.getHashValueFromKey(KLINE_PREFIX, listKey + LAST_TIMESTAMP_SUFFIX);
            log.debug("最後更新時間: {}", lastTimestamp);
            updateData(sessions, assetId, historyType, lastTimestamp);
        });
    }

    public void sendKlineDataByKafka(KafkaWebsocketDto klineData) {
        Long assetId = klineData.getAssetId();
        Set<String> sessions = KLINE_SUBSCRIPTIONS.get(assetId);
        if (sessions == null) {
            return;
        }
        Map<String, Object> result = new HashMap<>();
        Map<String, String> data = new HashMap<>();
        result.put("type", "current");
        result.put("data", new ArrayList<Map<String, String>>());

        AssetKlineDataDto assetData = klineData.getData();

        long timeLong = Long.parseLong(assetData.getTimestamp());
        Instant instant = Instant.ofEpochMilli(timeLong);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
        String timestamp = formatter.format(instant);

        data.put("timestamp", timestamp);
        data.put("open", assetData.getOpen());
        data.put("high", assetData.getHigh());
        data.put("low", assetData.getLow());
        data.put("close", assetData.getClose());
        data.put("volume", assetData.getVolume());
        result.put("data", List.of(data));
        for (String sessionId : sessions) {
            if (INITIALIZATION_STATUS.get(sessionId) == null || !INITIALIZATION_STATUS.get(sessionId)) {
                continue;
            }
            WebSocketSession session = SESSION_MAP.get(sessionId);
            User user = USER_MAP.get(sessionId);
            if (user == null) {
                KLINE_SUBSCRIPTIONS.get(assetId).remove(sessionId);
            } else {
                result.put("preferCurrencyExrate", user.getPreferredCurrency().getExchangeRate());
                sendMessage(result, session);
            }
        }
    }
}
