package xyz.dowob.stockweb.Component.Method;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.NewsService;
import xyz.dowob.stockweb.Service.Common.Property.PropertyInfluxService;
import xyz.dowob.stockweb.Service.Common.Property.PropertyService;
import xyz.dowob.stockweb.Service.Common.RedisService;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author tommo
 */
@Component
public class CrontabMethod {
    private final TokenService tokenService;
    private final CurrencyService currencyService;
    private final StockTwService stockTwService;
    private final CryptoService cryptoService;
    private final UserService userService;
    private final PropertyService propertyService;
    private final NewsService newsService;
    private final RedisService redisService;
    private final AssetService assetService;
    private final CryptoWebSocketHandler cryptoWebSocketHandler;
    private final SubscribeMethod subscribeMethod;
    private final PropertyInfluxService propertyInfluxService;


    @Autowired
    public CrontabMethod(TokenService tokenService, CurrencyService currencyService, StockTwService stockTwService, CryptoService cryptoService, UserService userService, PropertyService propertyService, NewsService newsService, RedisService redisService, AssetService assetService, CryptoWebSocketHandler cryptoWebSocketHandler, SubscribeMethod subscribeMethod, PropertyInfluxService propertyInfluxService) {
        this.tokenService = tokenService;
        this.currencyService = currencyService;
        this.stockTwService = stockTwService;
        this.cryptoService = cryptoService;
        this.userService = userService;
        this.propertyService = propertyService;
        this.newsService = newsService;
        this.redisService = redisService;
        this.assetService = assetService;
        this.cryptoWebSocketHandler = cryptoWebSocketHandler;
        this.subscribeMethod = subscribeMethod;
        this.propertyInfluxService = propertyInfluxService;
    }

    private List<String> trackableStocks = new ArrayList<>();
    private boolean immediatelyUpdateStockTw = false;
    Logger logger = LoggerFactory.getLogger(CrontabMethod.class);

    @Value("${news.remain.days}") private int newsRemainDays;

    @Value("${news.autoupdate.currency}") private boolean newsAutoupdateCurrency;

    @Value("${news.autoupdate.crypto}") private boolean newsAutoupdateCrypto;

    @Value("${news.autoupdate.stock_tw}") private boolean newsAutoupdateStockTw;

    @Value("${common.global_size}") private int pageSize;

    @Value("${stock_tw.enable_auto_start}") private boolean isStockTwAutoStart;

    @PostConstruct
    public void init() {
        if (isStockTwAutoStart) {
            logger.info("已開啟股票台灣自動更新");
            immediatelyUpdateStockTw = true;
        }
    }


    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanExpiredTokens() {
        tokenService.removeExpiredTokens();
    }

