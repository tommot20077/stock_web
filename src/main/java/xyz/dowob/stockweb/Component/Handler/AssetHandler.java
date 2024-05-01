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

    public BigDecimal exrateToPreferredCurrency(Asset asset, BigDecimal assetExrate, Currency preferredCurrency) {
        BigDecimal preferredCurrencyRate = preferredCurrency.getExchangeRate();
        if (asset.getAssetType() == AssetType.CURRENCY) {
            return preferredCurrencyRate.multiply(assetExrate);
        } else if (asset.getAssetType() == AssetType.CRYPTO || asset.getAssetType() == AssetType.STOCK_TW) {
            return preferredCurrencyRate.multiply(assetExrate);
        }  else {
            throw new IllegalArgumentException("不支援的資產類型");
        }
    }
}
