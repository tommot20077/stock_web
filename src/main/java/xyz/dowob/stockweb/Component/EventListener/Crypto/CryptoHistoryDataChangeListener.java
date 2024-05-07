package xyz.dowob.stockweb.Component.EventListener.Crypto;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.PropertyUpdateEvent;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Service.Common.ProgressTrackerService;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

/**
 * @author yuan
 */
@Component
public class CryptoHistoryDataChangeListener implements ApplicationListener<CryptoHistoryDataChangeEvent> {
    private final Logger logger = LoggerFactory.getLogger(CryptoHistoryDataChangeListener.class);

    private final CryptoService cryptoService;

    private final ProgressTrackerService progressTrackerService;

    private final RetryTemplate retryTemplate;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CryptoHistoryDataChangeListener(CryptoService cryptoService, ProgressTrackerService progressTrackerService, RetryTemplate retryTemplate, ApplicationEventPublisher eventPublisher) {
        this.cryptoService = cryptoService;
        this.progressTrackerService = progressTrackerService;
        this.retryTemplate = retryTemplate;
        this.eventPublisher = eventPublisher;
    }


    /**
     * 當CryptoHistoryDataChangeEvent事件發生時，此方法將被調用。
     * 如果事件的addOrRemove屬性為"add"，則進行以下操作：
     * 檢查該虛擬貨幣是否已在進行中，如果是，則不處理。
     * 追蹤該虛擬貨幣的歷史價格。
     * 發布PropertyUpdateEvent事件。
     * 如果事件的addOrRemove屬性為"remove"，則進行以下操作：
     * 移除該虛擬貨幣的歷史資料。
     * 發布PropertyUpdateEvent事件。
     * 如果事件的addOrRemove屬性既不是"add"也不是"remove"，則不處理。
     *
     * @param event CryptoHistoryDataChangeEvent事件對象
     *
     * @throws RuntimeException 如果追蹤歷史資料失敗或操作失敗，則拋出異常
     */
    @Override
    public void onApplicationEvent(
            @NotNull CryptoHistoryDataChangeEvent event) {
        logger.info("收到虛擬貨幣資料變更通知");
        if ("add".equals(event.getAddOrRemove())) {
            if (progressTrackerService.getAllProgressInfo()
                                      .stream()
                                      .anyMatch(x -> x.getTaskName().equals(event.getCryptoTradingPair().getTradingPair()))) {
                logger.debug("該虛擬貨幣已經在執行中，不處理");
                return;
            }
            logger.debug("新增歷史資料");
            try {
                cryptoService.trackCryptoHistoryPrices(event.getCryptoTradingPair());
                logger.debug("發布更新用戶資產事件");
                eventPublisher.publishEvent(new PropertyUpdateEvent(this));
            } catch (Exception e) {
                throw new RuntimeException("追蹤歷史資料失敗: " + e.getMessage());
            }
        } else if ("remove".equals(event.getAddOrRemove())) {
            try {
                retryTemplate.doWithRetry(() -> {
                    logger.warn("移除{}歷史資料", event.getCryptoTradingPair().getTradingPair());
                    cryptoService.removeCryptoPricesDataByTradingPair(event.getCryptoTradingPair().getTradingPair());
                });
                logger.debug("發布更新用戶資產事件");
                eventPublisher.publishEvent(new PropertyUpdateEvent(this));
            } catch (RetryException e) {
                Exception lastException = e.getLastException();
                logger.error("重試失敗，最後一次錯誤信息：" + lastException.getMessage(), lastException);
                throw new RuntimeException("操作失敗: " + lastException.getMessage(), lastException);
            }
        } else {
            logger.warn("未知動作，不處理");
        }
    }
}
