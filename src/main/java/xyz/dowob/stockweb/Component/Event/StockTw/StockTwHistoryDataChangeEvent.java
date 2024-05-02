package xyz.dowob.stockweb.Component.Event.StockTw;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

/**
 * 此類別代表當股票歷史數據變更時發布的事件。
 * 此事件包含一個StockTw對象和一個表示添加或移除的字符串。
 *
 * @author yuan
 */
@Getter
public class StockTwHistoryDataChangeEvent extends ApplicationEvent {
    private final String addOrRemove;
    private final StockTw stockTw;

    /**
     * StockTwHistoryDataChangeEvent類別的構造函數。
     *
     * @param source      事件最初發生的對象。
     * @param stockTw     股票對象。
     * @param addOrRemove 表示添加或移除的字符串。
     */
    public StockTwHistoryDataChangeEvent(Object source, StockTw stockTw, String addOrRemove) {
        super(source);
        this.stockTw = stockTw;
        this.addOrRemove = addOrRemove;
    }
}