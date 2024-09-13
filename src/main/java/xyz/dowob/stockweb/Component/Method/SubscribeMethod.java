package xyz.dowob.stockweb.Component.Method;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;

import java.util.Set;

/**
 * 這是一個訂閱方法，用於處理用戶訂閱資產。
 *
 * @author yuan
 */
@Component
public class SubscribeMethod {
    private final SubscribeRepository subscribeRepository;

    private final StockTwRepository stockTwRepository;

    private final CryptoRepository cryptoRepository;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 這是一個構造函數，用於注入用戶訂閱資產資料庫、股票資料庫、加密貨幣資料庫和應用程序事件發布者。
     *
     * @param subscribeRepository 用戶訂閱資產資料庫
     * @param stockTwRepository   股票資料庫
     * @param cryptoRepository    加密貨幣資料庫
     * @param eventPublisher      應用程序事件發布者
     */
    public SubscribeMethod(SubscribeRepository subscribeRepository, StockTwRepository stockTwRepository, CryptoRepository cryptoRepository, ApplicationEventPublisher eventPublisher) {
        this.subscribeRepository = subscribeRepository;
        this.stockTwRepository = stockTwRepository;
        this.cryptoRepository = cryptoRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 訂閱資產，分成貨幣匯率和其他匯率
     * 如果是貨幣匯率，不須處理訂閱數量
     * 如果是其他匯率，訂閱數量加 1
     *
     * @param property 資產
     * @param user     用戶
     */
    @Transactional(rollbackFor = Exception.class)
    public void subscribeProperty(Property property, User user) {
        Subscribe subscribe;
        if (property.getAsset().getAssetType() == AssetType.CURRENCY) {
            subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                             property.getAsset().getId(),
                                                                             user.getPreferredCurrency().getCurrency()).orElse(null);
            if (subscribe == null) {
                Currency currency = (Currency) property.getAsset();
                subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                                 user.getPreferredCurrency().getId(),
                                                                                 currency.getCurrency()).orElse(null);
            }
        } else {
            subscribe = subscribeRepository.findByUserIdAndAssetId(user.getId(), property.getAsset().getId()).orElse(null);
        }
        if (subscribe != null) {
            if (!subscribe.isUserSubscribed()) {
                subscribe.setUserSubscribed(true);
            }
            subscribe.setRemoveAble(false);
            subscribeRepository.save(subscribe);
        } else {
            subscribe = new Subscribe();
            subscribe.setUser(user);
            subscribe.setAsset(property.getAsset());
            subscribe.setUserSubscribed(true);
            subscribe.setRemoveAble(false);
            switch (property.getAsset()) {
                case StockTw stockTw -> {
                    if (!stockTw.checkUserIsSubscriber(user)) {
                        addSubscriberToStockTw(stockTw, user.getId());
                    }
                }
                case CryptoTradingPair crypto -> {
                    subscribe.setChannel("@kline_1m");
                    if (!crypto.checkUserIsSubscriber(user)) {
                        addSubscriberToCryptoTradingPair(crypto, user.getId());
                    }
                }
                case Currency currency -> {
                    if (currency == user.getPreferredCurrency()) {
                        return;
                    }
                    subscribe.setChannel(user.getPreferredCurrency().getCurrency());
                }
                case null, default -> throw new RuntimeException(new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_TYPE_NOT_FOUND,
                                                                                     property.getAsset()));
            }
            subscribeRepository.save(subscribe);
        }
    }

    /**
     * 取消訂閱資產, 分成貨幣匯率和其他匯率
     * 如果是貨幣匯率，不須處理訂閱數量
     * 如果是其他匯率，訂閱數量減 1
     *
     * @param property 資產
     * @param user     用戶
     */
    @Transactional(rollbackFor = Exception.class)
    public void unsubscribeProperty(Property property, User user) {
        Subscribe subscribe;
        if (property.getAsset().getAssetType() == AssetType.CURRENCY) {
            subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                             property.getAsset().getId(),
                                                                             user.getPreferredCurrency().getCurrency()).orElse(null);
            if (subscribe == null) {
                Currency currency = (Currency) property.getAsset();
                subscribe = subscribeRepository.findByUserIdAndAssetIdAndChannel(user.getId(),
                                                                                 user.getPreferredCurrency().getId(),
                                                                                 currency.getCurrency()).orElse(null);
            }
        } else {
            subscribe = subscribeRepository.findByUserIdAndAssetId(user.getId(), property.getAsset().getId()).orElse(null);
        }
        if (subscribe != null && subscribe.isUserSubscribed()) {
            subscribeRepository.delete(subscribe);
            switch (property.getAsset()) {
                case CryptoTradingPair crypto -> {
                    if (crypto.checkUserIsSubscriber(user)) {
                        removeSubscriberFromTradingPair(crypto, user.getId());
                    }
                }
                case StockTw stockTw -> {
                    if (stockTw.checkUserIsSubscriber(user)) {
                        removeSubscriberFromStockTw(stockTw, user.getId());
                    }
                }
                case Currency ignored -> {
                }
                case null, default -> throw new RuntimeException(new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_TYPE_NOT_FOUND,
                                                                                     property.getAsset()));
            }
        } else {
        }
    }

    /**
     * 將用戶訂閱加入股票訂閱表，並檢查是否有訂閱
     *
     * @param stockTw 股票
     * @param userId  用戶ID
     */
    public void addSubscriberToStockTw(StockTw stockTw, Long userId) {
        stockTwRepository.addAndCheckSubscriber(stockTw, userId, eventPublisher);
    }

    /**
     * 將用戶訂閱從股票訂閱表移除，並檢查是否有訂閱
     *
     * @param stockTw 股票
     * @param userId  用戶ID
     */
    public void removeSubscriberFromStockTw(StockTw stockTw, Long userId) {
        stockTwRepository.removeAndCheckSubscriber(stockTw, userId, eventPublisher);
    }

    /**
     * 將用戶訂閱加入加密貨幣訂閱表，並檢查是否有訂閱
     *
     * @param cryptoTradingPair 加密貨幣
     * @param userId            用戶ID
     */
    public void addSubscriberToCryptoTradingPair(CryptoTradingPair cryptoTradingPair, Long userId) {
        cryptoRepository.addAndCheckSubscriber(cryptoTradingPair, userId, eventPublisher);
    }

    /**
     * 將用戶訂閱從加密貨幣訂閱表移除，並檢查是否有訂閱
     *
     * @param cryptoTradingPair 加密貨幣
     * @param userId            用戶ID
     */
    public void removeSubscriberFromTradingPair(CryptoTradingPair cryptoTradingPair, Long userId) {
        cryptoRepository.removeAndCheckSubscriber(cryptoTradingPair, userId, eventPublisher);
    }

    /**
     * 檢查用戶訂閱的資產是否有歷史資料, 如果沒有則重新抓取資料
     * 1. 取得用戶訂閱的資產
     * 2. 檢查資產是否有歷史資料
     * 3. 如果沒有歷史資料，重新抓取資料
     * 4. 如果有歷史資料，不須重新抓取資料
     * 5. 如果資產類型錯誤，不須重新抓取資料
     * 6. 如果資產有訂閱但無歷史資料，重新抓取資料
     */
    public void CheckSubscribedAssets() {
        Set<Asset> userSubscribed = subscribeRepository.findAllAsset();
        for (Asset asset : userSubscribed) {
            if (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed()) {
                eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "add"));
            } else if (asset instanceof CryptoTradingPair cryptoTradingPair && !cryptoTradingPair.isHasAnySubscribed()) {
                eventPublisher.publishEvent(new CryptoHistoryDataChangeEvent(this, cryptoTradingPair, "add"));
            } else {
            }
        }
    }
}
