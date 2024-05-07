package xyz.dowob.stockweb.Component.EventListener.Crypto;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoSubscriberChangeEvent;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

/**
 * @author yuan
 */
@Component
public class CryptoSubscriberChangeListener implements ApplicationListener<CryptoSubscriberChangeEvent> {
    Logger logger = LoggerFactory.getLogger(CryptoSubscriberChangeListener.class);

    private final CryptoService cryptoService;

    @Autowired
    public CryptoSubscriberChangeListener(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * 當CryptoSubscriberChangeEvent事件發生時，此方法將被調用。
     * 如果虛擬貨幣服務的連線是開啟的，則進行以下操作：
     * 關閉現有的連線。
     * 開啟新的連線。
     * 如果虛擬貨幣服務的連線是關閉的，則不進行任何操作。
     *
     * @param event CryptoSubscriberChangeEvent事件對象
     */
    @Override
    public void onApplicationEvent(
            @NotNull CryptoSubscriberChangeEvent event) {
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
