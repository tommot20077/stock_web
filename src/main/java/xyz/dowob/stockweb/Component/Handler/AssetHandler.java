package xyz.dowob.stockweb.Component.Handler;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author yuan
 */
@Component
public class AssetHandler {
    private final CurrencyRepository currencyRepository;
    private Currency twdCurrency;
    Logger logger = LoggerFactory.getLogger(AssetHandler.class);


    @Autowired
    public AssetHandler(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }


    @PostConstruct
    public void init() {
        try {
            twdCurrency = currencyRepository.findByCurrency("TWD").orElse(null);
        } catch (Exception e) {
            logger.error("無法加載台幣匯率", e);
        }
    }


    public BigDecimal exrateToPreferredCurrency(Asset asset, BigDecimal assetExrate, Currency preferredCurrency) {
        BigDecimal preferredCurrencyRate = preferredCurrency.getExchangeRate();
        if (asset.getAssetType() == AssetType.CURRENCY) {
            return preferredCurrencyRate.divide(assetExrate, 8, RoundingMode.HALF_UP);
        } else if (asset.getAssetType() == AssetType.CRYPTO) {
            return preferredCurrencyRate.multiply(assetExrate);
        } else if (asset.getAssetType() == AssetType.STOCK_TW) {
            return assetExrate.divide(getTwdCurrency(), 8, RoundingMode.HALF_UP).multiply(preferredCurrencyRate);
        } else {
            throw new IllegalArgumentException("不支援的資產類型");
        }
    }


    private BigDecimal getTwdCurrency() {
        if (twdCurrency == null) {
            throw new IllegalStateException("台幣匯率未初始化");
        }
        return twdCurrency.getExchangeRate();
    }
}
