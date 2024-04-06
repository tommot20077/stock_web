package xyz.dowob.stockweb.Service.Crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import xyz.dowob.stockweb.Service.Common.FileSevice;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CryptoService {

    Logger logger = LoggerFactory.getLogger(CryptoService.class);
    private volatile boolean isRunning = false;
    private boolean isNeedToCheckConnection = false;
    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    private final CryptoInfluxDBService cryptoInfluxDBService;
    private WebSocketConnectionManager connectionManager;
    private final CryptoRepository cryptoRepository;
    private final ObjectMapper objectMapper;
    private final FileSevice fileSevice;

    @Value("${db.influxdb.bucket.crypto_history.detail}")
    private String frequency;
    private final String dataUrl = "https://data.binance.vision/data/spot/";
    RateLimiter rateLimiter = RateLimiter.create(1.0);



    @Autowired
    public CryptoService(CryptoWebSocketHandler webSocketHandler, CryptoInfluxDBService cryptoInfluxDBService, WebSocketConnectionManager connectionManager, CryptoRepository cryptoRepository, ObjectMapper objectMapper, FileSevice fileSevice) {
        this.cryptoWebSocketHandler = webSocketHandler;
        this.cryptoInfluxDBService = cryptoInfluxDBService;
        this.connectionManager = connectionManager;
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = objectMapper;
        this.fileSevice = fileSevice;
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
        String cryptoListUrl = "https://api.binance.com/api/v3/exchangeInfo";
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(cryptoListUrl, String.class);
        logger.debug("更新幣種交易對列表: " + response.getBody());
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("symbols");
                logger.debug("共有交易對數量: " + dataArray.size());
                if (dataArray.isArray()) {
                    logger.debug("陣列資料: " + dataArray);
                    for (JsonNode cryptoNode : dataArray) {
                        if ("TRADING".equals(cryptoNode.get("status").asText())) {
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
                throw new InterruptedException("請求過多，請在" + retryAfter + "秒後再試");
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

    @Async
    public void trackCryptoHistoryPrices (CryptoTradingPair cryptoTradingPair) {
        boolean hasData = true;
        LocalDate endDate = LocalDate.parse("20230601", DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate todayDate = LocalDate.now(ZoneId.of("UTC"));
        LocalDate getMonthlyDate = todayDate.minusMonths(2);
        LocalDate getDailyDate = todayDate.minusMonths(1).withDayOfMonth(1);
        DateTimeFormatter monthlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");


        while (hasData && (getMonthlyDate.isAfter(endDate) || getMonthlyDate.isEqual(endDate))) {
            String formatGetMonthlyDate = getMonthlyDate.format(monthlyFormatter);
            String fileName = String.format("%s-%s-%s.zip", cryptoTradingPair.getTradingPair(), frequency, formatGetMonthlyDate);
            String monthlyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", "monthly", cryptoTradingPair.getTradingPair(), frequency, fileName);
            logger.debug("要追蹤歷史價格的交易對: " + cryptoTradingPair.getTradingPair());
            logger.debug("歷史價格資料網址: " + monthlyUrl);
            rateLimiter.acquire();
            List<String[]> csvData = fileSevice.downloadFileAndUnzipAndRead(monthlyUrl, fileName);

            if (csvData == null) {
                logger.debug("歷史價格資料讀取失敗");
                hasData = false;
            } else {
                logger.debug("歷史價格資料讀取完成");
                cryptoInfluxDBService.writeCryptoHistoryToInflux(csvData, cryptoTradingPair.getTradingPair());
                getMonthlyDate = getMonthlyDate.minusMonths(1);
                logger.debug("抓取" + fileName + "的資料完成，開始處理下筆資料");
            }
        }
        hasData = true;
        endDate = todayDate.minusDays(1);
        while (hasData && (getDailyDate.isBefore(endDate)) || getDailyDate.isEqual(endDate)) {
            String formatGetDailyDate = getDailyDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String fileName = String.format("%s-%s-%s.zip", cryptoTradingPair.getTradingPair(), frequency,
                                            formatGetDailyDate);
            String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", "daily", cryptoTradingPair.getTradingPair(),
                                            frequency, fileName);
            logger.debug("要追蹤歷史價格的交易對: " + cryptoTradingPair.getTradingPair());
            logger.debug("歷史價格資料網址: " + dailyUrl);
            rateLimiter.acquire();
            List<String[]> csvData = fileSevice.downloadFileAndUnzipAndRead(dailyUrl, fileName);
            if (csvData == null) {
                logger.debug("沒有讀取到歷史價格");
                hasData = false;
            } else {
                logger.debug("歷史價格資料讀取完成");
                cryptoInfluxDBService.writeCryptoHistoryToInflux(csvData, cryptoTradingPair.getTradingPair());
                getDailyDate = getDailyDate.plusDays(1);
                logger.debug("抓取" + fileName + "的資料完成，開始處理下筆資料");
            }
        }
        logger.debug("歷史價格資料抓取完成");
    }


    public void trackCryptoHistoryPricesWithUpdateDaily() {
        Set<String> needToUpdateTradingPairs = cryptoRepository.findAllTradingPairBySubscribers();
        logger.debug("要更新每日最新價格的交易對: " + needToUpdateTradingPairs);
        if (needToUpdateTradingPairs.isEmpty()) {
            logger.debug("沒有要更新的交易對");
            return;
        }
        LocalDate trackDate = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        String formatGetDailyDate = trackDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        needToUpdateTradingPairs.forEach(tradingPair -> {
            String fileName = String.format("%s-%s-%s.zip", tradingPair, frequency, formatGetDailyDate);
            String dailyUrl = String.format(dataUrl + "%s/klines/%s/%s/%s", "daily", tradingPair, frequency, fileName);
            logger.debug("要追蹤歷史價格的交易對: " + tradingPair);
            logger.debug("歷史價格資料網址: " + dailyUrl);
            rateLimiter.acquire();
            List<String[]> csvData = fileSevice.downloadFileAndUnzipAndRead(dailyUrl, fileName);
            if (csvData == null) {
                logger.debug("沒有讀取到歷史價格");
            } else {
                logger.debug("歷史價格資料讀取完成");
                cryptoInfluxDBService.writeCryptoHistoryToInflux(csvData, tradingPair);
                logger.debug("抓取" + fileName + "的資料完成，開始處理下筆資料");
            }
        });
        logger.debug("歷史價格資料抓取完成");
    }

    public CryptoTradingPair getCryptoTradingPair(String tradingPair) {
        return cryptoRepository.findByTradingPair(tradingPair).orElseThrow(() -> new RuntimeException("找不到交易對: " + tradingPair));
    }
}
