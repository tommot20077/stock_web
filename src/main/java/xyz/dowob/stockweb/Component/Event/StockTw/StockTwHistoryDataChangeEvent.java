package xyz.dowob.stockweb.Component.Event.StockTw;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

@Getter
public class StockTwHistoryDataChangeEvent extends ApplicationEvent {
    private final String addOrRemove;
    private final StockTw stockTw;
    public StockTwHistoryDataChangeEvent(Object source, StockTw stockTw, String addOrRemove) {
        super(source);
        this.stockTw = stockTw;
        this.addOrRemove = addOrRemove;
    }
}
