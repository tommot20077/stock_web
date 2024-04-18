package xyz.dowob.stockweb.Component.Method;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.EventCacheRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EventCacheMethod {
    private final EventCacheRepository eventCacheRepository;
    @Autowired
    public EventCacheMethod(EventCacheRepository eventCacheRepository) {this.eventCacheRepository = eventCacheRepository;}

    public List<EventCache> getEventCacheWithAsset(Asset asset) {
        return eventCacheRepository.findEventCacheByPropertyAsset(asset);
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
