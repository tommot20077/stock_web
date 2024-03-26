package xyz.dowob.stockweb.Service.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Enum.OperationType;
import xyz.dowob.stockweb.Enum.TransactionType;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.Transaction;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Crypto.CryptoRepository;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.StockTW.StockTwRepository;
import xyz.dowob.stockweb.Repository.User.PropertyRepository;
import xyz.dowob.stockweb.Repository.User.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PropertyService {
    private final StockTwRepository stockTwRepository;
    private final PropertyRepository propertyRepository;
    private final CurrencyRepository currencyRepository;
    private final CryptoRepository cryptoRepository;
    private final TransactionRepository transactionRepository;
    Logger logger = LoggerFactory.getLogger(PropertyService.class);

    @Autowired
    public PropertyService(StockTwRepository stockTwRepository, PropertyRepository propertyRepository, CurrencyRepository currencyRepository, CryptoRepository cryptoRepository, TransactionRepository transactionRepository) {
        this.stockTwRepository = stockTwRepository;
        this.propertyRepository = propertyRepository;
        this.currencyRepository = currencyRepository;
        this.cryptoRepository = cryptoRepository;
        this.transactionRepository = transactionRepository;
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
                Property propertyToAdd = new Property();
                propertyToAdd.setUser(user);
                propertyToAdd.setAsset(stock);
                propertyToAdd.setAssetName(stock.getStockName());
                propertyToAdd.setQuantity(quantity);
                propertyToAdd.setDescription(description);
                propertyRepository.save(propertyToAdd);
                logger.debug("新增成功");
                recordTransaction(user, propertyToAdd, request.getOperationTypeEnum());
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
                recordTransaction(user, propertyToRemove, request.getOperationTypeEnum());
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
                recordTransaction(user, propertyToUpdate, request.getOperationTypeEnum());
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

                Property propertyToAdd = new Property();
                propertyToAdd.setUser(user);
                propertyToAdd.setAsset(currency);
                propertyToAdd.setAssetName(currency.getCurrency());
                propertyToAdd.setQuantity(quantity);
                propertyToAdd.setDescription(description);
                propertyRepository.save(propertyToAdd);
                logger.debug("新增成功");
                recordTransaction(user, propertyToAdd, request.getOperationTypeEnum());
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
                recordTransaction(user, propertyToRemove, request.getOperationTypeEnum());
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
                recordTransaction(user, propertyToUpdate, request.getOperationTypeEnum());
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

                Property propertyToAdd = new Property();
                propertyToAdd.setUser(user);
                propertyToAdd.setAsset(tradingPair);
                propertyToAdd.setAssetName(request.getSymbol());
                propertyToAdd.setQuantity(quantity);
                propertyToAdd.setDescription(description);
                propertyRepository.save(propertyToAdd);
                logger.debug("新增成功");
                recordTransaction(user, propertyToAdd, request.getOperationTypeEnum());
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
                recordTransaction(user, propertyToRemove, request.getOperationTypeEnum());
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
                recordTransaction(user, propertyToUpdate, request.getOperationTypeEnum());
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");

        }
    }

    private void recordTransaction(User user, Property property, OperationType operationType) {
        logger.debug("自動記錄交易");
        Transaction transaction = new Transaction();
        logger.debug("建立交易物件");
        transaction.setUser(user);
        logger.debug("設定使用者");
        logger.debug("設定交易類型: " + operationType);

        if (Objects.equals(operationType.toString(), "ADD")) {
            transaction.setType(TransactionType.DEPOSIT);
            logger.debug("設定交易類型: 存款");
            transaction.setDescription("系統自動備註: 新增持有" + property.getAssetName() + "數量: " + property.getQuantity().stripTrailingZeros().toPlainString());
            logger.debug("設定交易金額: " + property.getQuantity());
        } else if (Objects.equals(operationType.toString(), "REMOVE")) {
            transaction.setType(TransactionType.WITHDRAW);
            logger.debug("設定交易類型: 取款");
            transaction.setDescription("系統自動備註: 刪除持有" + property.getAssetName() + "數量: " + property.getQuantity().stripTrailingZeros().toPlainString());
            logger.debug("設定交易金額: " + property.getQuantity());
        } else if (Objects.equals(operationType.toString(), "UPDATE")) {
            transaction.setType(TransactionType.UPDATE);
            logger.debug("設定交易類型: 更新");
            transaction.setDescription("系統自動備註: 更新持有" + property.getAssetName() + "數量: " + property.getQuantity().stripTrailingZeros().toPlainString());
            logger.debug("設定交易金額: " + property.getQuantity());
        }

        transaction.setAsset(property.getAsset());
        transaction.setAssetName(property.getAssetName());
        transaction.setAmount(BigDecimal.valueOf(0));
        transaction.setQuantity(property.getQuantity());
        transaction.setUnitCurrency(user.getPreferredCurrency());
        transaction.setTransactionDate(LocalDateTime.now());
        transactionRepository.save(transaction);
        logger.debug("儲存交易紀錄");
    }
}
