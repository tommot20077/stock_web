package xyz.dowob.stockweb.Component.EventListener.StockTw;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Crontab;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwChangeEvent;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;


@Component
public class StockTwChangeListener implements ApplicationListener<StockTwChangeEvent> {
    private final Logger logger = LoggerFactory.getLogger(StockTwChangeListener.class);
    private final Crontab crontab;
    @Autowired
    public StockTwChangeListener(Crontab crontab) {
        this.crontab = crontab;
    }

    @Override
    public void onApplicationEvent(@NotNull StockTwChangeEvent event) {
        logger.debug("收到股票訂閱變更");
        try {
            crontab.checkSubscriptions();
            crontab.trackPricesPeriodically();
        } catch (JsonProcessingException e) {
            logger.error("Json轉換錯誤", e);
            throw new RuntimeException(e);
        }
    }

}
