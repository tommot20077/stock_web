package xyz.dowob.stockweb.Component.EventListener.StockTw;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Service.Common.ProgressTracker;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

/**
 * @author yuan
 */
@Component
public class StockTwHistoryDataChangeListener implements ApplicationListener<StockTwHistoryDataChangeEvent> {
    Logger logger = LoggerFactory.getLogger(StockTwHistoryDataChangeListener.class);

    private final StockTwService stockTwService;

    private final ProgressTracker progressTracker;

    private final RetryTemplate retryTemplate;

    @Autowired
    public StockTwHistoryDataChangeListener(StockTwService stockTwService, ProgressTracker progressTracker, RetryTemplate retryTemplate) {
        this.stockTwService = stockTwService;
        this.progressTracker = progressTracker;
        this.retryTemplate = retryTemplate;
    }

    /**
     * 此方法會在收到股票歷史資料變更事件時被調用。
     *
     * @param event 股票歷史資料變更事件對象。
     * 如果事件的 addOrRemove 屬性為 "add"，則檢查進度追蹤器中是否已有與事件相關的股票代碼。如果有，則不進行任何操作。如果沒有，則調用 stockTwService 的 trackStockTwHistoryPrices 方法來追蹤股票的歷史價格。
     * 如果事件的 addOrRemove 屬性為 "remove"，則嘗試移除股票的價格數據。如果在重試過程中出現異常，則拋出 RuntimeException。
     * 如果事件的 addOrRemove 屬性既不是 "add" 也不是 "remove"，則不進行任何操作。
     */
    @Override
    public void onApplicationEvent(
            @NotNull StockTwHistoryDataChangeEvent event) {
        logger.info("收到股票歷史資料變更事件");
        if ("add".equals(event.getAddOrRemove())) {
            if (progressTracker.getAllProgressInfo().stream().anyMatch(x -> x.getTaskName().equals(event.getStockTw().getStockCode()))) {
                logger.debug("該股票已經在執行中，不處理");
                return;
            }
            logger.debug("新增歷史資料");
            stockTwService.trackStockTwHistoryPrices(event.getStockTw());
        } else if ("remove".equals(event.getAddOrRemove())) {
            try {
                retryTemplate.doWithRetry(() -> {
                    logger.warn("移除資料");
                    stockTwService.removeStockTwPricesDataByStockCode(event.getStockTw().getStockCode());
                });
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
