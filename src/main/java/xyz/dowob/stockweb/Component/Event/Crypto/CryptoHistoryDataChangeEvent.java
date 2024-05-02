package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;

/**
 * 當加密貨幣歷史數據變更時發布的事件。
 * 此事件包含一個CryptoTradingPair對象和一個表示添加或移除的字符串。
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
     * @param addOrRemove       表示添加或移除的字符串。
     */
    public CryptoHistoryDataChangeEvent(Object source, CryptoTradingPair cryptoTradingPair, String addOrRemove) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
        this.addOrRemove = addOrRemove;
    }
}