package xyz.dowob.stockweb.Component.EventListener.Crypto;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoSubscriberChangeEvent;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;


@Component
public class CryptoSubscriberChangeListener
        implements ApplicationListener<CryptoSubscriberChangeEvent> {
    Logger logger = LoggerFactory.getLogger(CryptoSubscriberChangeListener.class);
    private final CryptoService cryptoService;
    @Autowired
    public CryptoSubscriberChangeListener(CryptoService cryptoService) {
        this.cryptoService = cryptoService;}

    @Override
    public void onApplicationEvent(@NotNull CryptoSubscriberChangeEvent event) {
        logger.info("收到虛擬貨幣訂閱變更");
        if (cryptoService.isConnectionOpen()) {
            logger.info("重新訂閱虛擬貨幣，關閉現有連線");
            cryptoService.closeConnection();
            cryptoService.openConnection();
        } else {
            logger.debug("目前沒有活躍的連線，不處理");
        }
    }
}
