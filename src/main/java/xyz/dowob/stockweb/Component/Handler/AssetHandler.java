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
    /**
     * 此方法將特定資產的匯率轉換為偏好貨幣的匯率。
     *
     * @param asset             包含資產類型等信息的資產對象。
     * @param assetExrate       資產的匯率。
     * @param preferredCurrency 包含偏好貨幣匯率等信息的偏好貨幣對象。
     *
     * @return 偏好貨幣下的資產匯率。
     *
     * @throws IllegalArgumentException 如果資產類型不受支援。
     */
    public BigDecimal exrateToPreferredCurrency(Asset asset, BigDecimal assetExrate, Currency preferredCurrency) {
        BigDecimal preferredCurrencyRate = preferredCurrency.getExchangeRate();
        if (asset.getAssetType() == AssetType.CURRENCY) {
            return preferredCurrencyRate.multiply(assetExrate);
        } else if (asset.getAssetType() == AssetType.CRYPTO || asset.getAssetType() == AssetType.STOCK_TW) {
            return preferredCurrencyRate.multiply(assetExrate);
        } else {
            throw new IllegalArgumentException("不支援的資產類型");
        }
    }
}
