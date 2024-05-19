package xyz.dowob.stockweb.Component.Event.StockTw;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

/**
 * 此類別代表當股票歷史數據變更時發布的事件。
 * 繼承自ApplicationEvent。
 * 此事件包含一個StockTw對象和一個表示添加或移除的字符串。
 * 用於通知StockTwHistoryDataChangeListener進行後續操作。
 * StockTwHistoryDataChangeListener根據addOrRemove的值對StockTw對象進行添加或移除操作。
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
     * @param addOrRemove 表示添加或移除的字符串,
     *                    如果為"add"，則表示添加；
     *                    如果為"remove"，則表示移除。
     *                    其他情況下，不進行任何操作。
     */
    public StockTwHistoryDataChangeEvent(@NonNull Object source, @NonNull StockTw stockTw, @NonNull String addOrRemove) {
        super(source);
        this.stockTw = stockTw;
        this.addOrRemove = addOrRemove;
    }
}