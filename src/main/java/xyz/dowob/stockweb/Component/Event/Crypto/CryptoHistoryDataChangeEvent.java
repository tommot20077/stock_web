package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.checkerframework.checker.units.qual.A;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
@Getter
public class CryptoHistoryDataChangeEvent extends ApplicationEvent {
    private final CryptoTradingPair cryptoTradingPair;
    private final String addOrRemove;

    public CryptoHistoryDataChangeEvent(Object source, CryptoTradingPair cryptoTradingPair, String addOrRemove) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
        this.addOrRemove = addOrRemove;
    }
}
