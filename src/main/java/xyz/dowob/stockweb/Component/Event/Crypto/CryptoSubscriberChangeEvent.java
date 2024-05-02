package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;

/**
 * 此類別代表當加密貨幣訂閱者變更時發布的事件。
 * 此事件包含一個CryptoTradingPair對象，代表發生變更的交易對。
 *
 * @author yuan
 */
@Getter
public class CryptoSubscriberChangeEvent extends ApplicationEvent {
    private final CryptoTradingPair cryptoTradingPair;

    /**
     * CryptoSubscriberChangeEvent類別的構造函數。
     *
     * @param source            事件最初發生的對象。
     * @param cryptoTradingPair 發生變更的交易對。
     */
    public CryptoSubscriberChangeEvent(Object source, CryptoTradingPair cryptoTradingPair) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
    }
}