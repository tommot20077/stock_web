package xyz.dowob.stockweb.Component.Event.Crypto;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;


@Getter
public class CryptoChangeEvent extends ApplicationEvent {

    private final CryptoTradingPair cryptoTradingPair;
    public CryptoChangeEvent(Object source, CryptoTradingPair cryptoTradingPair) {
        super(source);
        this.cryptoTradingPair = cryptoTradingPair;
    }
}

