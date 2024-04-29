package xyz.dowob.stockweb.Component.Event.StockTw;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

/**
 * @author yuan
 */
@Getter
public class StockTwSubscriberChangeEvent extends ApplicationEvent {
    private final StockTw stockTw;

    public StockTwSubscriberChangeEvent(Object source, StockTw stockTw) {
        super(source);
        this.stockTw = stockTw;
    }
}

