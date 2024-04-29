package xyz.dowob.stockweb.Component.EventListener.StockTw;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwSubscriberChangeEvent;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;

/**
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

    @Override
    public void onApplicationEvent(
            @NotNull StockTwSubscriberChangeEvent event) {
        logger.info("收到股票訂閱變更");
        try {
            crontabMethod.checkSubscriptions();
            crontabMethod.trackPricesPeriodically();
        } catch (JsonProcessingException e) {
            logger.error("Json轉換錯誤", e);
            throw new RuntimeException(e);
        }
    }
}
