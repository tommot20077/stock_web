package xyz.dowob.stockweb.Component.Event.StockTw;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

/**
 * 此類別代表當股票訂閱者變更時發布的事件。
 * 此事件包含一個StockTw對象，代表發生變更的股票。
 *
 * @author yuan
 */
@Getter
public class StockTwSubscriberChangeEvent extends ApplicationEvent {
    private final StockTw stockTw;

    /**
     * StockTwSubscriberChangeEvent類別的構造函數。
     *
     * @param source  事件最初發生的對象。
     * @param stockTw 發生變更的股票。
     */
    public StockTwSubscriberChangeEvent(Object source, StockTw stockTw) {
        super(source);
        this.stockTw = stockTw;
    }
}