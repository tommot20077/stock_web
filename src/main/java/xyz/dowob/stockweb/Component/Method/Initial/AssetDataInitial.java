package xyz.dowob.stockweb.Component.Method.Initial;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class AssetDataInitial {
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
            Page<String> currencies = currencyRepository.findAllCurrenciesByPage(pageable);
            if (currencies.isEmpty()) {
                currencyService.updateCurrencyData();
            }
            Page<String> stockTws = stockTwRepository.findAllStockCodeByPage(pageable);
            if (stockTws.isEmpty()) {
                stockTwService.updateStockList();
            }
            Page<String> cryptoTradingPairs = cryptoRepository.findAllTradingPairByPage(pageable);
            if (cryptoTradingPairs.isEmpty()) {
                cryptoService.updateSymbolList();
            }
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }
}
