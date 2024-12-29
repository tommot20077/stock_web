package xyz.dowob.stockweb.Component.Method;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.ImmediateDataUpdateEvent;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Enum.AssetType;
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
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 這是一個定時任務方法，用於執行定時任務。
 * 它包含了一系列的定時任務，用於更新資料庫中的資料。
 *
 * @author yuan
 */
@Log4j2
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

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 這是一個構造函數，用於注入服務。
     *
     * @param tokenService           用戶令牌服務
     * @param currencyService        貨幣相關服務
     * @param stockTwService         台股相關服務
     * @param cryptoService          加密貨幣相關服務
     * @param userService            用戶相關服務
     * @param propertyService        資產相關服務
     * @param newsService            新聞相關服務
     * @param redisService           緩存相關服務
     * @param assetService           資產相關服務
     * @param cryptoWebSocketHandler 加密貨幣WebSocket處理器
     * @param subscribeMethod        訂閱相關方法
     * @param propertyInfluxService  資產Influx相關服務
     */
    public CrontabMethod(TokenService tokenService, CurrencyService currencyService, StockTwService stockTwService, CryptoService cryptoService, UserService userService, PropertyService propertyService, NewsService newsService, RedisService redisService, AssetService assetService, CryptoWebSocketHandler cryptoWebSocketHandler, SubscribeMethod subscribeMethod, PropertyInfluxService propertyInfluxService, ApplicationEventPublisher applicationEventPublisher) {
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
        this.applicationEventPublisher = applicationEventPublisher;
    }

    private List<String> trackableStocks = new ArrayList<>();

    private boolean immediatelyUpdateStockTw = false;

    @Value("${news.remain.days:30}")
    private int newsRemainDays;

    @Value("${news.autoupdate.currency:false}")
    private boolean newsAutoupdateCurrency;

    @Value("${news.autoupdate.crypto:true}")
    private boolean newsAutoupdateCrypto;

    @Value("${news.autoupdate.stock_tw:true}")
    private boolean newsAutoupdateStockTw;

    @Value("${common.global_page_size:100}")
    private int pageSize;

    @Value("${stock_tw.enable_auto_start:false}")
    private boolean isStockTwAutoStart;

    @Value("${common.kafka.enable:false}")
    private boolean isKafkaEnable;

    /**
     * 初始化方法
     * 1.台股即時更新:根據配置文件設置stock_tw.enable_auto_start
     * 2.檢查資產前綴樹緩存是否存在，不存在則重新緩存
     */
    @PostConstruct
    public void init() {
        try {
            if (isStockTwAutoStart || isKafkaEnable) {
                immediatelyUpdateStockTw = true;
            }
            if (redisService.getCacheValueFromKey("assetTrie") == null) {
                assetService.cacheTrieToRedis();
            }
        } catch (Exception e) {
            log.error("初始化失敗: {}", e.getMessage());
        }
    }

    /**
     * 清除過期的token
     * 每天凌晨1點
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanExpiredTokens() {
        try {
            tokenService.removeExpiredTokens();
        } catch (Exception e) {
            log.error("清除過期的token失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新匯率資料
     * 每2小時
     */
    @Scheduled(cron = "0 30 */2 * * ?")
    public void updateCurrencyData() {
        try {
            currencyService.updateCurrencyData();
        } catch (Exception e) {
            log.error("更新匯率資料失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新台股股票列表
     * 每天凌晨3點
     */
    @Scheduled(cron = "0 0 3 * * ?",
               zone = "Asia/Taipei")
    public void updateStockList() {
        try {
            stockTwService.updateStockList();
        } catch (Exception e) {
            log.error("更新台股股票列表失敗: {}", e.getMessage());
        }
    }

    /**
     * 檢查台灣股票訂閱狀況，如果有訂閱則追蹤即時交易價格
     * 每天早上8:30
     */
    @Scheduled(cron = "0 30 8 * * ? ",
               zone = "Asia/Taipei")
    public void checkSubscriptions() {
        try {
            Map<String, List<String>> subscriptionValidity = stockTwService.checkSubscriptionValidity();
            if (subscriptionValidity != null) {
                trackableStocks = subscriptionValidity.get("inquiry");
            } else {
                trackableStocks = stockTwService.checkSubscriptionValidity().get("inquiry");
                if (trackableStocks != null && !trackableStocks.isEmpty()) {
                    stockTwService.trackStockNowPrices(trackableStocks);
                }
            }
        } catch (Exception e) {
            log.error("檢查台灣股票訂閱狀況失敗: {}", e.getMessage());
        }
    }

    /**
     * 追蹤台股股票5秒搓合交易價格
     * 在開盤時間每5秒更新一次，收盤時間每10分鐘更新一次
     * 台灣時間週一至週五上午9點至下午2點
     */
    @Scheduled(cron = "*/5 * 9-13 * * MON-FRI ",
               zone = "Asia/Taipei")
    public void trackStockTwPricesPeriodically() {
        try {
            if (immediatelyUpdateStockTw) {
                LocalTime now = LocalTime.now(ZoneId.of("Asia/Taipei"));
                if (!trackableStocks.isEmpty()) {
                    if (now.isAfter(LocalTime.of(13, 30)) && now.getMinute() % 10 == 0) {
                        stockTwService.trackStockNowPrices(trackableStocks);
                    } else {
                        stockTwService.trackStockNowPrices(trackableStocks);
                    }
                } else {
                    checkSubscriptions();
                }
            }
        } catch (Exception e) {
            log.error("追蹤台股股票5秒搓合交易價格失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新台股股票的每日歷史價格
     * 台灣時間週一至週五下午4點30分
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI ",
               zone = "Asia/Taipei")
    public void updateStockHistoryPrices() {
        try {
            stockTwService.trackStockHistoryPricesWithUpdateDaily();
        } catch (Exception e) {
            log.error("更新台股股票的每日歷史價格失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新加密貨幣的每日歷史價格
     * 每天UTC時間凌晨2點30分
     */
    @Scheduled(cron = "0 30 2 * * ? ",
               zone = "UTC")
    public void updateCryptoHistoryPrices() {
        try {
            cryptoService.trackCryptoHistoryPricesWithUpdateDaily();
        } catch (Exception e) {
            log.error("更新加密貨幣的每日歷史價格失敗: {}", e.getMessage());
        }
    }

    /**
     * 檢查資產的歷史數據完整性
     * 每4小時執行一次
     */
    @Scheduled(cron = "0 30 */4 * * ? ",
               zone = "UTC")
    public void checkHistoryData() {
        try {
            subscribeMethod.CheckSubscribedAssets();
        } catch (Exception e) {
            log.error("檢查資產的歷史數據完整性失敗: {}", e.getMessage());
        }
    }

    /**
     * 記錄使用者的資產總價, 並寫入Influx
     * 當用戶沒有資產時, 重製用戶的Influx資產資料庫
     * 每小時執行一次
     */
    @Scheduled(cron = "0 0 */1 * * ? ")
    public void recordUserPropertySummary() {
        try {
            List<User> users = userService.getAllUsers();
            for (User user : users) {
                List<PropertyListDto.getAllPropertiesDto> getAllPropertiesDtoList = propertyService.getUserAllProperties(user, false);
                if (getAllPropertiesDtoList == null) {
                    propertyService.resetUserPropertySummary(user);
                    continue;
                }
                List<PropertyListDto.writeToInfluxPropertyDto> toInfluxPropertyDto = propertyService.convertGetAllPropertiesDtoToWriteToInfluxPropertyDto(
                        getAllPropertiesDtoList);
                propertyService.writeAllPropertiesToInflux(toInfluxPropertyDto, user);
            }
        } catch (Exception e) {
            log.error("記錄使用者的資產總價失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新使用者的現金流
     * 每4小時執行一次
     */
    @Scheduled(cron = "0 10 */4 * * ? ",
               zone = "UTC")
    public void updateUserCashFlow() {
        try {
            List<User> users = userService.getAllUsers();
            for (User user : users) {
                propertyInfluxService.writeNetFlowToInflux(BigDecimal.ZERO, user);
            }
        } catch (Exception e) {
            log.error("更新使用者的現金流失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新使用者的 ROI
     * 每4小時執行一次
     */
    @Scheduled(cron = "0 40 */4 * * ? ",
               zone = "UTC")
    public void updateUserRoiData() {
        try {
            Long time = Instant.now().toEpochMilli();
            List<User> users = userService.getAllUsers();
            for (User user : users) {
                List<String> roiResult = propertyService.prepareRoiDataAndCalculate(user);
                ObjectNode roiObject = propertyService.formatToObjectNode(roiResult);
                propertyInfluxService.writeUserRoiDataToInflux(roiObject, user, time);
            }
        } catch (Exception e) {
            log.error("更新使用者的 ROI 失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新使用者的 ROI 統計資料
     * 每4小時執行一次
     */
    @Scheduled(cron = "0 45 */4 * * ? ",
               zone = "UTC")
    public void updateUserRoiStatistic() {
        try {
            List<User> users = userService.getAllUsers();
            for (User user : users) {
                Map<String, BigDecimal> roiStatisticResult = propertyService.roiStatisticCalculation(user);
                propertyInfluxService.writeUserRoiStatisticsToInflux(roiStatisticResult, user);
                Map<String, String> sharpeRatioResult = propertyService.calculateSharpeRatio(user);
                propertyInfluxService.writeUserSharpRatioToInflux(sharpeRatioResult, user);
                Map<String, Map<String, List<BigDecimal>>> drawDownResult = propertyService.calculateUserDrawDown(user);
                propertyInfluxService.writeUserDrawDownToInflux(drawDownResult, user);
            }
        } catch (Exception e) {
            log.error("更新使用者的 ROI 統計資料失敗: {}", e.getMessage());
        }
    }

    /**
     * 檢查並重新連接WebSocket
     * 每5分鐘
     */
    @Scheduled(fixedRate = 300000)
    public void checkAndReconnectWebSocket() {
        try {
            if (cryptoService.isNeedToCheckConnection() && !cryptoWebSocketHandler.isRunning()) {
                cryptoService.checkAndReconnectWebSocket();
            }
        } catch (Exception e) {
            log.error("檢查並重新連接WebSocket失敗: {}", e.getMessage());
        }
    }

    /**
     * 刪除過期的新聞, 保留最近指定時間內的新聞
     * 預設保留30天，可在配置文件中設置{newsRemainDays}
     * 每天UTC時間凌晨1點
     */
    @Transactional
    @Scheduled(cron = "0 0 1 * * ? ",
               zone = "UTC")
    public void removeExpiredNews() {
        try {
            if (newsRemainDays > 0) {
                LocalDateTime removeTime = LocalDateTime.now().minusDays(newsRemainDays);
                newsService.deleteNewsBeforeDate(removeTime);
            }
        } catch (Exception e) {
            log.error("刪除過期的新聞失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新新聞資料
     * 1. 刪除新聞緩存
     * 2. 更新頭條新聞
     * 3. 更新訂閱資產的新聞
     * 4. 更新債券新聞
     * 每8小時執行一次
     */
    @Scheduled(cron = "0 30 */8 * * ? ")
    public void updateNewsData() {
        try {
            redisService.deleteByPattern("news_headline_page_*");
            newsService.sendNewsRequest(true, 1, null, null);
            List<Asset> subscribeAsset = assetService.findHasSubscribeAsset(newsAutoupdateCrypto,
                                                                            newsAutoupdateStockTw,
                                                                            newsAutoupdateCurrency);
            for (Asset asset : subscribeAsset) {
                newsService.sendNewsRequest(false, 1, null, asset);
            }
            newsService.sendNewsRequest(false, 1, "DEBT", null);
        } catch (Exception e) {
            log.error("更新新聞資料失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新資產列表緩存
     * 每4小時執行一次
     */
    @Scheduled(cron = "0 10 */4 * * ? ")
    public void updateAssetListCache() {
        try {
            List<String> keys = new ArrayList<>(Arrays.asList("crypto", "stock_tw", "currency"));
            for (String key : keys) {
                int totalPage = assetService.findAssetTotalPage(key, pageSize);
                for (int page = 1; page <= totalPage; page++) {
                    String innerKey = keys + "_page_" + page;
                    List<Asset> assetsList = assetService.findAssetPageByType(key, page, false);
                    assetService.formatStringAssetListToFrontendType(assetsList, innerKey);
                }
            }
        } catch (Exception e) {
            log.error("更新資產列表緩存失敗: {}", e.getMessage());
        }
    }

    /**
     * 更新政府公債資料
     * 每天凌晨2點
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void updateGovernmentBondsData() {
        try {
            assetService.GovernmentBondsDataFetcherAndSaveToInflux();
        } catch (Exception e) {
            log.error("更新政府公債資料失敗: {}", e.getMessage());
        }
    }

    /**
     * 變更股票台灣自動更新狀態
     *
     * @param isOpen 是否開啟
     */
    public void operateStockTwTrack(boolean isOpen) {
        try {
            immediatelyUpdateStockTw = isOpen;
            applicationEventPublisher.publishEvent(new ImmediateDataUpdateEvent(this, isOpen, AssetType.STOCK_TW));
        } catch (Exception e) {
            log.error("變更股票台灣自動更新狀態失敗: {}", e.getMessage());
        }
    }

    /**
     * 獲取股票台灣自動更新狀態
     * 獲取當前時間，判斷是否在開盤時間
     * 如果在開盤時間，則返回是否開啟
     * 如果不在開盤時間，則返回false
     *
     * @return 是否開啟
     */
    public boolean isStockTwAutoStart() {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
            DayOfWeek dayOfWeek = now.getDayOfWeek();
            if (dayOfWeek.getValue() >= DayOfWeek.MONDAY.getValue() && dayOfWeek.getValue() <= DayOfWeek.FRIDAY.getValue()) {
                if (now.getHour() >= 9 && now.getHour() < 14) {
                    return immediatelyUpdateStockTw;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("獲取股票台灣自動更新狀態失敗: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 定期緩存資產前綴樹
     * 每週五凌晨2點
     */
    @Scheduled(cron = "0 0 2 * * 5")
    @Transactional
    public void cacheAssetTrie() {
        try {
            redisService.deleteByPattern("assetTrie");
            assetService.cacheTrieToRedis();
        } catch (Exception e) {
            log.error("緩存資產前綴樹失敗: {}", e.getMessage());
        }
    }
}
