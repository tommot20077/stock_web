package xyz.dowob.stockweb.Component.EventListener.Asset;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.AssetHistoryDataFetchCompleteEvent;
import xyz.dowob.stockweb.Component.Event.Asset.PropertyUpdateEvent;
import xyz.dowob.stockweb.Component.Method.EventCacheMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Service.Common.Property.PropertyInfluxService;

import java.math.BigDecimal;
import java.util.List;


/**
 * 當AssetHistoryDataFetchCompleteEvent事件發生時，此類別將被調用。
 * 實現ApplicationListener接口。並以AssetHistoryDataFetchCompleteEvent作為參數。
 *
 * @author yuan
 */
@Component
public class AssetHistoryDataFetchCompleteListener implements ApplicationListener<AssetHistoryDataFetchCompleteEvent> {
    private final PropertyInfluxService propertyInfluxService;

    private final EventCacheMethod eventCacheMethod;

    private final RetryTemplate retryTemplate;

    private final ApplicationEventPublisher eventPublisher;

    Logger logger = LoggerFactory.getLogger(AssetHistoryDataFetchCompleteListener.class);

    /**
     * AssetHistoryDataFetchCompleteListener類別的構造函數。
     *
     * @param propertyInfluxService influx資產相關服務方法
     * @param eventCacheMethod      伺服器事件緩存相關方法
     * @param retryTemplate         重試模板
     * @param eventPublisher        事件發布者
     */
    @Autowired
    public AssetHistoryDataFetchCompleteListener(PropertyInfluxService propertyInfluxService, EventCacheMethod eventCacheMethod, RetryTemplate retryTemplate, ApplicationEventPublisher eventPublisher) {
        this.propertyInfluxService = propertyInfluxService;
        this.eventCacheMethod = eventCacheMethod;
        this.retryTemplate = retryTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 當AssetHistoryDataFetchCompleteEvent事件發生時，此方法將被調用。
     * 如果事件成功，則進行以下操作：
     * 1. 從事件中獲取資產並獲取相關的事件緩存。
     * 2. 如果事件緩存不為空，則遍歷事件緩存，計算淨流量並寫入Influx，然後刪除事件緩存。
     * 3. 發布PropertyUpdateEvent事件。
     *
     * @param event AssetHistoryDataFetchCompleteEvent事件對象
     *
     * @throws RuntimeException 如果重試失敗，則拋出異常
     */
    @Override
    public void onApplicationEvent(
            @NonNull AssetHistoryDataFetchCompleteEvent event) {
        try {
            retryTemplate.doWithRetry(() -> {
                if (event.getSuccess()) {
                    try {
                        logger.debug("取得資料成功");
                        List<EventCache> assetEventCaches = eventCacheMethod.getEventCacheWithAsset(event.getAsset());
                        if (!assetEventCaches.isEmpty()) {
                            for (EventCache eventCache : assetEventCaches) {
                                BigDecimal netFlow = propertyInfluxService.calculateNetFlow(eventCache.getQuantity(),
                                                                                            eventCache.getProperty().getAsset());
                                propertyInfluxService.writeNetFlowToInflux(netFlow, eventCache.getProperty().getUser());
                                eventCacheMethod.deleteEventCache(eventCache);
                            }
                        }
                        logger.debug("寫入資料成功");
                        logger.info("取得{}歷史資料成功", event.getAsset().getId());

                        logger.debug("發布更新用戶資產事件");
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this));
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
            logger.error("重試失敗，最後一次錯誤信息：{}", lastException.getMessage(), lastException);
            throw new RuntimeException("操作失敗: " + lastException.getMessage(), lastException);
        }
    }
}
