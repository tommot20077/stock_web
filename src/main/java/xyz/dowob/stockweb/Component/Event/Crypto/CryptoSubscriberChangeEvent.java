package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;


@Getter
public class CryptoSubscriberChangeEvent
        extends ApplicationEvent {

    private final CryptoTradingPair cryptoTradingPair;

    public CryptoSubscriberChangeEvent(Object source, CryptoTradingPair cryptoTradingPair) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
    }
}

