package xyz.dowob.stockweb.Component.Event.StockTw;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

/**
 * 此類別代表當股票訂閱者變更時發布的事件。
 * 繼承自ApplicationEvent。
 * 此事件包含一個StockTw對象，代表發生變更的股票。
 * 用於通知StockTwSubscriberChangeListener進行後續操作。
 * StockTwSubscriberChangeListener根據StockTw對象進行訂閱或取消訂閱操作。
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
    public StockTwSubscriberChangeEvent(@NonNull Object source, @NonNull StockTw stockTw) {
        super(source);
        this.stockTw = stockTw;
    }
}