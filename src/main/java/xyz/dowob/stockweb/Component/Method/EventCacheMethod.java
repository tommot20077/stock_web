package xyz.dowob.stockweb.Component.Method;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.EventCache;
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

    /**
     * 這是一個構造函數，用於注入事件緩存資料庫和應用程序事件發布者。
     *
     * @param eventCacheRepository      事件緩存資料庫
     */
    @Autowired
    public EventCacheMethod(EventCacheRepository eventCacheRepository) {
        this.eventCacheRepository = eventCacheRepository;
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
