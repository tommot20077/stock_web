package xyz.dowob.stockweb.Component.Handler;

import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Currency.Currency;

import java.math.BigDecimal;

/**
 * 此類別包含處理資產相關操作的方法。
 * 用於將特定資產的匯率轉換為偏好貨幣的匯率。
 *
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
     */
    public BigDecimal exrateToPreferredCurrency(Asset asset, BigDecimal assetExrate, Currency preferredCurrency) {
        BigDecimal preferredCurrencyRate = preferredCurrency.getExchangeRate();
        return switch (asset.getAssetType()) {
            case CURRENCY, CRYPTO, STOCK_TW -> preferredCurrencyRate.multiply(assetExrate);
        };
    }
}
