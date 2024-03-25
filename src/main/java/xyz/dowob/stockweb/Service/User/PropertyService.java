package xyz.dowob.stockweb.Service.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.PropertyRepository;


import java.math.BigDecimal;

@Service
public class PropertyService {
    private final StockTwRepository stockTwRepository;
    private final PropertyRepository propertyRepository;
    private final CurrencyRepository currencyRepository;
    private final CryptoRepository cryptoRepository;
    Logger logger = LoggerFactory.getLogger(PropertyService.class);

    @Autowired
    public PropertyService(StockTwRepository stockTwRepository, PropertyRepository propertyRepository, CurrencyRepository currencyRepository, CryptoRepository cryptoRepository) {
        this.stockTwRepository = stockTwRepository;
        this.propertyRepository = propertyRepository;
        this.currencyRepository = currencyRepository;
        this.cryptoRepository = cryptoRepository;
    }

    public void modifyStock(User user, PropertyListDto.PropertyDto request) {
        logger.debug("讀取資料: " + request);
        String stockCode = request.getSymbol();
        logger.debug("股票代碼: " + stockCode);
        String description = request.getDescription();
        logger.debug("描述: " + description);
        BigDecimal quantity = request.getQuantityBigDecimal();
        logger.debug("數量: " + quantity);
        StockTw stock = stockTwRepository.findByStockCode(stockCode).orElse(null);


        switch (request.getOperationTypeEnum()) {
            case ADD:
                logger.debug("新增");
                if (request.getId() != null) {
                    logger.debug("id: " + request.getId() + "新增時不應該有 id");
                    throw new RuntimeException("新增時不應該有 id");
                }

                if (stock == null) {
                    logger.debug("找不到指定的股票代碼");
                    throw new RuntimeException("找不到指定的股票代碼");
                }
                logger.debug("新增持有股票");
                Property property = new Property();
                property.setUser(user);
                property.setAsset(stock);
                property.setAssetName(stock.getStockName());
                property.setQuantity(quantity);
                property.setDescription(description);
                propertyRepository.save(property);
                logger.debug("新增成功");
                break;

            case REMOVE:
                logger.debug("刪除");
                if (request.getId() == null) {
                    logger.debug("刪除時必須有 id");
                    throw new RuntimeException("刪除時必須有 id");
                }

                Property propertyToRemove = propertyRepository.findById(request.getId()).orElse(null);
                if (propertyToRemove == null) {
                    logger.debug("找不到指定的持有股票");
                    throw new RuntimeException("找不到指定的持有股票");
                }

                if (!propertyToRemove.getUser().equals(user)) {
                    logger.debug("無法刪除其他人的持有股票");
                    throw new RuntimeException("無法刪除其他人的持有股票");
                }

                propertyRepository.delete(propertyToRemove);
                logger.debug("刪除成功");
                break;

            case UPDATE:
                if (request.getId() == null) {
                    logger.debug("更新時必須有 id");
                    throw new RuntimeException("更新時必須有 id");
                }

                Property propertyToUpdate = propertyRepository.findById(request.getId()).orElse(null);
                if (propertyToUpdate == null) {
                    logger.debug("找不到指定的持有股票");
                    throw new RuntimeException("找不到指定的持有股票");
                }

                if (!propertyToUpdate.getUser().equals(user)) {
                    logger.debug("無法更新其他人的持有股票");
                    throw new RuntimeException("無法更新其他人的持有股票");
                }

                logger.debug("更新股要");
                propertyToUpdate.setQuantity(quantity);
                propertyToUpdate.setDescription(description);
                propertyRepository.save(propertyToUpdate);
                logger.debug("更新成功");
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");

        }
    }

