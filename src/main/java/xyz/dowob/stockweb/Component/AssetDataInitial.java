package xyz.dowob.stockweb.Component;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

import java.util.List;
@Component
public class AssetDataInitial {
    Logger logger = LoggerFactory.getLogger(AssetDataInitial.class);
    private final CurrencyService currencyService;
    private final CurrencyRepository currencyRepository;
    private final StockTwService stockTwService;
    private final StockTwRepository stockTwRepository;
    private final CryptoService cryptoService;
    private final CryptoRepository cryptoRepository;

    public AssetDataInitial(CurrencyService currencyService, CurrencyRepository currencyRepository, StockTwService stockTwService, StockTwRepository stockTwRepository, CryptoService cryptoService, CryptoRepository cryptoRepository) {this.currencyService = currencyService;
        this.currencyRepository = currencyRepository;
        this.stockTwService = stockTwService;
        this.stockTwRepository = stockTwRepository;
        this.cryptoService = cryptoService;
        this.cryptoRepository = cryptoRepository;
    }

    @PostConstruct
    public void init() {
        try {
            logger.info("確認貨幣匯率資料");
            List<String> currencies = currencyRepository.findAllDistinctCurrencies();
            if (currencies.isEmpty()) {
                logger.info("貨幣匯率資料為空,開始加載");
                currencyService.updateCurrencyData();
            }

            logger.debug("確認台灣股票資料");
            List<Object[]> stockTws = stockTwRepository.findAllByOrderByStockCode();
            if (stockTws.isEmpty()) {
                logger.info("台灣股票資料為空,開始加載");
                stockTwService.updateStockList();
            }

            logger.debug("確認加密貨幣資料");
            List<String> cryptoTradingPairs = cryptoRepository.findAllBaseAssetByOrderByBaseAssetAsc();
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
