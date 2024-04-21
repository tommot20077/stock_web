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
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.EventCacheRepository;

import java.math.BigDecimal;
import java.util.List;

import static xyz.dowob.stockweb.Enum.AssetType.*;

@Service
public class EventCacheMethod {
    private final EventCacheRepository eventCacheRepository;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired
    public EventCacheMethod(EventCacheRepository eventCacheRepository, ApplicationEventPublisher applicationEventPublisher) {this.eventCacheRepository = eventCacheRepository;
        this.eventPublisher = applicationEventPublisher;
    }

    Logger logger = LoggerFactory.getLogger(EventCacheMethod.class);

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
                        if (isAddMessage) {
                            logger.debug("重新發布事件: {}", cryptoTradingPair);
                            eventPublisher.publishEvent(new CryptoHistoryDataChangeEvent(this, cryptoTradingPair ,"add"));
                        } else {
                            logger.debug("重新發布事件: {}", cryptoTradingPair);
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



    public List<EventCache> getEventCacheWithAsset(Asset asset) {
        return eventCacheRepository.findEventCacheByPropertyAsset(asset);
    }

    public List<EventCache> getEventCacheWithProperty(Property property) {
        return eventCacheRepository.findEventCacheByProperty(property);
    }


    public void addEventCache(Property property, BigDecimal quantity) {
        EventCache eventCache = new EventCache();
        eventCache.setProperty(property);
        eventCache.setQuantity(quantity);
        eventCacheRepository.save(eventCache);
    }

    public void deleteEventCache(EventCache eventCache) {
        eventCacheRepository.delete(eventCache);
    }

}
