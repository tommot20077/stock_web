package xyz.dowob.stockweb.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.PropertyRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;

@Component
public class SubscribeMethod {
    Logger logger = LoggerFactory.getLogger(SubscribeMethod.class);
    private final SubscribeRepository subscribeRepository;
    private final StockTwRepository stockTwRepository;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired
    public SubscribeMethod(SubscribeRepository subscribeRepository, StockTwRepository stockTwRepository, ApplicationEventPublisher eventPublisher) {
        this.subscribeRepository = subscribeRepository;
        this.stockTwRepository = stockTwRepository;
        this.eventPublisher = eventPublisher;
    }


    @Transactional(rollbackFor = Exception.class)
    public void unsubscribeProperty(Property property, User user) {
        logger.debug("取消訂閱資產ID: " + property.getId());
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetId(user.getId(), property.getAsset().getId()).orElse(null);
        logger.debug("取得用戶資產訂閱表: " + subscribe);
        if (subscribe != null && subscribe.isUserSubscribed()) {
            logger.debug("用戶有此資產訂閱");
            subscribeRepository.delete(subscribe);
            if (property.getAsset() instanceof CryptoTradingPair cryptoTradingPair) {
                if (cryptoTradingPair.checkUserIsSubscriber(user)) {
                    cryptoTradingPair.getSubscribers().remove(user.getId());
                }
            } else if (property.getAsset() instanceof StockTw stockTw) {
                if (stockTw.checkUserIsSubscriber(user)) {
                    removeSubscriberFromStockTw(stockTw, user.getId());//todo 以後可以改成用asset並抓取type來做確認
                }
            } else if (property.getAsset() instanceof Currency) {
                logger.debug("直接刪除subscription");
            }
            else {
                throw new IllegalArgumentException("錯誤的資產類型");
            }
            logger.debug("資產訂閱數量減 1");
        } else {
            logger.debug("用戶沒有此資產訂閱");
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void subscribeProperty(Property property, User user) {
        logger.debug("訂閱資產ID: " + property.getId());
        logger.debug("用戶此項持有資產");;
        Subscribe subscribe = subscribeRepository.findByUserIdAndAssetId(user.getId(), property.getAsset().getId()).orElse(null);
        logger.debug("取得用戶訂閱表: " + subscribe);
        if (subscribe != null) {
            if (!subscribe.isUserSubscribed()) {
                subscribe.setUserSubscribed(true);
                subscribeRepository.save(subscribe);
                logger.debug("訂閱成功");
            }
        } else {
            logger.debug("用戶訂閱不存在，嘗試訂閱");
            subscribe = new Subscribe();
            subscribe.setUser(user);
            subscribe.setAsset(property.getAsset());
            subscribe.setUserSubscribed(true);
            logger.debug("用戶訂閱數量加 1");
            logger.debug("訂閱資產類型: " + property.getAsset().getAssetType());
            if (property.getAsset() instanceof StockTw stockTw) {
                logger.debug("訂閱股票: " + stockTw.getStockCode());
                if (!stockTw.checkUserIsSubscriber(user)) {
                    addSubscriberToStockTw(stockTw, user.getId());
                    logger.debug("用戶訂閱此股票數不為 0，股票訂閱數加 1");
                }
            } else if (property.getAsset() instanceof CryptoTradingPair crypto) {
                logger.debug("訂閱加密貨幣: " + crypto.getBaseAsset());
                subscribe.setChannel("@kline_1m");
                if (!crypto.checkUserIsSubscriber(user)) {
                    crypto.getSubscribers().add(user.getId());//todo 目前只有改股票
                    logger.debug("用戶訂閱此加密貨幣數不為 0，加密貨幣訂閱數加 1");
                }
            } else if (property.getAsset() instanceof Currency currency) {
                logger.debug("訂閱匯率: " + currency.getCurrency() + "-" + user.getPreferredCurrency());
                subscribe.setChannel(user.getPreferredCurrency().getCurrency());
            } else {
                logger.debug("錯誤的資產類型");
            }
            subscribeRepository.save(subscribe);
        }
    }




    private void addSubscriberToStockTw(StockTw stockTw, Long userId) {
        stockTwRepository.addSubscriber(stockTw, userId, eventPublisher);
    }

    private void removeSubscriberFromStockTw(StockTw stockTw, Long userId) {
        stockTwRepository.removeSubscriber(stockTw, userId, eventPublisher);
    }

}

