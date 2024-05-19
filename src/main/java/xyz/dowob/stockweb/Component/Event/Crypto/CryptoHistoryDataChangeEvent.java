package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;

/**
 * 當加密貨幣歷史數據變更時發布的事件。
 * 繼承自ApplicationEvent。
 * 此事件包含一個CryptoTradingPair對象和一個表示添加或移除的字符串。
 * 用於通知CryptoHistoryDataChangeListener進行後續操作。
 * CryptoHistoryDataChangeListener根據addOrRemove的值對CryptoTradingPair進行添加或移除操作。
 *
 * @author yuan
 */
@Getter
public class CryptoHistoryDataChangeEvent extends ApplicationEvent {
    private final CryptoTradingPair cryptoTradingPair;

    private final String addOrRemove;

    /**
     * CryptoHistoryDataChangeEvent類別的構造函數。
     *
     * @param source            事件最初發生的對象。
     * @param cryptoTradingPair 交易對象。
     * @param addOrRemove       表示添加或移除的字符串,
     *                          如果為"add"，則表示添加；
     *                          如果為"remove"，則表示移除。
     *                          其他情況下，不進行任何操作。
     */
    public CryptoHistoryDataChangeEvent(@NonNull Object source, @NonNull CryptoTradingPair cryptoTradingPair, @NonNull String addOrRemove) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
        this.addOrRemove = addOrRemove;
    }
}