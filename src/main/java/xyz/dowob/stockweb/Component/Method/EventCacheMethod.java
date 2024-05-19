package xyz.dowob.stockweb.Component.Method;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Repository.Common.EventCacheRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 這是一個事件緩存方法，用於處理伺服器事件緩存。
 * 目的是在當伺服器可能因為某些原因而中斷時，保留事件緩存，以便在伺服器重新啟動時重新發布事件。
 *
 * @author yuan
 */
@Service
public class EventCacheMethod {
    private final EventCacheRepository eventCacheRepository;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 這是一個構造函數，用於注入事件緩存資料庫和應用程序事件發布者。
     *
     * @param eventCacheRepository      事件緩存資料庫
     * @param applicationEventPublisher 應用程序事件發布者
     */
    @Autowired
    public EventCacheMethod(EventCacheRepository eventCacheRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.eventCacheRepository = eventCacheRepository;
        this.eventPublisher = applicationEventPublisher;
    }

    Logger logger = LoggerFactory.getLogger(EventCacheMethod.class);

    /**
     * 初始化事件緩存, 檢查是否有未完成事件
     * 1. 如果有未完成事件，依照事件資產類型重新發布事件
     * 2. 如果事件已完成，刪除事件
     * 3. 如果事件資產類型錯誤，刪除事件
     */

    @PostConstruct
    public void init() {
        logger.info("檢查事件緩存");
        List<EventCache> eventCachesList = eventCacheRepository.findAllEventCachesProperty();
        if (!eventCachesList.isEmpty()) {
            logger.debug("有未完成事件");
            eventCachesList.forEach(eventCache -> {
                boolean isAddMessage;

                if (eventCache.isComplete()) {
                    logger.debug("資產:{}已完成: {}，刪除資產事件", eventCache.getProperty(), eventCache.getProperty().getAsset());
                    deleteEventCache(eventCache);
                } else {
                    isAddMessage = eventCache.getQuantity().compareTo(BigDecimal.ZERO) >= 0;
                    if (eventCache.getProperty().getAsset() instanceof CryptoTradingPair cryptoTradingPair) {
                        logger.debug("重新發布事件: {}", cryptoTradingPair);
                        if (isAddMessage) {
                            eventPublisher.publishEvent(new CryptoHistoryDataChangeEvent(this, cryptoTradingPair, "add"));
                        } else {
                            eventPublisher.publishEvent(new CryptoHistoryDataChangeEvent(this, cryptoTradingPair, "remove"));
                        }
                    } else if (eventCache.getProperty().getAsset() instanceof StockTw stockTw) {
                        if (isAddMessage) {
                            eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "add"));
                        } else {
                            eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "remove"));
                        }
                    } else {
                        logger.error("資產:{} ，類型錯誤: {} ，刪除資產事件", eventCache.getProperty(), eventCache.getProperty().getAsset());
                        deleteEventCache(eventCache);
                    }
                }
            });
        }
        logger.info("檢查事件緩存完成");
    }


    /**
     * 根據資產獲取事件緩存
     *
     * @param asset 資產
     *
     * @return 事件緩存列表
     */
    public List<EventCache> getEventCacheWithAsset(Asset asset) {
        return eventCacheRepository.findEventCacheByPropertyAsset(asset);
    }

    /**
     * 根據財產獲取事件緩存
     *
     * @param property 用戶財產
     *
     * @return 事件緩存列表
     */
    public List<EventCache> getEventCacheWithProperty(Property property) {
        return eventCacheRepository.findEventCacheByProperty(property);
    }


    /**
     * 添加事件緩存
     *
     * @param property 財產
     * @param quantity 數量
     */
    public void addEventCache(Property property, BigDecimal quantity) {
        EventCache eventCache = new EventCache();
        eventCache.setProperty(property);
        eventCache.setQuantity(quantity);
        eventCacheRepository.save(eventCache);
    }

    /**
     * 刪除事件緩存
     *
     * @param eventCache 事件緩存
     */
    public void deleteEventCache(EventCache eventCache) {
        eventCacheRepository.delete(eventCache);
    }

}