    @Scheduled(cron = "0 30 */2 * * ?")
    public void updateCurrencyData() {
        currencyService.updateCurrencyData();
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Taipei")
    public void updateStockList() {
        stockTwService.updateStockList();
    }

    @Scheduled(cron = "0 30 8 * * ? ", zone = "Asia/Taipei")
    public void checkSubscriptions() throws JsonProcessingException {

        logger.info("正在檢查訂閱狀況");
        logger.debug(String.valueOf(trackableStocks));
        Map<String, List<String>> subscriptionValidity = stockTwService.checkSubscriptionValidity();
        if (subscriptionValidity != null) {
            logger.debug("成功獲取列表，加入到處理欄中");
            trackableStocks = subscriptionValidity.get("inquiry");
        } else {
            logger.debug("無法獲取列表，重新獲取");
            trackableStocks = stockTwService.checkSubscriptionValidity().get("inquiry");
            if (trackableStocks != null && !trackableStocks.isEmpty()) {
                logger.debug("成功獲取列表，加入到處理欄中");
                stockTwService.trackStockNowPrices(trackableStocks);
            } else {
                logger.info("沒有可以訂閱的股票");
            }
        }
    }

    @Scheduled(cron = "*/5 * 9-13 * * MON-FRI ", zone = "Asia/Taipei")
    public void trackStockTwPricesPeriodically() {
        if (immediatelyUpdateStockTw) {
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Taipei"));
            if (!trackableStocks.isEmpty()) {
                logger.debug("已經獲取列表");

                if (now.isAfter(LocalTime.of(13, 30)) && now.getMinute() % 10 == 0) {
                    logger.debug("收盤時間:更新速度為10分鐘");
                    stockTwService.trackStockNowPrices(trackableStocks);
                } else {
                    logger.debug("開盤時間:更新速度為5秒");
                    stockTwService.trackStockNowPrices(trackableStocks);
                }
            } else {
                logger.debug("列表為空，嘗試獲取訂閱列表");
                try {
                    checkSubscriptions();
                } catch (JsonProcessingException e) {
                    logger.error("無法獲取列表: " + e.getMessage());
                }
            }
        }
    }

    @Scheduled(cron = "0 30 16 * * MON-FRI ", zone = "Asia/Taipei")
    public void updateStockHistoryPrices() {
        logger.info("開始更新股票的每日最新價格");
        stockTwService.trackStockHistoryPricesWithUpdateDaily();
    }

    @Scheduled(cron = "0 30 2 * * ? ", zone = "UTC")
    public void updateCryptoHistoryPrices() {
        logger.info("開始更新加密貨幣的每日最新價格");
        cryptoService.trackCryptoHistoryPricesWithUpdateDaily();
    }

    @Scheduled(cron = "0 30 */4 * * ? ", zone = "UTC")
    public void checkHistoryData() {
        logger.info("開始檢查資產的歷史數據");
        subscribeMethod.CheckSubscribedAssets();
    }

    @Scheduled(cron = "0 0 */1 * * ? ", zone = "UTC")
    public void recordUserPropertySummary() {
        logger.info("開始記錄使用者的資產總價");
        List<User> users = userService.getAllUsers();
        logger.debug(String.valueOf(users));
        try {
            for (User user : users) {
                logger.debug("正在記錄使用者 " + user.getUsername() + " 的資產總價");
                List<PropertyListDto.getAllPropertiesDto> getAllPropertiesDtoList = propertyService.getUserAllProperties(user, false);
                if (getAllPropertiesDtoList == null) {
                    logger.debug("用戶:" + user.getUsername() + "沒有資產可以記錄");
                    logger.info("重製用戶 " + user.getUsername() + " 的influx資產資料庫");
                    propertyService.resetUserPropertySummary(user);
                    logger.info("重製用戶資料完成");
                    continue;
                }
                List<PropertyListDto.writeToInfluxPropertyDto> toInfluxPropertyDto = propertyService.convertGetAllPropertiesDtoToWriteToInfluxPropertyDto(
                        getAllPropertiesDtoList);
                propertyService.writeAllPropertiesToInflux(toInfluxPropertyDto, user);
            }
            logger.debug("記錄完成");
        } catch (Exception e) {
            logger.error("記錄失敗", e);
        }
    }

    @Scheduled(cron = "0 10 */4 * * ? ", zone = "UTC")
    public void updateUserCashFlow() {
        logger.info("開始更新使用者的現金流");
        List<User> users = userService.getAllUsers();
        try {
            for (User user : users) {
                propertyInfluxService.writeNetFlowToInflux(BigDecimal.ZERO, user);
            }
            logger.debug("更新完成");
        } catch (Exception e) {
            logger.error("更新失敗", e);
        }
    }

    @Scheduled(cron = "0 40 */4 * * ? ", zone = "UTC")
    public void updateUserRoiData() {
        logger.info("開始更新使用者的 ROI");
        Long time = Instant.now().toEpochMilli();
        List<User> users = userService.getAllUsers();
        for (User user : users) {
            List<String> roiResult = propertyService.prepareRoiDataAndCalculate(user);
            ObjectNode roiObject = propertyService.formatToObjectNode(roiResult);
            propertyInfluxService.writeUserRoiDataToInflux(roiObject, user, time);
        }
        logger.debug("更新完成");
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndReconnectWebSocket() {
        if (cryptoService.isNeedToCheckConnection() && !cryptoWebSocketHandler.isRunning()) {
            cryptoService.checkAndReconnectWebSocket();
        }
    }

    @Scheduled(cron = "0 0 1 * * ? ", zone = "UTC")
    public void removeExpiredNews() {
        logger.info("開始刪除過期的新聞");
        LocalDateTime removeTime = LocalDateTime.now().minusDays(newsRemainDays);
        newsService.deleteNewsAfterDate(removeTime);
        logger.debug("刪除完成");
    }

    @Scheduled(cron = "0 30 */8 * * ? ", zone = "UTC")
    public void updateNewsData() {
        logger.info("開始更新頭條新聞");
        redisService.deleteByPattern("news_headline_page_*");
        logger.debug("刪除緩存完成");
        newsService.sendNewsRequest(true, 1, null, null);
        logger.info("開始更新訂閱資產的新聞");
        List<Asset> subscribeAsset = assetService.findHasSubscribeAsset(newsAutoupdateCrypto,
                                                                        newsAutoupdateStockTw,
                                                                        newsAutoupdateCurrency);
        for (Asset asset : subscribeAsset) {
            logger.debug("正在更新 " + asset.getId() + " 的新聞");
            newsService.sendNewsRequest(false, 1, null, asset);
        }
        logger.info("新聞更新完成");
    }

    @Scheduled(cron = "0 10 */4 * * ? ", zone = "UTC")
    public void updateAssetListCache() {
        try {
            List<String> keys = new ArrayList<>(Arrays.asList("crypto", "stock_tw", "currency"));
            logger.info("開始更新資產列表緩存");

            for (String key : keys) {
                int totalPage = assetService.findAssetTotalPage(key, pageSize);
                for (int page = 1; page <= totalPage; page++) {
                    logger.debug("正在更新 " + key + " 的第 " + page + " 頁資產列表緩存");
                    String innerKey = keys + "_page_" + page;
                    List<Asset> assetsList = assetService.findAssetPageByType(key, page, false);
                    assetService.formatStringAssetListToFrontendType(assetsList, innerKey);
                }
            }
            logger.info("資產列表緩存更新完成");
        } catch (Exception e) {
            logger.error("更新資產列表緩存失敗", e);
            throw new RuntimeException("更新資產列表緩存失敗", e);
        }
    }

    public void operateStockTwTrack(boolean isOpen) {
        immediatelyUpdateStockTw = isOpen;
        logger.info("已獲取股票台灣自動更新狀態 " + isOpen);
    }
    public boolean isStockTwAutoStart() {
        return immediatelyUpdateStockTw;
    }
}

