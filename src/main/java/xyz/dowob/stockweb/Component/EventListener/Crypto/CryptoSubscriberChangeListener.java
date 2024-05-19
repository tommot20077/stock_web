package xyz.dowob.stockweb.Component.EventListener.Crypto;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoSubscriberChangeEvent;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

/**
 * 當CryptoSubscriberChangeEvent事件發生時，此類別將被調用。
 * 實現ApplicationListener接口。並以CryptoSubscriberChangeEvent作為參數。
 * 此類別用於監聽CryptoSubscriberChangeEvent事件，並根據事件的發生進行相應操作。
 *
 * @author yuan
 */
@Component
public class CryptoSubscriberChangeListener implements ApplicationListener<CryptoSubscriberChangeEvent> {
    Logger logger = LoggerFactory.getLogger(CryptoSubscriberChangeListener.class);

    private final CryptoService cryptoService;

    /**
     * CryptoSubscriberChangeListener類別的構造函數。
     *
     * @param cryptoService 加密貨幣相關服務方法
     */
    @Autowired
    public CryptoSubscriberChangeListener(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * 當CryptoSubscriberChangeEvent事件發生時，此方法將被調用。
     * 如果虛擬貨幣服務的連線是開啟的，則進行以下操作：
     * 1.關閉現有的連線。
     * 2.開啟新的連線。
     * 如果虛擬貨幣服務的連線是關閉的，則不進行任何操作。
     *
     * @param event CryptoSubscriberChangeEvent事件對象
     */
    @Override
    public void onApplicationEvent(
            @NonNull CryptoSubscriberChangeEvent event) {
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
