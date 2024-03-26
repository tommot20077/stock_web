package xyz.dowob.stockweb.Service.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Dto.Property.TransactionListDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.Transaction;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Common.AssetRepository;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.PropertyRepository;
import xyz.dowob.stockweb.Repository.User.TransactionRepository;


@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CurrencyRepository currencyRepository;
    private final CryptoRepository cryptoRepository;
    private final AssetRepository assetRepository;
    private final PropertyRepository propertyRepository;
    private final StockTwRepository stockTwRepository;
    Logger logger = LoggerFactory.getLogger(TransactionService.class);
    @Autowired
    public TransactionService(TransactionRepository transactionRepository, CurrencyRepository currencyRepository, CryptoRepository cryptoRepository, AssetRepository assetRepository, PropertyRepository propertyRepository, StockTwRepository stockTwRepository) {
        this.transactionRepository = transactionRepository;
        this.currencyRepository = currencyRepository;
        this.cryptoRepository = cryptoRepository;
        this.assetRepository = assetRepository;
        this.propertyRepository = propertyRepository;
        this.stockTwRepository = stockTwRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void operation(User user, TransactionListDto.TransactionDto transaction) {
        logger.debug("紀錄交易: {}", transaction);
        String symbol = transaction.getSymbol();
        String unit = transaction.getUnit();
        Long symbolId = getAssetId(symbol);
        Long unitId = getAssetId(unit);
        Asset asset = assetRepository.findById(symbolId).orElseThrow(() -> new IllegalArgumentException("找不到交易對象: " + symbol));
        Asset unitAsset = assetRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("找不到貨幣單位: " + unit));
        Property userUnitProperty = propertyRepository.findByAssetAndUser(unitAsset, user).orElse(null);
        Property userSymbolProperty = propertyRepository.findByAssetAndUser(asset, user).orElse(null);

        switch (transaction.getOperationTypeEnum()) {
            case BUY:
                // TODO 用戶設定顯示貨幣
                logger.debug("買入{}", symbol);
                if (userUnitProperty == null || userUnitProperty.getQuantity().compareTo(transaction.getAmountAsBigDecimal()) < 0) {
                    throw new IllegalArgumentException("用戶資產中沒有沒有足夠的" + unit + "來完成交易");
                }
                userUnitProperty.setQuantity(userUnitProperty.getQuantity().subtract(transaction.getAmountAsBigDecimal()));

                if (userSymbolProperty == null) {
                    logger.debug("建立 {} 資產", symbol);
                    userSymbolProperty = new Property();
                    userSymbolProperty.setUser(user);
                    userSymbolProperty.setAsset(asset);
                    userSymbolProperty.setAssetName(symbol);
                    userSymbolProperty.setQuantity(transaction.getQuantityAsBigDecimal());
                    userSymbolProperty.setDescription(transaction.getDescription());
                } else {
                    logger.debug("更新 {} 資產", symbol);
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().add(transaction.getQuantityAsBigDecimal()));
                }
                propertyRepository.save(userUnitProperty);
                propertyRepository.save(userSymbolProperty);
                logger.debug("儲存 {} 資產變更", symbol);
                break;

            case SELL:
                logger.debug("賣出{}", symbol);
                if (userSymbolProperty == null || userSymbolProperty.getQuantity().compareTo(transaction.getQuantityAsBigDecimal()) < 0) {
                    throw new IllegalArgumentException("用戶資產中沒有沒有足夠的" + symbol + "來完成交易");
                }
                userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().subtract(transaction.getQuantityAsBigDecimal()));

                if (userUnitProperty == null) {
                    logger.debug("建立 {} 資產", unit);
                    userUnitProperty = new Property();
                    userUnitProperty.setUser(user);
                    userUnitProperty.setAsset(unitAsset);
                    userUnitProperty.setAssetName(unit);
                    userUnitProperty.setQuantity(transaction.getAmountAsBigDecimal());
                    userUnitProperty.setDescription(transaction.getDescription());
                } else {
                    logger.debug("更新 {} 資產", unit);
                    userUnitProperty.setQuantity(userUnitProperty.getQuantity().add(transaction.getAmountAsBigDecimal()));
                }
                logger.debug("儲存 {} 資產變更", unit);
                propertyRepository.save(userUnitProperty);
                propertyRepository.save(userSymbolProperty);
                break;

            case WITHDRAW:
                logger.debug("提款{}", symbol);
                if (userSymbolProperty == null || userSymbolProperty.getQuantity().compareTo(transaction.getQuantityAsBigDecimal()) < 0) {
                    throw new IllegalArgumentException("用戶資產中沒有沒有足夠的" + symbol + "來完成交易");
                }
                userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().subtract(transaction.getQuantityAsBigDecimal()));
                userSymbolProperty.setDescription(transaction.getDescription());
                propertyRepository.save(userSymbolProperty);
                logger.debug("儲存 {} 資產變更", symbol);
                break;

            case DEPOSIT:
                logger.debug("存款{}", symbol);
                if (userSymbolProperty == null) {
                    logger.debug("建立 {} 資產", symbol);
                    userSymbolProperty = new Property();
                    userSymbolProperty.setUser(user);
                    userSymbolProperty.setAsset(asset);
                    userSymbolProperty.setAssetName(symbol);
                    userSymbolProperty.setQuantity(transaction.getQuantityAsBigDecimal());
                    userSymbolProperty.setDescription(transaction.getDescription());
                } else {
                    logger.debug("更新 {} 資產", symbol);
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().add(transaction.getAmountAsBigDecimal()));
                }
                propertyRepository.save(userSymbolProperty);
                logger.debug("儲存 {} 資產變更", symbol);
                break;
        }
        logger.debug("儲存交易");
        Transaction buyTransaction = new Transaction();
        buyTransaction.setUser(user);
        buyTransaction.setType(transaction.getOperationTypeEnum());
        buyTransaction.setAsset(asset);
        buyTransaction.setAssetName(symbol);
        buyTransaction.setAmount(transaction.getAmountAsBigDecimal());
        buyTransaction.setQuantity(transaction.getQuantityAsBigDecimal());
        buyTransaction.setUnitCurrency(unitAsset);
        buyTransaction.setTransactionDate(transaction.getTransactionDate());
        buyTransaction.setDescription(transaction.getDescription());
        transactionRepository.save(buyTransaction);
        logger.debug("儲存交易紀錄");

    }

    private Long getAssetId(String symbol) {
        String key = symbol.toUpperCase();
        Long assetId;

        assetId = currencyRepository.findByCurrency(key).map(Currency::getId).orElse(null);
        if (assetId != null) return assetId;

        assetId = cryptoRepository.findByTradingPair(key).map(CryptoTradingPair::getId).orElse(null);
        if (assetId != null) return assetId;

        assetId = stockTwRepository.findByStockCode(key).map(StockTw::getId).orElse(null);
        if (assetId != null) return assetId;

        throw new IllegalArgumentException("找不到可以轉換的貨幣或加密貨幣: " + symbol);
    }
}
