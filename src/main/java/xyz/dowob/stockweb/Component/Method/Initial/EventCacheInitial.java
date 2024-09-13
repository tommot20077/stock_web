package xyz.dowob.stockweb.Component.Method.Initial;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
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
@Log4j2
@Component
public class EventCacheInitial {
    private final EventCacheRepository eventCacheRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final EventCacheMethod eventCacheMethod;

    /**
     * 這是一個構造函數，用於注入事件緩存資料庫和應用程序事件發布者。
     *
     * @param eventCacheRepository      事件緩存資料庫
     * @param applicationEventPublisher 應用程序事件發布者
     */
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
        try {
            List<EventCache> eventCachesList = eventCacheRepository.findAllEventCachesProperty();
            if (!eventCachesList.isEmpty()) {
                eventCachesList.forEach(eventCache -> {
                    boolean isAddMessage;
                    if (eventCache.isComplete()) {
                        eventCacheMethod.deleteEventCache(eventCache);
                    } else {
                        isAddMessage = eventCache.getQuantity().compareTo(BigDecimal.ZERO) >= 0;
                        if (eventCache.getProperty().getAsset() instanceof CryptoTradingPair cryptoTradingPair) {
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
                            eventCacheMethod.deleteEventCache(eventCache);
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }
}
