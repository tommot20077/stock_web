package xyz.dowob.stockweb.Component.EventListener.Asset;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.AssetHistoryDataFetchCompleteEvent;
import xyz.dowob.stockweb.Component.Method.EventCacheMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Service.Common.Property.PropertyInfluxService;

import java.math.BigDecimal;
import java.util.List;


@Component
public class AssetHistoryDataFetchCompleteListener implements ApplicationListener<AssetHistoryDataFetchCompleteEvent> {
    private final PropertyInfluxService propertyInfluxService;
    private final EventCacheMethod eventCacheMethod;
    private final RetryTemplate retryTemplate;

    Logger logger = LoggerFactory.getLogger(AssetHistoryDataFetchCompleteListener.class);
    @Autowired
    public AssetHistoryDataFetchCompleteListener(PropertyInfluxService propertyInfluxService, EventCacheMethod eventCacheMethod, RetryTemplate retryTemplate) {
        this.propertyInfluxService = propertyInfluxService;
        this.eventCacheMethod = eventCacheMethod;

        this.retryTemplate = retryTemplate;
    }

    @Override
    public void onApplicationEvent(@NotNull AssetHistoryDataFetchCompleteEvent event) {
        try {
            retryTemplate.doWithRetry(() -> {
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
                        logger.info("取得{}歷史資料成功", event.getAsset().getId());
                    } catch (Exception e) {
                        logger.error("寫入資料失敗");
                        throw new Exception("寫入資料失敗");
                    }
                } else {
                    logger.error("取得資料失敗");
                    throw new Exception("取得資料失敗");
                }
            });
        } catch (RetryException e) {
            Exception lastException = e.getLastException();
            logger.error("重試失敗，最後一次錯誤信息：" + lastException.getMessage(), lastException);
            throw new RuntimeException("操作失敗: " + lastException.getMessage(), lastException);
        }

    }
}
