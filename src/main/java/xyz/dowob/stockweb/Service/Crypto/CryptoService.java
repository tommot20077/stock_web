package xyz.dowob.stockweb.Service.Crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Component.Event.Crypto.WebSocketConnectionStatusEvent;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CryptoService {

    Logger logger = LoggerFactory.getLogger(CryptoService.class);
    private volatile boolean isRunning = false;
    private boolean isNeedToCheckConnection = false;
    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    private WebSocketConnectionManager connectionManager;
    private final CryptoRepository cryptoRepository;
    private final ObjectMapper objectMapper;



    @Autowired
    public CryptoService(CryptoWebSocketHandler webSocketHandler, WebSocketConnectionManager connectionManager, CryptoRepository cryptoRepository, ObjectMapper objectMapper) {
        this.cryptoWebSocketHandler = webSocketHandler;
        this.connectionManager = connectionManager;
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = objectMapper;
    }


    @EventListener
    public void handleWebSocketConnectionStatusEvent(WebSocketConnectionStatusEvent event) {
        isRunning = event.isConnected();
    }

    public boolean isConnectionOpen() {
        return isRunning;
    }

    public void openConnection() throws IllegalStateException {
        if (isRunning) {
            throw new IllegalStateException("已經開啟連線");
        }
        isNeedToCheckConnection = true;
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        connectionManager = new WebSocketConnectionManager(webSocketClient, cryptoWebSocketHandler, "wss://stream.binance.com:9443/stream?streams=");
        connectionManager.setAutoStartup(true);
        connectionManager.start();
    }

    public void closeConnection() throws IllegalStateException {
        if (!isRunning) {
            throw new IllegalStateException("目前沒有開啟的連線");
        } else {
            isNeedToCheckConnection = false;
            connectionManager.stop();
        }
    }



    public void unsubscribeTradingPair(String symbol, String channel, User user) throws Exception {
        if (!this.isConnectionOpen()) {
            logger.warn("目前沒有啟動連線");
        }
        if (!channel.contains("@")){
            logger.warn("channel格式錯誤");
            throw new IllegalStateException("channel格式錯誤");
        }
        cryptoWebSocketHandler.unsubscribeTradingPair(symbol, channel, user);
    }

    public void subscribeTradingPair(String symbol, String channel, User user) throws Exception {
        if (!this.isConnectionOpen()) {
            logger.warn("目前沒有啟動連線");
        }
        if (!channel.contains("@")){
            logger.warn("channel格式錯誤");
            throw new IllegalStateException("channel格式錯誤");
        }
        cryptoWebSocketHandler.subscribeTradingPair(symbol, channel, user);
    }

    @Async
    public void updateSymbolList() {
        CryptoTradingPair cryptoTradingPair;
        String CryptoListUrl = "https://api.binance.com/api/v3/exchangeInfo";
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(CryptoListUrl, String.class);
        logger.debug("更新幣種交易對列表: " + response.getBody());
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("symbols");
                logger.debug("共有交易對數量: " + dataArray.size());
                if (dataArray.isArray()) {
                    logger.debug("陣列資料: " + dataArray);
                    for (JsonNode cryptoNode : dataArray) {
                        if (cryptoNode.get("status").asText().equals("TRADING")) {
                            cryptoTradingPair = cryptoRepository.findByTradingPair(cryptoNode.get("symbol").asText()).orElse(new CryptoTradingPair());
                            logger.debug("交易對象幣種: " + cryptoNode.get("baseAsset").asText());
                            cryptoTradingPair.setBaseAsset(cryptoNode.get("baseAsset").asText());
                            logger.debug("交易基準幣種: " + cryptoNode.get("quoteAsset").asText());
                            cryptoTradingPair.setQuoteAsset(cryptoNode.get("quoteAsset").asText());
                            logger.debug("更新交易對: " + cryptoNode.get("symbol").asText());
                            cryptoTradingPair.setTradingPair(cryptoNode.get("symbol").asText());
                            cryptoTradingPair.setAssetType(AssetType.CRYPTO);
                            cryptoRepository.save(cryptoTradingPair);
                        }
                    }
                    logger.info("更新幣種交易對列表成功");
                } else {
                    throw new RuntimeException("無法解析幣種列表");
                }
            } catch (Exception e) {
                throw new RuntimeException("更新幣種列表失敗: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("更新幣種列表失敗");
        }
    }

    public void tooManyRequest(HttpServletResponse response) throws InterruptedException {
        if (response.getStatus() == 429) {
            logger.warn("WebSocket連線過多");
            String retryAfter = response.getHeaders("Retry-After").toString();
            if (retryAfter != null) {
                logger.warn("請求過於頻繁，請在" + retryAfter + "秒後再試");
                int waitSeconds = Integer.parseInt(retryAfter);
                Thread.sleep(waitSeconds * 1000L);
                throw new InterruptedException("WebSocket連線過多，請在" + retryAfter + "秒後再試");
            }
        }
    }

    public List<String> getAllTradingPairs() {
        List<CryptoTradingPair> tradingPairs = cryptoRepository.findAll();
        if (tradingPairs.isEmpty()) {
            return null;
        }
        return tradingPairs
                .stream()
                .map(CryptoTradingPair::getTradingPair).toList();
    }

    public String getServerTradingPairs() throws JsonProcessingException {
        List<CryptoTradingPair> tradingPairs = cryptoRepository.findAll();
        if (tradingPairs.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> tradingPairList = tradingPairs.stream()
                .map(tp -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("subscribeNumber", cryptoRepository.countCryptoSubscribersNumber(tp));
                    map.put("tradingPair", tp.getTradingPair());
                    return map;
                })
                .toList();

        return objectMapper.writeValueAsString(tradingPairList);
    }

    public void checkAndReconnectWebSocket() {
        if (!isRunning) {
            try {
                logger.info("開啟自動重連WebSocket: " + isNeedToCheckConnection+ " ，正在重新連線...");
                openConnection();
                logger.info("重新連線成功");
            } catch (Exception e) {
                logger.error("重新連線失敗: " + e.getMessage());
            }
        }
    }
    public boolean isNeedToCheckConnection() {
        return isNeedToCheckConnection;
    }
}
