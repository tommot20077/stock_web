package xyz.dowob.stockweb.Component.EventListener.Crypto;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Service.Common.ProgressTracker;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;

@Component
public class CryptoHistoryDataChangeListener implements ApplicationListener<CryptoHistoryDataChangeEvent> {
    private final Logger logger = LoggerFactory.getLogger(CryptoHistoryDataChangeListener.class);
    private final CryptoService cryptoService;
    private final ProgressTracker progressTracker;

    @Autowired
    public CryptoHistoryDataChangeListener(CryptoService cryptoService, ProgressTracker progressTracker) {this.cryptoService = cryptoService;
        this.progressTracker = progressTracker;
    }

    @Override
    public void onApplicationEvent(@NotNull CryptoHistoryDataChangeEvent event) {
        logger.debug("收到虛擬貨幣資料變更通知");
        if ("add".equals(event.getAddOrRemove())) {
            if (progressTracker.getAllProgressInfo().stream().anyMatch(
                    x -> x.getTaskName().equals(event.getCryptoTradingPair().getTradingPair()))) {
                logger.debug("該虛擬貨幣已經在執行中，不處理");
                return;
            }
            logger.debug("新增歷史資料");
            cryptoService.trackCryptoHistoryPrices(event.getCryptoTradingPair());
        } else if ("remove".equals(event.getAddOrRemove())) {
            logger.debug("移除資料");
            cryptoService.removeCryptoPricesDataByTradingPair(event.getCryptoTradingPair().getTradingPair());
        } else {
            logger.debug("未知動作，不處理");
        }
    }
}
