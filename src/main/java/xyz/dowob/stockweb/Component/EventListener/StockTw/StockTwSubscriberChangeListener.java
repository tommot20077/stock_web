package xyz.dowob.stockweb.Component.EventListener.StockTw;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwSubscriberChangeEvent;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;

/**
 * 當StockTwSubscriberChangeEvent事件發生時，此類別將被調用。
 * 實現ApplicationListener接口。並以StockTwSubscriberChangeEvent作為參數。
 * 此類別用於監聽StockTwSubscriberChangeEvent事件，並根據事件的發生進行相應操作。
 * @author yuan
 */
@Component
public class StockTwSubscriberChangeListener implements ApplicationListener<StockTwSubscriberChangeEvent> {
    Logger logger = LoggerFactory.getLogger(StockTwSubscriberChangeListener.class);

    private final CrontabMethod crontabMethod;

    @Autowired
    public StockTwSubscriberChangeListener(CrontabMethod crontabMethod) {
        this.crontabMethod = crontabMethod;
    }

    /**
     * 此方法會在收到股票訂閱變更事件時被調用。
     *
     * @param event 股票訂閱變更事件對象。
     *              方法首先記錄一條信息，表示收到了股票訂閱變更事件。然後，嘗試調用 crontabMethod 的 checkSubscriptions 和 trackStockTwPricesPeriodically 方法來檢查訂閱並定期追蹤股票價格。
     * @throws RuntimeException 如果在 JSON 轉換過程中出現異常，則拋出異常
     */
    @Override
    public void onApplicationEvent(
            @NonNull StockTwSubscriberChangeEvent event) {
        logger.info("收到股票訂閱變更");
        try {
            crontabMethod.checkSubscriptions();
            crontabMethod.trackStockTwPricesPeriodically();
        } catch (JsonProcessingException e) {
            logger.error("Json轉換錯誤", e);
            throw new RuntimeException(e);
        }
    }
}
