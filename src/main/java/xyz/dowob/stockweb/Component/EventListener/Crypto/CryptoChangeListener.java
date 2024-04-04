package xyz.dowob.stockweb.Component.EventListener.Crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Crontab;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoChangeEvent;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwChangeEvent;
import xyz.dowob.stockweb.Component.Handler.CryptoWebSocketHandler;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;


@Component
public class CryptoChangeListener implements ApplicationListener<CryptoChangeEvent> {
    private final Logger logger = LoggerFactory.getLogger(CryptoChangeListener.class);
    private final CryptoService cryptoService;
    @Autowired
    public CryptoChangeListener(CryptoService cryptoService) {
        this.cryptoService = cryptoService;}

    @Override
    public void onApplicationEvent(@NotNull CryptoChangeEvent event) {
        logger.debug("收到虛擬貨幣訂閱變更");
        if (cryptoService.isConnectionOpen()) {
            logger.debug("重新訂閱虛擬貨幣，關閉現有連線");
            cryptoService.closeConnection();
            cryptoService.openConnection();
        } else {
            logger.debug("目前沒有活躍的連線，不處理");
        }
    }

}
