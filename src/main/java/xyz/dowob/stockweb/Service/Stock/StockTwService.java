package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StockTwService {
    private final StockTwRepository stockTwRepository;
    private final SubscribeRepository subscribeRepository;
    private final InfluxDBClient stockInfluxDBClient;
    private final ObjectMapper objectMapper;
    private final String stockListUrl = "https://api.finmindtrade.com/api/v4/data?";

    @Value(value = "${stock.tw.finmind.token}")
    private  String finMindToken;

    private Logger logger = LoggerFactory.getLogger(StockTwService.class);
    @Autowired
    public StockTwService(StockTwRepository stockTwRepository, SubscribeRepository subscribeRepository, @Qualifier("StockInfluxDBClient") InfluxDBClient stockInfluxDBClient, ObjectMapper objectMapper) {
        this.stockTwRepository = stockTwRepository;
        this.subscribeRepository = subscribeRepository;
        this.stockInfluxDBClient = stockInfluxDBClient;
        this.objectMapper = objectMapper;
    }

    public void addStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼"));
        Long assetId = stock.getId();

        if (subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).isPresent()) {
            throw new RuntimeException("已經訂閱過此股票");
        }

        stock.setSubscribeNumber(stock.getSubscribeNumber() + 1);
        stock.setAssetType(AssetType.STOCK_TW);
        stockTwRepository.save(stock);

        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(stock);
        subscribeRepository.save(subscribe);


    }

    @Transactional
    public void removeStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼"));
        if (stock != null) {
            Long assetId = stock.getId();
            if (subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).isPresent()) {
                subscribeRepository.deleteByUserIdAndAssetId(user.getId(), assetId);

                stock.setSubscribeNumber(stock.getSubscribeNumber() - 1);
                stockTwRepository.save(stock);
            } else {
                throw new RuntimeException("沒有找到指定的訂閱");
            }
        } else {
            throw new RuntimeException("沒有找到指定的股票代碼");
        }
    }
    @Transactional
    @Async
    public void updateStockList() {
        StockTw stock;
        String url = stockListUrl + "dataset=TaiwanStockInfo&stock_id=&token=" + finMindToken;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        logger.debug("更新股票列表: " + response.getBody());
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataArray = jsonNode.get("data");
                logger.debug("股票數量: " + dataArray.size());
                if (dataArray.isArray()) {
                    logger.debug("陣列資料: " + dataArray);
                    for (JsonNode stockNode : dataArray) {
                        stock = stockTwRepository.findByStockCode(stockNode.get("stock_id").asText()).orElse(new StockTw());
                        logger.debug("更新股票: " + stockNode.get("stock_id").asText());
                        stock.setStockCode(stockNode.get("stock_id").asText());
                        stock.setStockName(stockNode.get("stock_name").asText());
                        stock.setIndustryCategory(stockNode.get("industry_category").asText());
                        stock.setStockType(stockNode.get("type").asText());
                        stock.setUpdateTime(formatStringToDate(stockNode.get("date").asText()));
                        stock.setAssetType(AssetType.STOCK_TW);
                        stockTwRepository.save(stock);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("更新股票列表失敗");
            }
        }
    }

    public StockTw getStockData(String stockId) {
        return stockTwRepository.findByStockCode(stockId).orElse(null);
    }

    public List<Object[]> getAllStockData() {
        return stockTwRepository.findDistinctStockCodeAndName();
    }

    private String formatDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate date = LocalDate.now();
        return date.format(formatter);
    }

    private LocalDate formatStringToDate(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(date, formatter);
        } catch (Exception e) {
            return null;
        }

    }



}
