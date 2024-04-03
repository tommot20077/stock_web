package xyz.dowob.stockweb.Service.Stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

import javax.sound.midi.Track;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class StockTwService {
    private final StockTwRepository stockTwRepository;
    private final SubscribeRepository subscribeRepository;
    private final StockTwInfluxDBService stockTwInfluxDBService;
    private final ObjectMapper objectMapper;
    private final String stockListUrl = "https://api.finmindtrade.com/api/v4/data?";
    private final String stockCurrentPriceUrl = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=";
    RateLimiter rateLimiter = RateLimiter.create(1.0);

    @Value(value = "${stock.tw.finmind.token}")
    private  String finMindToken;

    private final Logger logger = LoggerFactory.getLogger(StockTwService.class);
    @Autowired
    public StockTwService(StockTwRepository stockTwRepository, SubscribeRepository subscribeRepository, StockTwInfluxDBService stockTwInfluxDBService, ObjectMapper objectMapper) {
        this.stockTwRepository = stockTwRepository;
        this.subscribeRepository = subscribeRepository;
        this.stockTwInfluxDBService = stockTwInfluxDBService;
        this.objectMapper = objectMapper;
    }
    @Transactional(rollbackFor = Exception.class)
    public void addStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼"));
        Long assetId = stock.getId();

        if (subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).isPresent()) {
            throw new RuntimeException("已經訂閱過此股票");
        }

        if (!stock.checkUserIsSubscriber(user)) {
            stock.getSubscribers().add(user.getId());
            logger.debug("訂閱成功");
        }
        stock.setAssetType(AssetType.STOCK_TW);
        stockTwRepository.save(stock);

        Subscribe subscribe = new Subscribe();
        subscribe.setUser(user);
        subscribe.setAsset(stock);
        subscribe.setUserSubscribed(true);
        subscribeRepository.save(subscribe);


    }

    @Transactional(rollbackFor = Exception.class)
    public void removeStockSubscribeToUser(String stockId, User user) {
        StockTw stock = stockTwRepository.findByStockCode(stockId).orElseThrow(() -> new RuntimeException("沒有找到指定的股票代碼: " + stockId));
        Long assetId = stock.getId();
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetId(user.getId(), assetId).orElseThrow(() -> new RuntimeException("沒有找到指定的訂閱"));
        if (subscribe.isUserSubscribed()) {
            if (stock.checkUserIsSubscriber(user)) {
                stock.getSubscribers().remove(user.getId());
                logger.debug("取消訂閱成功");
            }
            stockTwRepository.save(stock);
            subscribeRepository.delete(subscribe);
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
                } else {
                    throw new RuntimeException("無法解析股票列表");
                }
            } catch (Exception e) {
                throw new RuntimeException("更新股票列表失敗");
            }
        } else {
            throw new RuntimeException("更新股票列表失敗");
        }
    }

    public Map<String, List<String>> CheckSubscriptionValidity() {
        //TODO: 收盤數處處理

        RestTemplate restTemplate = new RestTemplate();
        Set<Object[]> subscribeList = stockTwRepository.findAllAssetIdsWithSubscribers();
        List<String> checkSuccessList = new ArrayList<>();
        List<String> checkFailList = new ArrayList<>();
        List<String> inquiryList = new ArrayList<>();
        subscribeList.forEach(subscribe -> {
            String url;
            String inquiry;
            String stockCode = subscribe[0].toString();
            String stock_type = subscribe[1].toString();
            if (stock_type.equals("twse")) {
                inquiry ="tse_" + stockCode + ".tw";
                url = stockCurrentPriceUrl + inquiry;
            } else if (stock_type.equals("otc")) {
                inquiry ="otc_" + stockCode + ".tw";
                url = stockCurrentPriceUrl + inquiry;
            } else {
                logger.warn("暫時不支援此類型: " + stockCode + "-" + stock_type);
                return;
            }
            logger.debug("查詢股票: " + stockCode + " " + stock_type);
            logger.debug("查詢股票網址: " + url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode node;
            try {
                node = objectMapper.readTree(response);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Json轉換錯誤: " + response, e);
            }
            JsonNode msgArray = node.path("msgArray");
            if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
                logger.debug("回應訊息成功");
                checkSuccessList.add((String) subscribe[0]);
                inquiryList.add(inquiry);
            } else {
                logger.debug("回應訊息失敗");
                checkFailList.add((String) subscribe[0]);
            }
            rateLimiter.acquire();
        });



        Map<String, List<String>> result = new HashMap<>();
        result.put("success", checkSuccessList);
        result.put("fail", checkFailList);
        result.put("inquiry", inquiryList);

        return result;
    }

    @Async
    public void trackStockPrices(List<String> stockInquiryList) throws JsonProcessingException {

        RestTemplate restTemplate = new RestTemplate();
        final StringBuilder inquireUrl = new StringBuilder(stockCurrentPriceUrl);
        logger.debug("要追蹤價格的股票: " + stockInquiryList);
        stockInquiryList.forEach(stockInquiry -> {
            inquireUrl.append(stockInquiry).append("|");
        });
        logger.debug("拼接查詢網址: " + inquireUrl);

        String response = restTemplate.getForObject(inquireUrl.toString(), String.class);
        logger.debug("查詢結果: " + response);
        JsonNode node = objectMapper.readTree(response);
        JsonNode msgArray = node.path("msgArray");
        if (!msgArray.isMissingNode() && msgArray.isArray() && !msgArray.isEmpty()) {
            logger.debug("開始寫入到inflxdb");
            stockTwInfluxDBService.writeToInflux(msgArray);
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
