package xyz.dowob.stockweb.Component.EventListener.StockTw;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Service.Common.ProgressTracker;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

@Component
public class StockTwHistoryDataChangeListener implements ApplicationListener<StockTwHistoryDataChangeEvent> {
    Logger logger = LoggerFactory.getLogger(StockTwHistoryDataChangeListener.class);
    private final StockTwService stockTwService;
    private final ProgressTracker progressTracker;
    @Autowired
    public StockTwHistoryDataChangeListener(StockTwService stockTwService, ProgressTracker progressTracker) {this.stockTwService = stockTwService;
        this.progressTracker = progressTracker;
    }

    @Override
    public void onApplicationEvent(@NotNull StockTwHistoryDataChangeEvent event) {
        logger.debug("收到股票歷史資料變更事件");
        if ("add".equals(event.getAddOrRemove())) {
            if (progressTracker.getAllProgressInfo().stream().anyMatch(
                    x -> x.getTaskName().equals(event.getStockTw().getStockCode()))) {
                logger.debug("該股票已經在執行中，不處理");
                return;
            }
            logger.debug("新增歷史資料");
            stockTwService.trackStockTwHistoryPrices(event.getStockTw());
        } else if ("remove".equals(event.getAddOrRemove())) {
            logger.debug("移除資料");
            stockTwService.removeStockTwPricesDataByStockCode(event.getStockTw().getStockCode());
        } else {
            logger.debug("未知動作，不處理");
        }
    }

}
