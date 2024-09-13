package xyz.dowob.stockweb.Service.User;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Annotation.MeaninglessData;
import xyz.dowob.stockweb.Component.Event.Asset.PropertyUpdateEvent;
import xyz.dowob.stockweb.Component.Method.CombineMethod;
import xyz.dowob.stockweb.Component.Method.EventCacheMethod;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Dto.Property.TransactionListDto;
import xyz.dowob.stockweb.Exception.*;
import xyz.dowob.stockweb.Model.Common.Asset;
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
import xyz.dowob.stockweb.Service.Common.Property.PropertyInfluxService;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static xyz.dowob.stockweb.Exception.RepositoryExceptions.ErrorEnum.INFLUXDB_WRITE_ERROR;
import static xyz.dowob.stockweb.Exception.TransactionExceptions.ErrorEnum.TRANSACTION_TYPE_NOT_FOUND;
import static xyz.dowob.stockweb.Exception.TransactionExceptions.ErrorEnum.USER_HAS_NOT_ENOUGH_ASSET;

/**
 * 有關於用戶交易訊息的業務邏輯
 *
 * @author yuan
 */
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    private final CurrencyRepository currencyRepository;

    private final CryptoRepository cryptoRepository;

    private final PropertyRepository propertyRepository;

    private final StockTwRepository stockTwRepository;

    private final SubscribeMethod subscribeMethod;

    private final CombineMethod combineMethod;

    private final PropertyInfluxService propertyInfluxService;

    private final EventCacheMethod eventCacheMethod;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${common.global_page_size:100}")
    private int globalPageSize;

    /**
     * TransactionService構造函數
     *
     * @param transactionRepository 交易數據庫
     * @param currencyRepository    貨幣數據庫
     * @param cryptoRepository      加密貨幣數據庫
     * @param propertyRepository    資產數據庫
     * @param stockTwRepository     台股數據庫
     * @param subscribeMethod       訂閱方法
     * @param combineMethod         組合方法
     * @param propertyInfluxService 資產InfluxDB服務
     * @param eventCacheMethod      事件緩存方法
     * @param eventPublisher        事件發布器
     */
    public TransactionService(TransactionRepository transactionRepository, CurrencyRepository currencyRepository, CryptoRepository cryptoRepository, PropertyRepository propertyRepository, StockTwRepository stockTwRepository, SubscribeMethod subscribeMethod, CombineMethod combineMethod, PropertyInfluxService propertyInfluxService, EventCacheMethod eventCacheMethod, ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.currencyRepository = currencyRepository;
        this.cryptoRepository = cryptoRepository;
        this.propertyRepository = propertyRepository;
        this.stockTwRepository = stockTwRepository;
        this.subscribeMethod = subscribeMethod;
        this.combineMethod = combineMethod;
        this.propertyInfluxService = propertyInfluxService;
        this.eventCacheMethod = eventCacheMethod;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 交易操作處理，包含買入、賣出、提款、存款
     * 買入: 扣除支付資產數量，增加交易資產數量
     * 賣出: 扣除交易資產數量，增加支付資產數量
     * 提款: 扣除交易資產數量，並計算淨流量寫入 InfluxDB
     * 存款: 增加交易資產數量，並計算淨流量寫入 InfluxDB
     *
     * @param user        用戶
     * @param transaction 交易
     */
    @Transactional(rollbackFor = Exception.class)
    public void operation(User user, TransactionListDto.TransactionDto transaction) throws UserExceptions, TransactionExceptions, RepositoryExceptions, AssetExceptions, FormatExceptions {
        String symbol = transaction.getSymbol().toUpperCase();
        String unit = transaction.getUnit().toUpperCase();
        Asset asset = getAsset(symbol);
        Asset unitAsset = getAsset(unit);
        List<Property> userUnitPropertyList = propertyRepository.findByAssetAndUser(unitAsset, user);
        List<Property> userSymbolPropertyList = propertyRepository.findByAssetAndUser(asset, user);
        Property userUnitProperty = combineMethod.combinePropertyValues(userUnitPropertyList);
        Property userSymbolProperty = combineMethod.combinePropertyValues(userSymbolPropertyList);
        switch (transaction.formatOperationTypeEnum()) {
            case BUY:
                if (userUnitProperty == null || userUnitProperty.getQuantity().compareTo(transaction.formatAmountAsBigDecimal()) < 0) {
                    throw new TransactionExceptions(USER_HAS_NOT_ENOUGH_ASSET, unit);
                }
                int diffAdd = userUnitProperty.getQuantity().compareTo(transaction.formatAmountAsBigDecimal());
                userUnitProperty.setQuantity(userUnitProperty.getQuantity().subtract(transaction.formatAmountAsBigDecimal()));
                if (userSymbolProperty != null) {
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().add(transaction.formatQuantityAsBigDecimal()));
                } else {
                    userSymbolProperty = new Property();
                    userSymbolProperty.setUser(user);
                    userSymbolProperty.setAsset(asset);
                    userSymbolProperty.setQuantity(transaction.formatQuantityAsBigDecimal());
                    if (asset instanceof StockTw) {
                        userSymbolProperty.setAssetName(symbol + "-" + ((StockTw) asset).getStockName());
                    } else {
                        userSymbolProperty.setAssetName(symbol);
                    }
                }
                if (transaction.getDescription() == null) {
                    userSymbolProperty.setDescription("");
                } else {
                    userSymbolProperty.setDescription(transaction.getDescription());
                }
                if (diffAdd == 0) {
                    propertyRepository.delete(userUnitProperty);
                } else {
                    propertyRepository.save(userUnitProperty);
                }
                propertyRepository.save(userSymbolProperty);
                subscribeMethod.subscribeProperty(userSymbolProperty, user);
                if (userSymbolProperty.getAsset() instanceof CryptoTradingPair cryptoTradingPair) {
                    if (cryptoTradingPair.isHasAnySubscribed()) {
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                } else if (userSymbolProperty.getAsset() instanceof StockTw stockTw) {
                    if (stockTw.isHasAnySubscribed()) {
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                }
                break;
            case SELL:
                if (userSymbolProperty == null || userSymbolProperty.getQuantity()
                                                                    .compareTo(transaction.formatQuantityAsBigDecimal()) < 0) {
                    throw new TransactionExceptions(USER_HAS_NOT_ENOUGH_ASSET, symbol);
                } else {
                    int diffSell = userSymbolProperty.getQuantity().compareTo(transaction.formatQuantityAsBigDecimal());
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().subtract(transaction.formatQuantityAsBigDecimal()));
                    if (userUnitProperty != null) {
                        userUnitProperty.setQuantity(userUnitProperty.getQuantity().add(transaction.formatAmountAsBigDecimal()));
                    } else {
                        userUnitProperty = new Property();
                        userUnitProperty.setUser(user);
                        userUnitProperty.setAsset(unitAsset);
                        userUnitProperty.setAssetName(unit);
                        userUnitProperty.setQuantity(transaction.formatAmountAsBigDecimal());
                    }
                    if (transaction.getDescription() == null) {
                        userSymbolProperty.setDescription("");
                    } else {
                        userSymbolProperty.setDescription(transaction.getDescription());
                    }
                    if (diffSell == 0) {
                        subscribeMethod.unsubscribeProperty(userSymbolProperty, user);
                        propertyRepository.delete(userSymbolProperty);
                        propertyRepository.save(userUnitProperty);
                        break;
                    } else {
                        propertyRepository.save(userUnitProperty);
                        propertyRepository.save(userSymbolProperty);
                    }
                    eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                }
                break;
            case WITHDRAW:
                if (userSymbolProperty == null || userSymbolProperty.getQuantity()
                                                                    .compareTo(transaction.formatQuantityAsBigDecimal()) < 0) {
                    throw new TransactionExceptions(USER_HAS_NOT_ENOUGH_ASSET, symbol);
                } else {
                    BigDecimal netFlow = propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal().negate(), asset);
                    if (userSymbolProperty.getQuantity().compareTo(transaction.formatQuantityAsBigDecimal()) == 0) {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> propertyInfluxService.writeNetFlowToInflux(netFlow.negate(),
                                                                                                                                     user));
                        Property finalUserSymbolProperty = userSymbolProperty;
                        future.whenComplete((f, e) -> {
                            if (e != null) {
                                throw new RuntimeException(new RepositoryExceptions(INFLUXDB_WRITE_ERROR, e.getMessage()));
                            } else {
                                subscribeMethod.unsubscribeProperty(finalUserSymbolProperty, user);
                                propertyRepository.delete(finalUserSymbolProperty);
                                eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                            }
                        });
                    } else {
                        userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().subtract(transaction.formatQuantityAsBigDecimal()));
                        if (transaction.getDescription() == null) {
                            userSymbolProperty.setDescription("");
                        } else {
                            userSymbolProperty.setDescription(transaction.getDescription());
                        }
                        propertyRepository.save(userSymbolProperty);
                        propertyInfluxService.writeNetFlowToInflux(netFlow.negate(), user);
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                }
                break;
            case DEPOSIT:
                if (userSymbolProperty != null) {
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().add(transaction.formatQuantityAsBigDecimal()));
                } else {
                    userSymbolProperty = new Property();
                    userSymbolProperty.setUser(user);
                    userSymbolProperty.setAsset(asset);
                    userSymbolProperty.setQuantity(transaction.formatQuantityAsBigDecimal());
                    if (asset instanceof StockTw) {
                        userSymbolProperty.setAssetName(symbol + "-" + ((StockTw) asset).getStockName());
                    } else {
                        userSymbolProperty.setAssetName(symbol);
                    }
                }
                if (transaction.getDescription() == null) {
                    userSymbolProperty.setDescription("");
                } else {
                    userSymbolProperty.setDescription(transaction.getDescription());
                }
                propertyRepository.save(userSymbolProperty);
                subscribeMethod.subscribeProperty(userSymbolProperty, user);
                switch (asset) {
                    case StockTw stockTw -> {
                        if (stockTw.isHasAnySubscribed()) {
                            propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal(), stockTw);
                            eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                        } else {
                            eventCacheMethod.addEventCache(userSymbolProperty, transaction.formatQuantityAsBigDecimal());
                            eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                        }
                    }
                    case CryptoTradingPair cryptoTradingPair -> {
                        if (cryptoTradingPair.isHasAnySubscribed()) {
                            propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal(), cryptoTradingPair);
                        } else {
                            eventCacheMethod.addEventCache(userSymbolProperty, transaction.formatQuantityAsBigDecimal());
                        }
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                    case Currency currency -> {
                        propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal(), currency);
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                    default -> {
                    }
                }
                break;
            case OTHER:
                throw new TransactionExceptions(TRANSACTION_TYPE_NOT_FOUND);
        }
        Transaction recordTransaction = new Transaction();
        recordTransaction.setUser(user);
        recordTransaction.setType(transaction.formatOperationTypeEnum());
        recordTransaction.setAsset(asset);
        recordTransaction.setAssetName(symbol);
        recordTransaction.setAmount(transaction.formatAmountAsBigDecimal());
        recordTransaction.setQuantity(transaction.formatQuantityAsBigDecimal());
        if (unitAsset instanceof Currency) {
            recordTransaction.setUnitCurrency(unitAsset);
            recordTransaction.setUnitCurrencyName(unit);
        } else {
            recordTransaction.setUnitCurrency(user.getPreferredCurrency());
            recordTransaction.setUnitCurrencyName(user.getPreferredCurrency().getCurrency());
        }
        recordTransaction.setTransactionDate(transaction.formatTransactionDate());
        if (transaction.getDescription() == null) {
            recordTransaction.setDescription("");
        } else {
            recordTransaction.setDescription(transaction.getDescription());
        }
        transactionRepository.save(recordTransaction);
    }

    /**
     * 取得資料庫中資產
     *
     * @param symbol 資產名稱
     *
     * @return 資產
     */
    private Asset getAsset(String symbol) throws AssetExceptions {
        String key = symbol.toUpperCase();
        String keyCrypto = symbol.toUpperCase() + "USDT";
        Asset asset;
        asset = cryptoRepository.findByTradingPair(keyCrypto).orElse(null);
        if (asset != null) {
            return asset;
        }
        asset = currencyRepository.findByCurrency(key).orElse(null);
        if (asset != null) {
            return asset;
        }
        asset = stockTwRepository.findByStockCode(key).orElse(null);
        if (asset != null) {
            return asset;
        }
        throw new AssetExceptions(AssetExceptions.ErrorEnum.ASSET_NOT_FOUND, symbol);
    }

    /**
     * 查詢用戶所有交易紀錄
     *
     * @param user 用戶
     *
     * @return 交易紀錄
     *
     * @throws JsonProcessingException JSON處理錯誤
     */
    @MeaninglessData
    public String getUserAllTransaction(User user, int page) throws JsonProcessingException {
        Pageable pageable = PageRequest.of(page - 1, globalPageSize);
        List<TransactionListDto.TransactionDto> transactions = new ArrayList<>();
        Page<Transaction> userTransactions = transactionRepository.findByUserOrderByTransactionDateDesc(user, pageable);
        for (Transaction transaction : userTransactions.getContent()) {
            TransactionListDto.TransactionDto transactionDto = new TransactionListDto.TransactionDto();
            transactionDto.setId(String.valueOf(transaction.getId()));
            transactionDto.setSymbol(transaction.getAssetName());
            transactionDto.setUnit(transaction.getUnitCurrencyName());
            transactionDto.setAmount(transaction.getAmount().stripTrailingZeros().toPlainString());
            transactionDto.setQuantity(transaction.getQuantity().stripTrailingZeros().toPlainString());
            transactionDto.setDescription(transaction.getDescription());
            DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String formattedDate = transaction.getTransactionDate().format(outputFormat);
            transactionDto.setDate(formattedDate);
            transactionDto.setType(String.valueOf(transaction.getType()));
            transactions.add(transactionDto);
        }
        Map<String, Object> result = new HashMap<>(Map.of("transactions",
                                                          transactions,
                                                          "totalPages",
                                                          userTransactions.getTotalPages(),
                                                          "currentPage",
                                                          userTransactions.getNumber() + 1));
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(result);
    }
}
