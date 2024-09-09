package xyz.dowob.stockweb.Service.User;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Event.Asset.PropertyUpdateEvent;
import xyz.dowob.stockweb.Component.Method.CombineMethod;
import xyz.dowob.stockweb.Component.Method.EventCacheMethod;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Dto.Property.TransactionListDto;
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

    Logger logger = LoggerFactory.getLogger(TransactionService.class);

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
    @Autowired
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
    public void operation(User user, TransactionListDto.TransactionDto transaction) {
        logger.debug("User: {}", user);
        logger.debug("紀錄交易: {}", transaction);
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
                logger.debug("買入{}", symbol);
                if (userUnitProperty == null || userUnitProperty.getQuantity().compareTo(transaction.formatAmountAsBigDecimal()) < 0) {
                    logger.debug("{} 資產不足", unit);
                    throw new IllegalArgumentException("用戶資產中沒有沒有足夠的" + unit + "來完成交易");
                }
                logger.debug("扣除 {} 資產數量", unit);
                int diffAdd = userUnitProperty.getQuantity().compareTo(transaction.formatAmountAsBigDecimal());
                userUnitProperty.setQuantity(userUnitProperty.getQuantity().subtract(transaction.formatAmountAsBigDecimal()));

                if (userSymbolProperty != null) {
                    logger.debug("用戶已有資產");
                    logger.debug("增加 {} 資產數量", symbol);
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().add(transaction.formatQuantityAsBigDecimal()));
                } else {
                    logger.debug("建立 {} 資產", symbol);
                    userSymbolProperty = new Property();
                    logger.debug("設定用戶{}", user);
                    userSymbolProperty.setUser(user);
                    logger.debug("設定買入對象{}", asset);
                    userSymbolProperty.setAsset(asset);
                    logger.debug("設定交易數量{}", transaction.formatQuantityAsBigDecimal());
                    userSymbolProperty.setQuantity(transaction.formatQuantityAsBigDecimal());

                    if (asset instanceof StockTw) {
                        logger.debug("交易對象是台股");
                        logger.debug("設定買入對象名稱{}", (symbol + "-" + ((StockTw) asset).getStockName()));
                        userSymbolProperty.setAssetName(symbol + "-" + ((StockTw) asset).getStockName());
                    } else {
                        logger.debug("設定買入對象名稱{}", symbol);
                        userSymbolProperty.setAssetName(symbol);
                    }
                }

                if (transaction.getDescription() == null) {
                    logger.debug("沒有備註，使用預設值");
                    userSymbolProperty.setDescription("");
                } else {
                    logger.debug("備註為{}", transaction.getDescription());
                    userSymbolProperty.setDescription(transaction.getDescription());
                }


                if (diffAdd == 0) {
                    logger.debug("刪除支付資產");
                    propertyRepository.delete(userUnitProperty);
                } else {
                    propertyRepository.save(userUnitProperty);
                }
                propertyRepository.save(userSymbolProperty);
                subscribeMethod.subscribeProperty(userSymbolProperty, user);
                logger.debug("儲存 {} 資產變更", symbol);

                if (userSymbolProperty.getAsset() instanceof CryptoTradingPair cryptoTradingPair) {
                    if (cryptoTradingPair.isHasAnySubscribed()) {
                        logger.debug("發布更新用戶資產事件");
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                } else if (userSymbolProperty.getAsset() instanceof StockTw stockTw) {
                    if (stockTw.isHasAnySubscribed()) {
                        logger.debug("發布更新用戶資產事件");
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                }

                break;

            case SELL:
                logger.debug("賣出 {}", symbol);
                if (userSymbolProperty == null || userSymbolProperty.getQuantity()
                                                                    .compareTo(transaction.formatQuantityAsBigDecimal()) < 0) {
                    logger.debug("{} 資產不足", symbol);
                    throw new IllegalArgumentException("用戶資產中沒有沒有足夠的" + symbol + "來完成交易");
                } else {
                    int diffSell = userSymbolProperty.getQuantity().compareTo(transaction.formatQuantityAsBigDecimal());
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().subtract(transaction.formatQuantityAsBigDecimal()));
                    logger.debug("扣除 {} 資產數量", symbol);
                    logger.debug("建立 {} 資產", unit);

                    if (userUnitProperty != null) {
                        logger.debug("用戶已有資產");
                        logger.debug("增加 {} 資產數量", unit);
                        userUnitProperty.setQuantity(userUnitProperty.getQuantity().add(transaction.formatAmountAsBigDecimal()));
                    } else {
                        userUnitProperty = new Property();
                        logger.debug("設定用戶 {}", user);
                        userUnitProperty.setUser(user);
                        logger.debug("設定賣出貨幣 {}", unitAsset);
                        userUnitProperty.setAsset(unitAsset);
                        logger.debug("設定交易對象名稱 {}", unit);
                        userUnitProperty.setAssetName(unit);
                        logger.debug("設定交易數量 {}", transaction.formatAmountAsBigDecimal());
                        userUnitProperty.setQuantity(transaction.formatAmountAsBigDecimal());
                    }

                    if (transaction.getDescription() == null) {
                        logger.debug("沒有備註，使用預設值");
                        userSymbolProperty.setDescription("");
                    } else {
                        logger.debug("備註為{}", transaction.getDescription());
                        userSymbolProperty.setDescription(transaction.getDescription());
                    }

                    if (diffSell == 0) {
                        logger.debug("刪除 {} 資產", symbol);
                        subscribeMethod.unsubscribeProperty(userSymbolProperty, user);
                        propertyRepository.delete(userSymbolProperty);
                        propertyRepository.save(userUnitProperty);
                        break;
                    } else {
                        logger.debug("儲存 {} 資產變更", unit);
                        propertyRepository.save(userUnitProperty);
                        propertyRepository.save(userSymbolProperty);
                    }
                    logger.debug("發布更新用戶資產事件");
                    eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                }
                break;

            case WITHDRAW:
                logger.debug("提款{}", symbol);
                if (userSymbolProperty == null || userSymbolProperty.getQuantity()
                                                                    .compareTo(transaction.formatQuantityAsBigDecimal()) < 0) {
                    logger.debug("{} 資產不足", symbol);
                    throw new IllegalArgumentException("用戶資產中沒有沒有足夠的" + symbol + "來完成交易");
                } else {
                    BigDecimal netFlow = propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal().negate(), asset);

                    if (userSymbolProperty.getQuantity().compareTo(transaction.formatQuantityAsBigDecimal()) == 0) {

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> propertyInfluxService.writeNetFlowToInflux(netFlow.negate(),
                                                                                                                                     user));
                        Property finalUserSymbolProperty = userSymbolProperty;
                        future.whenComplete((f, e) -> {
                            if (e != null) {
                                logger.error("寫入 InfluxDB 時發生錯誤", e);
                                throw new RuntimeException("寫入 InfluxDB 時發生錯誤");
                            } else {
                                logger.debug("寫入 InfluxDB 成功");
                                logger.debug("刪除 {} 資產", symbol);
                                subscribeMethod.unsubscribeProperty(finalUserSymbolProperty, user);
                                propertyRepository.delete(finalUserSymbolProperty);
                                logger.debug("發布更新用戶資產事件");
                                eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                            }
                        });
                    } else {
                        userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().subtract(transaction.formatQuantityAsBigDecimal()));

                        if (transaction.getDescription() == null) {
                            logger.debug("沒有備註，使用預設值");
                            userSymbolProperty.setDescription("");
                        } else {
                            logger.debug("備註為{}", transaction.getDescription());
                            userSymbolProperty.setDescription(transaction.getDescription());
                        }
                        propertyRepository.save(userSymbolProperty);
                        propertyInfluxService.writeNetFlowToInflux(netFlow.negate(), user);
                        logger.debug("發布更新用戶資產事件");
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                }

                logger.debug("儲存 {} 資產變更", symbol);
                break;

            case DEPOSIT:
                logger.debug("存款{}", symbol);

                if (userSymbolProperty != null) {
                    logger.debug("用戶已有資產");
                    logger.debug("增加 {} 資產數量", symbol);
                    userSymbolProperty.setQuantity(userSymbolProperty.getQuantity().add(transaction.formatQuantityAsBigDecimal()));
                } else {
                    logger.debug("建立 {} 資產", symbol);
                    userSymbolProperty = new Property();
                    logger.debug("設定用戶{}", user);
                    userSymbolProperty.setUser(user);
                    logger.debug("設定存款對象{}", asset);
                    userSymbolProperty.setAsset(asset);
                    logger.debug("設定交易數量{}", transaction.formatQuantityAsBigDecimal());
                    userSymbolProperty.setQuantity(transaction.formatQuantityAsBigDecimal());

                    if (asset instanceof StockTw) {
                        logger.debug("交易對象是台股");
                        logger.debug("設定存款對象名稱{}", (symbol + "-" + ((StockTw) asset).getStockName()));
                        userSymbolProperty.setAssetName(symbol + "-" + ((StockTw) asset).getStockName());
                    } else {
                        logger.debug("設定存款對象名稱{}", symbol);
                        userSymbolProperty.setAssetName(symbol);
                    }
                }
                if (transaction.getDescription() == null) {
                    logger.debug("沒有備註，使用預設值");
                    userSymbolProperty.setDescription("");
                } else {
                    logger.debug("備註為{}", transaction.getDescription());
                    userSymbolProperty.setDescription(transaction.getDescription());
                }

                propertyRepository.save(userSymbolProperty);
                subscribeMethod.subscribeProperty(userSymbolProperty, user);
                logger.debug("儲存 {} 資產變更", symbol);

                switch (asset) {
                    case StockTw stockTw -> {
                        if (stockTw.isHasAnySubscribed()) {
                            logger.debug("寫入 InfluxDB");
                            propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal(), stockTw);
                            logger.debug("發布更新用戶資產事件");
                            eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                        } else {
                            logger.debug("等待事件監聽器回傳，存入事件緩存");
                            eventCacheMethod.addEventCache(userSymbolProperty, transaction.formatQuantityAsBigDecimal());
                            logger.debug("發布更新用戶資產事件");
                            eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                        }
                    }
                    case CryptoTradingPair cryptoTradingPair -> {
                        if (cryptoTradingPair.isHasAnySubscribed()) {
                            logger.debug("寫入 InfluxDB");
                            propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal(), cryptoTradingPair);
                        } else {
                            logger.debug("等待事件監聽器回傳，存入事件緩存");
                            eventCacheMethod.addEventCache(userSymbolProperty, transaction.formatQuantityAsBigDecimal());
                        }
                        logger.debug("發布更新用戶資產事件");
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                    case Currency currency -> {
                        propertyInfluxService.calculateNetFlow(transaction.formatQuantityAsBigDecimal(), currency);
                        logger.debug("發布更新用戶資產事件");
                        eventPublisher.publishEvent(new PropertyUpdateEvent(this, user));
                    }
                    default -> {
                    }
                }
                break;

            case OTHER:
                logger.debug("錯誤，其他交易類型不支持");
                throw new IllegalStateException("錯誤，其他交易類型不支持");
        }
        logger.debug("儲存交易");
        Transaction recordTransaction = new Transaction();
        logger.debug("設定交易紀錄用戶 {}", user);
        recordTransaction.setUser(user);
        logger.debug("設定交易紀錄類型 {}", transaction.formatOperationTypeEnum());
        recordTransaction.setType(transaction.formatOperationTypeEnum());
        logger.debug("設定交易紀錄資產 {}", asset);
        recordTransaction.setAsset(asset);
        logger.debug("設定交易紀錄資產名稱 {}", symbol);
        recordTransaction.setAssetName(symbol);
        logger.debug("設定交易金額數量 {}", transaction.formatAmountAsBigDecimal());
        recordTransaction.setAmount(transaction.formatAmountAsBigDecimal());
        logger.debug("設定交易數量 {}", transaction.formatQuantityAsBigDecimal());
        recordTransaction.setQuantity(transaction.formatQuantityAsBigDecimal());

        if (unitAsset instanceof Currency) {
            logger.debug("設定交易幣別 {}", unitAsset);
            recordTransaction.setUnitCurrency(unitAsset);
            logger.debug("設定交易幣別名稱 {}", unit);
            recordTransaction.setUnitCurrencyName(unit);
        } else {
            logger.debug("設定交易幣別為用戶預設 {}", user.getPreferredCurrency().getCurrency());
            recordTransaction.setUnitCurrency(user.getPreferredCurrency());
            logger.debug("設定交易幣別名稱 {}", user.getPreferredCurrency().getCurrency());
            recordTransaction.setUnitCurrencyName(user.getPreferredCurrency().getCurrency());
        }


        logger.debug("設定交易日期 {}", transaction.formatTransactionDate());
        recordTransaction.setTransactionDate(transaction.formatTransactionDate());

        if (transaction.getDescription() == null) {
            logger.debug("沒有備註，使用預設值");
            recordTransaction.setDescription("");
        } else {
            logger.debug("備註為{}", transaction.getDescription());
            recordTransaction.setDescription(transaction.getDescription());
        }

        transactionRepository.save(recordTransaction);
        logger.debug("儲存交易紀錄");

    }

    /**
     * 取得資料庫中資產
     *
     * @param symbol 資產名稱
     *
     * @return 資產
     */

    private Asset getAsset(String symbol) {
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

        throw new IllegalArgumentException("找不到可以轉換的資產: " + symbol);
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
    public String getUserAllTransaction(User user, int page) throws JsonProcessingException {
        Pageable pageable = PageRequest.of(page - 1, globalPageSize);

        List<TransactionListDto.TransactionDto> transactions = new ArrayList<>();
        Page<Transaction> userTransactions = transactionRepository.findByUserOrderByTransactionDateDesc(user, pageable);

        for (Transaction transaction : userTransactions.getContent()) {
            TransactionListDto.TransactionDto transactionDto = new TransactionListDto.TransactionDto();
            logger.debug("交易紀錄: {}", transaction);
            transactionDto.setId(String.valueOf(transaction.getId()));
            logger.debug("交易ID: {}", transaction.getId());
            transactionDto.setSymbol(transaction.getAssetName());
            logger.debug("交易對象名稱: {}", transaction.getAssetName());
            transactionDto.setUnit(transaction.getUnitCurrencyName());
            logger.debug("交易對象貨幣名稱: {}", transaction.getUnitCurrencyName());
            transactionDto.setAmount(transaction.getAmount().stripTrailingZeros().toPlainString());
            logger.debug("交易對象金額: {}", transaction.getAmount().stripTrailingZeros().toPlainString());
            transactionDto.setQuantity(transaction.getQuantity().stripTrailingZeros().toPlainString());
            logger.debug("交易對象數量: {}", transaction.getQuantity().stripTrailingZeros().toPlainString());
            transactionDto.setDescription(transaction.getDescription());
            logger.debug("交易描述: {}", transaction.getDescription());


            DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String formattedDate = transaction.getTransactionDate().format(outputFormat);
            transactionDto.setDate(formattedDate);
            logger.debug("交易日期: {}", formattedDate);
            transactionDto.setType(String.valueOf(transaction.getType()));
            logger.debug("交易類型: {}", transaction.getType());
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
