package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;

/**
 * 此類別代表當加密貨幣訂閱者變更時發布的事件。
 * 繼承自ApplicationEvent。
 * 此事件包含一個CryptoTradingPair對象，代表發生變更的交易對。
 * 用於通知CryptoSubscriberChangeListener進行後續操作。
 * CryptoSubscriberChangeListener根據CryptoTradingPair對象進行訂閱或取消訂閱操作。
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
    public CryptoSubscriberChangeEvent(@NonNull Object source, @NonNull CryptoTradingPair cryptoTradingPair) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
    }
}