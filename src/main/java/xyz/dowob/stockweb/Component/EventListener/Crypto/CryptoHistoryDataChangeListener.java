package xyz.dowob.stockweb.Component.EventListener.Crypto;

import lombok.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.PropertyUpdateEvent;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Exception.ServiceExceptions;
import xyz.dowob.stockweb.Service.Common.ProgressTrackerService;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

import static xyz.dowob.stockweb.Exception.ServiceExceptions.ErrorEnum.TRACK_CRYPTO_HISTORY_ERROR;

/**
 * 當CryptoHistoryDataChangeEvent事件發生時，此類別將被調用。
 * 實現ApplicationListener接口。並以CryptoHistoryDataChangeEvent作為參數。
 * 此類別用於監聽CryptoHistoryDataChangeEvent事件，並根據事件的addOrRemove屬性執行相應操作。
 *
 * @author yuan
 */
@Component
public class CryptoHistoryDataChangeListener implements ApplicationListener<CryptoHistoryDataChangeEvent> {
    private final CryptoService cryptoService;

    private final ProgressTrackerService progressTrackerService;

    private final RetryTemplate retryTemplate;

    private final ApplicationEventPublisher eventPublisher;

    private final CrontabMethod crontabMethod;

    /**
     * CryptoHistoryDataChangeListener類別的構造函數。
     *
     * @param cryptoService          加密貨幣相關服務方法
     * @param progressTrackerService 進度追蹤相關服務方法
     * @param retryTemplate          重試模板
     * @param eventPublisher         事件發布者
     * @param crontabMethod          定時任務相關方法
     */
    public CryptoHistoryDataChangeListener(CryptoService cryptoService, ProgressTrackerService progressTrackerService, RetryTemplate retryTemplate, ApplicationEventPublisher eventPublisher, CrontabMethod crontabMethod) {
        this.cryptoService = cryptoService;
        this.progressTrackerService = progressTrackerService;
        this.retryTemplate = retryTemplate;
        this.eventPublisher = eventPublisher;
        this.crontabMethod = crontabMethod;
    }

    /**
     * 當CryptoHistoryDataChangeEvent事件發生時，此方法將被調用。
     * 如果事件的addOrRemove屬性為"add"，則進行以下操作：
     * 1.檢查該虛擬貨幣是否已在進行中，如果是，則不處理。
     * 2.追蹤該虛擬貨幣的歷史價格。
     * 3.調用 crontabMethod 的 cacheAssetTrie 方法重新構建資產前綴樹。
     * 4.發布PropertyUpdateEvent事件。
     * <p>
     * 如果事件的addOrRemove屬性為"remove"，則進行以下操作：
     * 1.移除該虛擬貨幣的歷史資料。
     * 2.調用 crontabMethod 的 cacheAssetTrie 方法重新構建資產前綴樹。
     * 3.發布PropertyUpdateEvent事件。
     * <p>
     * 如果事件的addOrRemove屬性既不是"add"也不是"remove"，則不處理。
     *
     * @param event CryptoHistoryDataChangeEvent事件對象
     *
     * @throws RuntimeException 如果追蹤歷史資料失敗或操作失敗，則拋出異常
     */
    @Override
    public void onApplicationEvent(
            @NonNull CryptoHistoryDataChangeEvent event) {
        if ("add".equals(event.getAddOrRemove())) {
            if (progressTrackerService.getAllProgressInfo()
                                      .stream()
                                      .anyMatch(x -> x.getTaskName().equals(event.getCryptoTradingPair().getTradingPair()))) {
                return;
            }
            try {
                cryptoService.trackCryptoHistoryPrices(event.getCryptoTradingPair());
                crontabMethod.cacheAssetTrie();
                eventPublisher.publishEvent(new PropertyUpdateEvent(this));
            } catch (Exception e) {
                throw new RuntimeException(new ServiceExceptions(TRACK_CRYPTO_HISTORY_ERROR, e));
            }
        } else if ("remove".equals(event.getAddOrRemove())) {
            try {
                retryTemplate.doWithRetry(() -> {
                    cryptoService.removeCryptoPricesDataByTradingPair(event.getCryptoTradingPair().getTradingPair());
                    crontabMethod.cacheAssetTrie();
                });
                eventPublisher.publishEvent(new PropertyUpdateEvent(this));
            } catch (RetryException e) {
                Exception lastException = e.getLastException();
                throw new RuntimeException("操作失敗: " + lastException.getMessage(), lastException);
            }
        }
    }
}
