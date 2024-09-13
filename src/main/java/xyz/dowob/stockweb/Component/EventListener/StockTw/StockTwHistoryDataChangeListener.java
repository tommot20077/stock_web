package xyz.dowob.stockweb.Component.EventListener.StockTw;

import lombok.NonNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Service.Common.ProgressTrackerService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

/**
 * 當StockTwHistoryDataChangeEvent事件發生時，此類別將被調用。
 * 實現ApplicationListener接口。並以StockTwHistoryDataChangeEvent作為參數。
 * 此類別用於監聽StockTwHistoryDataChangeEvent事件，並根據事件的addOrRemove屬性執行相應操作。
 *
 * @author yuan
 */
@Component
public class StockTwHistoryDataChangeListener implements ApplicationListener<StockTwHistoryDataChangeEvent> {
    private final StockTwService stockTwService;

    private final ProgressTrackerService progressTrackerService;

    private final RetryTemplate retryTemplate;

    private final CrontabMethod crontabMethod;

    /**
     * StockTwHistoryDataChangeListener類別的構造函數。
     *
     * @param stockTwService         股票相關服務方法
     * @param progressTrackerService 進度追蹤相關服務方法
     * @param retryTemplate          重試模板
     * @param crontabMethod          定時任務相關方法
     */
    public StockTwHistoryDataChangeListener(StockTwService stockTwService, ProgressTrackerService progressTrackerService, RetryTemplate retryTemplate, CrontabMethod crontabMethod) {
        this.stockTwService = stockTwService;
        this.progressTrackerService = progressTrackerService;
        this.retryTemplate = retryTemplate;
        this.crontabMethod = crontabMethod;
    }

    /**
     * 此方法會在收到股票歷史資料變更事件時被調用。
     *
     * @param event 股票歷史資料變更事件對象。
     *              如果事件的 addOrRemove 屬性為 "add"，則檢查進度追蹤器中是否已有與事件相關的股票代碼。如果有，則不進行任何操作。如果沒有，則調用 stockTwService 的 trackStockTwHistoryPrices 方法來追蹤股票的歷史價格。
     *              如果事件的 addOrRemove 屬性為 "remove"，則嘗試移除股票的價格數據。如果在重試過程中出現異常，則拋出 RuntimeException。
     *              如果事件的 addOrRemove 屬性既不是 "add" 也不是 "remove"，則不進行任何操作。
     *              最後調用 crontabMethod 的 cacheAssetTrie 方法重新構建資產前綴樹。
     */
    @Override
    public void onApplicationEvent(
            @NonNull StockTwHistoryDataChangeEvent event) {
        if ("add".equals(event.getAddOrRemove())) {
            if (progressTrackerService.getAllProgressInfo()
                                      .stream()
                                      .anyMatch(x -> x.getTaskName().equals(event.getStockTw().getStockCode()))) {
                return;
            }
            stockTwService.trackStockTwHistoryPrices(event.getStockTw());
            crontabMethod.cacheAssetTrie();
        } else if ("remove".equals(event.getAddOrRemove())) {
            try {
                retryTemplate.doWithRetry(() -> {
                    stockTwService.removeStockTwPricesDataByStockCode(event.getStockTw().getStockCode());
                    crontabMethod.cacheAssetTrie();
                });
            } catch (RetryException e) {
                Exception lastException = e.getLastException();
                throw new RuntimeException("操作失敗: " + lastException.getMessage(), lastException);
            }
        }
    }
}
