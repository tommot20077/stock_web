package xyz.dowob.stockweb.Component.Method.Initial;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Method.EventCacheMethod;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Repository.Common.EventCacheRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 這是一個初始化類，用於在程式啟動時加載確認事件緩存。
 *
 * @author yuan
 */
@Component
public class EventCacheInitial {
    private final EventCacheRepository eventCacheRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final EventCacheMethod eventCacheMethod;

    Logger logger = LoggerFactory.getLogger(EventCacheInitial.class);

    /**
     * 這是一個構造函數，用於注入事件緩存資料庫和應用程序事件發布者。
     *
     * @param eventCacheRepository      事件緩存資料庫
     * @param applicationEventPublisher 應用程序事件發布者
     */
    @Autowired
    public EventCacheInitial(EventCacheRepository eventCacheRepository, ApplicationEventPublisher applicationEventPublisher, EventCacheMethod eventCacheMethod) {
        this.eventCacheRepository = eventCacheRepository;
        this.eventPublisher = applicationEventPublisher;
        this.eventCacheMethod = eventCacheMethod;
    }

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
                    eventCacheMethod.deleteEventCache(eventCache);
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
                        eventCacheMethod.deleteEventCache(eventCache);
                    }
                }
            });
        }
        logger.info("檢查事件緩存完成");
    }
}