    public void modifyCurrency(User user, PropertyListDto.PropertyDto request) {
        logger.debug("讀取資料: " + request);
        Long id = request.getId();
        logger.debug("id: " + id);
        String description = request.getDescription();
        logger.debug("描述: " + description);
        BigDecimal quantity = request.getQuantityBigDecimal();
        logger.debug("數量: " + quantity);
        Currency currency = currencyRepository.findByCurrency(request.getSymbol().toUpperCase()).orElse(null);
        logger.debug("貨幣: " + currency);

        switch (request.getOperationTypeEnum()) {
            case ADD:
                logger.debug("新增");
                if (id != null) {
                    logger.debug("id: " + id + "新增時不應該有 id");
                    throw new RuntimeException("新增時不應該有 id");
                }
                if (currency == null) {
                    logger.debug("找不到指定的貨幣代碼");
                    throw new RuntimeException("找不到指定的貨幣代碼");
                }
                logger.debug("新增持有貨幣");

                Property property = new Property();
                property.setUser(user);
                property.setAsset(currency);
                property.setAssetName(currency.getCurrency());
                property.setQuantity(quantity);
                property.setDescription(description);
                propertyRepository.save(property);
                logger.debug("新增成功");
                break;

            case REMOVE:
                logger.debug("刪除");
                if (id == null) {
                    logger.debug("刪除時必須有 id");
                    throw new RuntimeException("刪除時必須有 id");
                }

                Property propertyToRemove = propertyRepository.findById(id).orElse(null);
                if (propertyToRemove == null) {
                    logger.debug("找不到指定的持有貨幣");
                    throw new RuntimeException("找不到指定的持有貨幣");
                }

                if (!propertyToRemove.getUser().equals(user)) {
                    logger.debug("無法刪除其他人的持有貨幣");
                    throw new RuntimeException("無法刪除其他人的持有貨幣");
                }

                propertyRepository.delete(propertyToRemove);
                logger.debug("刪除成功");
                break;

            case UPDATE:
                if (id == null) {
                    logger.debug("更新時必須有 id");
                    throw new RuntimeException("更新時必須有 id");
                }

                Property propertyToUpdate = propertyRepository.findById(id).orElse(null);
                if (propertyToUpdate == null) {
                    logger.debug("找不到指定的持有貨幣");
                    throw new RuntimeException("找不到指定的持有貨幣");
                }

                if (!propertyToUpdate.getUser().equals(user)) {
                    logger.debug("無法更新其他人的持有貨幣");
                    throw new RuntimeException("無法更新其他人的持有貨幣");
                }

                logger.debug("更新貨幣");

                propertyToUpdate.setQuantity(quantity);
                propertyToUpdate.setDescription(description);
                propertyRepository.save(propertyToUpdate);
                logger.debug("更新成功");
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");
        }
    }
    
    public void modifyCrypto(User user, PropertyListDto.PropertyDto request) {
        logger.debug("讀取資料: " + request);
        Long id = request.getId();
        logger.debug("id: " + id);
        String description = request.getDescription();
        logger.debug("描述: " + description);
        BigDecimal quantity = request.getQuantityBigDecimal();
        logger.debug("數量: " + quantity);
        String cryptoTradingPair = (request.getSymbol() + "USDT").toUpperCase();
        logger.debug("加密貨幣: " + cryptoTradingPair);

        switch (request.getOperationTypeEnum()) {
            case ADD:
                logger.debug("新增");
                if (id != null) {
                    logger.debug("id: " + id + "新增時不應該有 id");
                    throw new RuntimeException("新增時不應該有 id");
                }

                CryptoTradingPair tradingPair = cryptoRepository.findByTradingPair(cryptoTradingPair).orElse(null);
                if (tradingPair == null) {
                    logger.debug("找不到指定的加密貨幣");
                    throw new RuntimeException("找不到指定的加密貨幣");
                }
                logger.debug("新增持有加密貨幣");

                Property property = new Property();
                property.setUser(user);
                property.setAsset(tradingPair);
                property.setAssetName(request.getSymbol());
                property.setQuantity(quantity);
                property.setDescription(description);
                propertyRepository.save(property);
                logger.debug("新增成功");
                break;

            case REMOVE:
                logger.debug("刪除");
                if (id == null) {
                    logger.debug("刪除時必須有 id");
                    throw new RuntimeException("刪除時必須有 id");
                }

                Property propertyToRemove = propertyRepository.findById(id).orElse(null);
                if (propertyToRemove == null) {
                    logger.debug("找不到指定的持有加密貨幣");
                    throw new RuntimeException("找不到指定的持有加密貨幣");
                }

                if (!propertyToRemove.getUser().equals(user)) {
                    logger.debug("無法刪除其他人的持有加密貨幣");
                    throw new RuntimeException("無法刪除其他人的持有加密貨幣");
                }

                propertyRepository.delete(propertyToRemove);
                logger.debug("刪除成功");
                break;

            case UPDATE:
                logger.debug("更新");
                if (id == null) {
                    logger.debug("更新時必須有 id");
                    throw new RuntimeException("更新時必須有 id");
                }

                Property propertyToUpdate = propertyRepository.findById(id).orElse(null);
                if (propertyToUpdate == null) {
                    logger.debug("找不到指定的持有加密貨幣");
                    throw new RuntimeException("找不到指定的持有加密貨幣");
                }

                if (!propertyToUpdate.getUser().equals(user)) {
                    logger.debug("無法更新其他人的持有加密貨幣");
                    throw new RuntimeException("無法更新其他人的持有加密貨幣");
                }

                logger.debug("更新加密貨幣");

                propertyToUpdate.setQuantity(quantity);
                propertyToUpdate.setDescription(description);
                propertyRepository.save(propertyToUpdate);
                logger.debug("更新成功");
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");

        }
    }
}
