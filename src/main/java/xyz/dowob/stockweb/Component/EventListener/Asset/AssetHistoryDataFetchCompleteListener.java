package xyz.dowob.stockweb.Component.EventListener.Asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.AssetHistoryDataFetchCompleteEvent;
import xyz.dowob.stockweb.Component.Method.EventCacheMethod;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Service.Common.Property.PropertyInfluxService;

import java.math.BigDecimal;
import java.util.List;


@Component
public class AssetHistoryDataFetchCompleteListener implements ApplicationListener<AssetHistoryDataFetchCompleteEvent> {
    private final PropertyInfluxService propertyInfluxService;
    private final EventCacheMethod eventCacheMethod;
    Logger logger = LoggerFactory.getLogger(AssetHistoryDataFetchCompleteListener.class);
    @Autowired
    public AssetHistoryDataFetchCompleteListener(PropertyInfluxService propertyInfluxService, EventCacheMethod eventCacheMethod) {
        this.propertyInfluxService = propertyInfluxService;
        this.eventCacheMethod = eventCacheMethod;
    }

    @Override
    public void onApplicationEvent(AssetHistoryDataFetchCompleteEvent event) {
        if (event.getSuccess()) {
            try {
                logger.debug("取得資料成功");
                List<EventCache> assetEventCaches = eventCacheMethod.getEventCacheWithAsset(event.getAsset());
                if (!assetEventCaches.isEmpty()) {
                    for (EventCache eventCache : assetEventCaches) {
                        BigDecimal netFlow = propertyInfluxService.calculateNetFlow(
                                eventCache.getQuantity(),
                                eventCache.getProperty().getAsset()
                        );
                        propertyInfluxService.writeNetFlowToInflux(netFlow, eventCache.getProperty().getUser());
                        eventCacheMethod.deleteEventCache(eventCache);
                    }
                }
                logger.debug("寫入資料成功");
            } catch (Exception e) {
                logger.debug("寫入資料失敗");
            }
        } else {
            logger.debug("取得資料失敗");
        }
    }
}
