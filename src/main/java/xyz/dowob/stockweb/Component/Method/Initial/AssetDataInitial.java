package xyz.dowob.stockweb.Component.Method.Initial;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

/**
 * 這是一個初始化類，用於在應用程序啟動時加載資產資料。
 *
 * @author yuan
 */
@Component
public class AssetDataInitial {
    Logger logger = LoggerFactory.getLogger(AssetDataInitial.class);

    private final CurrencyService currencyService;

    private final CurrencyRepository currencyRepository;

    private final StockTwService stockTwService;

    private final StockTwRepository stockTwRepository;

    private final CryptoService cryptoService;

    private final CryptoRepository cryptoRepository;

    public AssetDataInitial(CurrencyService currencyService, CurrencyRepository currencyRepository, StockTwService stockTwService, StockTwRepository stockTwRepository, CryptoService cryptoService, CryptoRepository cryptoRepository) {
        this.currencyService = currencyService;
        this.currencyRepository = currencyRepository;
        this.stockTwService = stockTwService;
        this.stockTwRepository = stockTwRepository;
        this.cryptoService = cryptoService;
        this.cryptoRepository = cryptoRepository;
    }

    /**
     * init：這是一個初始化方法，會在類別實例化後自動執行。
     * 首先，它會創建一個分頁請求，用於從各個MySQL資料庫中獲取資料。它會檢查資產資料是否存在。
     * 如果不存在，則會調用服務來更新這些資料
     */
    @PostConstruct
    public void init() {
        try {
            Pageable pageable = PageRequest.of(0, 10);

            logger.info("確認貨幣匯率資料");
            Page<String> currencies = currencyRepository.findAllCurrenciesByPage(pageable);
            if (currencies.isEmpty()) {
                logger.info("貨幣匯率資料為空,開始加載");
                currencyService.updateCurrencyData();
            }

            logger.debug("確認台灣股票資料");
            Page<String> stockTws = stockTwRepository.findAllStockCodeByPage(pageable);
            if (stockTws.isEmpty()) {
                logger.info("台灣股票資料為空,開始加載");
                stockTwService.updateStockList();
            }

            logger.debug("確認加密貨幣資料");
            Page<String> cryptoTradingPairs = cryptoRepository.findAllTradingPairByPage(pageable);
            if (cryptoTradingPairs.isEmpty()) {
                logger.info("加密貨幣資料為空,開始加載");
                cryptoService.updateSymbolList();
            }
            logger.info("資料初始化完成");
        } catch (Exception e) {
            logger.error("無法加載資產資料", e);
        }
    }
}
