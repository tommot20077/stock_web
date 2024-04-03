package xyz.dowob.stockweb.Component.EventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Crontab;
import xyz.dowob.stockweb.Component.Event.StockChangeEvent;


@Component
public class StockChangeListener implements ApplicationListener<StockChangeEvent> {
    private final Logger logger = LoggerFactory.getLogger(StockChangeListener.class);
    private final Crontab crontab;
    @Autowired
    public StockChangeListener(Crontab crontab) {this.crontab = crontab;}

    @Override
    public void onApplicationEvent(@NotNull StockChangeEvent event) {
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
