package xyz.dowob.stockweb.Component.Event;

import xyz.dowob.stockweb.Model.Stock.StockTw;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


@Getter
public class StockChangeEvent extends ApplicationEvent {

    private final StockTw stockTw;
    public StockChangeEvent(Object source, StockTw stockTw) {
        super(source);
        this.stockTw = stockTw;
    }
}

