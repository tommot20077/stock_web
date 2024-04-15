package xyz.dowob.stockweb.Service.Common.Property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.query.FluxTable;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Component.Handler.AssetHandler;
import xyz.dowob.stockweb.Component.Method.ChartMethod;
import xyz.dowob.stockweb.Component.Method.SubscribeMethod;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Enum.OperationType;
import xyz.dowob.stockweb.Enum.TransactionType;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PropertyService {
    private final StockTwRepository stockTwRepository;
    private final PropertyRepository propertyRepository;
    private final CurrencyRepository currencyRepository;
    private final CryptoRepository cryptoRepository;
    private final TransactionRepository transactionRepository;
    private final SubscribeMethod subscribeMethod;
    private final AssetInfluxMethod assetInfluxMethod;
    private final PropertyInfluxService propertyInfluxService;
    private final AssetHandler assetHandler;
    private final ChartMethod chartMethod;
    Logger logger = LoggerFactory.getLogger(PropertyService.class);

    @Autowired
    public PropertyService(StockTwRepository stockTwRepository, PropertyRepository propertyRepository, CurrencyRepository currencyRepository, CryptoRepository cryptoRepository, TransactionRepository transactionRepository, SubscribeMethod subscribeMethod, AssetInfluxMethod assetInfluxMethod, PropertyInfluxService propertyInfluxService, AssetHandler assetHandler, ChartMethod chartMethod) {
        this.stockTwRepository = stockTwRepository;
        this.propertyRepository = propertyRepository;
        this.currencyRepository = currencyRepository;
        this.cryptoRepository = cryptoRepository;
        this.transactionRepository = transactionRepository;
        this.subscribeMethod = subscribeMethod;
        this.assetInfluxMethod = assetInfluxMethod;
        this.propertyInfluxService = propertyInfluxService;
        this.assetHandler = assetHandler;
        this.chartMethod = chartMethod;
    }

    @Transactional(rollbackOn = Exception.class)
    public void modifyStock(User user, PropertyListDto.PropertyDto request) {
        logger.debug("讀取資料: " + request);
        String symbol = request.getSymbol();
        logger.debug("股票全稱: " + symbol);
        String stockCode = request.extractStockCode(request.getSymbol());
        logger.debug("股票代碼: " + stockCode);
        String description = request.getDescription();
        logger.debug("描述: " + description);
        BigDecimal quantity = request.formatQuantityBigDecimal();
        logger.debug("數量: " + quantity);
        StockTw stock = null;

        if (request.formatOperationTypeEnum() == OperationType.ADD) {
            logger.debug("新增或更新操作");
            stock = stockTwRepository.findByStockCode(stockCode).orElseThrow(() -> new RuntimeException("找不到指定的股票代碼"));
            logger.debug("找到股票: " + stock);
        }

        switch (request.formatOperationTypeEnum()) {
            case ADD:
                logger.debug("新增");
                Property propertyToAdd;
                if (request.getId() != null) {
                    logger.debug("id: " + request.getId() + "新增時不應該有 id");
                    throw new RuntimeException("新增時不應該有 id");
                }

                List<Property> propertyList = propertyRepository.findByAssetAndUser(stock, user);
                if (!propertyList.isEmpty()) {
                    logger.debug("已經有持有此股票");
                    propertyToAdd = propertyList.getFirst();
                    propertyToAdd.setQuantity(quantity.add(propertyToAdd.getQuantity()));
                    logger.debug("新增時的數量: " + propertyToAdd.getQuantity());
                } else {
                    logger.debug("沒有持有此股票");
                    propertyToAdd = new Property();
                    propertyToAdd.setUser(user);
                    propertyToAdd.setAsset(stock);
                    propertyToAdd.setQuantity(quantity);

                    if (!symbol.contains("-") && Objects.requireNonNull(stock).getStockName() != null) {
                        logger.debug("不包含 '-'，使用股票代碼 + 股票名稱作為名稱");
                        propertyToAdd.setAssetName(stock.getStockCode() + "-" + stock.getStockName());
                    } else {
                        propertyToAdd.setAssetName(symbol);
                    }
                }

                if (description!= null) {
                    propertyToAdd.setDescription(description);
                } else {
                    logger.debug("沒有備註，使用預設值");
                    propertyToAdd.setDescription("");
                }

                propertyRepository.save(propertyToAdd);
                logger.debug("新增成功");
                recordTransaction(user, propertyToAdd, request.formatOperationTypeEnum());
                subscribeMethod.subscribeProperty(propertyToAdd, user);
                break;

            case REMOVE:
                logger.debug("刪除");
                if (request.getId() == null) {
                    logger.debug("刪除時必須有 id");
                    throw new RuntimeException("刪除時必須有 id");
                }

                Property propertyToRemove = propertyRepository.findById(request.getId()).orElseThrow(() -> new RuntimeException("找不到指定的持有股票"));

                if (!propertyToRemove.getUser().equals(user)) {
                    logger.debug("無法刪除其他人的持有股票");
                    throw new RuntimeException("無法刪除其他人的持有股票");
                }
                subscribeMethod.unsubscribeProperty(propertyToRemove, user);
                recordTransaction(user, propertyToRemove, request.formatOperationTypeEnum());
                propertyRepository.delete(propertyToRemove);
                logger.debug("刪除成功");
                break;

            case UPDATE:
                if (request.getId() == null) {
                    logger.debug("更新時必須有 id");
                    throw new RuntimeException("更新時必須有 id");
                }

                Property propertyToUpdate = propertyRepository.findById(request.getId()).orElseThrow(() -> new RuntimeException("找不到指定的持有股票"));

                if (!propertyToUpdate.getUser().equals(user)) {
                    logger.debug("無法更新其他人的持有股票");
                    throw new RuntimeException("無法更新其他人的持有股票");
                }

                logger.debug("更新股票");
                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    logger.debug("數量必須大於 0");
                    throw new RuntimeException("數量必須大於 0");
                } else {
                    propertyToUpdate.setQuantity(quantity);
                }

                if (description!= null) {
                    propertyToUpdate.setDescription(description);
                } else {
                    logger.debug("沒有備註，使用預設值");
                    propertyToUpdate.setDescription("");
                }

                propertyRepository.save(propertyToUpdate);
                logger.debug("更新成功");
                recordTransaction(user, propertyToUpdate, request.formatOperationTypeEnum());
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");

        }
    }
    @Transactional(rollbackOn = Exception.class)
    public void modifyCurrency(User user, PropertyListDto.PropertyDto request) {
        logger.debug("讀取資料: " + request);
        Long id = request.getId();
        logger.debug("id: " + id);
        String description = request.getDescription();
        logger.debug("描述: " + description);
        BigDecimal quantity = request.formatQuantityBigDecimal();
        logger.debug("數量: " + quantity);

        Currency currency = null;
        if (request.formatOperationTypeEnum() == OperationType.ADD) {
            logger.debug("新增或更新操作");
            currency = currencyRepository.findByCurrency(request.getSymbol().toUpperCase()).orElseThrow(() -> new RuntimeException("找不到指定的貨幣代碼"));
            logger.debug("貨幣: " + currency);
        }


        switch (request.formatOperationTypeEnum()) {
            case ADD:
                logger.debug("新增");
                Property propertyToAdd;
                if (id != null) {
                    logger.debug("id: " + id + "新增時不應該有 id");
                    throw new RuntimeException("新增時不應該有 id");
                }

                List<Property> propertyList = propertyRepository.findByAssetAndUser(currency, user);
                if (propertyList.isEmpty()) {
                    logger.debug("找不到指定的持有貨幣");
                    propertyToAdd = new Property();
                    propertyToAdd.setUser(user);
                    propertyToAdd.setAsset(currency);
                    propertyToAdd.setAssetName(Objects.requireNonNull(currency).getCurrency());
                    propertyToAdd.setQuantity(quantity);
                } else {
                    logger.debug("已經有持有此貨幣");
                    propertyToAdd = propertyList.getFirst();
                    logger.debug("新增時的數量: " + quantity.add(propertyToAdd.getQuantity()));
                    propertyToAdd.setQuantity(quantity.add(propertyToAdd.getQuantity()));
                }

                if (description!= null) {
                    propertyToAdd.setDescription(description);
                } else {
                    logger.debug("沒有備註，使用預設值");
                    propertyToAdd.setDescription("");
                }

                propertyRepository.save(propertyToAdd);
                subscribeMethod.subscribeProperty(propertyToAdd, user);
                logger.debug("新增成功");
                recordTransaction(user, propertyToAdd, request.formatOperationTypeEnum());
                break;

            case REMOVE:
                logger.debug("刪除");
                if (id == null) {
                    logger.debug("刪除時必須有 id");
                    throw new RuntimeException("刪除時必須有 id");
                }

                Property propertyToRemove = propertyRepository.findById(id).orElseThrow(() -> new RuntimeException("找不到指定的持有貨幣"));

                if (!propertyToRemove.getUser().equals(user)) {
                    logger.debug("無法刪除其他人的持有貨幣");
                    throw new RuntimeException("無法刪除其他人的持有貨幣");
                }
                recordTransaction(user, propertyToRemove, request.formatOperationTypeEnum());
                subscribeMethod.unsubscribeProperty(propertyToRemove, user);
                propertyRepository.delete(propertyToRemove);
                logger.debug("刪除成功");
                break;

            case UPDATE:
                if (id == null) {
                    logger.debug("更新時必須有 id");
                    throw new RuntimeException("更新時必須有 id");
                }

                Property propertyToUpdate = propertyRepository.findById(id).orElseThrow(() -> new RuntimeException("找不到指定的持有貨幣"));

                if (!propertyToUpdate.getUser().equals(user)) {
                    logger.debug("無法更新其他人的持有貨幣");
                    throw new RuntimeException("無法更新其他人的持有貨幣");
                }

                logger.debug("更新貨幣");

                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    logger.debug("數量必須大於 0");
                    throw new RuntimeException("數量必須大於 0");
                } else {
                    propertyToUpdate.setQuantity(quantity);
                }

                if (description!= null) {
                    propertyToUpdate.setDescription(description);
                } else {
                    logger.debug("沒有備註，使用預設值");
                    propertyToUpdate.setDescription("");
                }
                propertyRepository.save(propertyToUpdate);
                logger.debug("更新成功");
                recordTransaction(user, propertyToUpdate, request.formatOperationTypeEnum());
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");
        }
    }
    @Transactional(rollbackOn = Exception.class)
    public void modifyCrypto(User user, PropertyListDto.PropertyDto request) {
        logger.debug("讀取資料: " + request);
        Long id = request.getId();
        logger.debug("id: " + id);
        String description = request.getDescription();
        logger.debug("描述: " + description);
        BigDecimal quantity = request.formatQuantityBigDecimal();
        logger.debug("數量: " + quantity);
        String cryptoTradingPair = (request.getSymbol() + "USDT").toUpperCase();
        logger.debug("加密貨幣: " + cryptoTradingPair);

        CryptoTradingPair tradingPair = null;
        if (request.formatOperationTypeEnum() == OperationType.ADD) {
            logger.debug("新增或更新操作");
            tradingPair = cryptoRepository.findByTradingPair(cryptoTradingPair).orElseThrow(() -> new RuntimeException("找不到指定的加密貨幣"));
            logger.debug("虛擬貨幣: " + tradingPair);
        }

        switch (request.formatOperationTypeEnum()) {
            case ADD:
                Property propertyToAdd;
                logger.debug("新增");

                if (id != null) {
                    logger.debug("id: " + id + "新增時不應該有 id");
                    throw new RuntimeException("新增時不應該有 id");
                }

                List<Property> properties = propertyRepository.findByAssetAndUser(tradingPair, user);
                if (properties.isEmpty()) {
                    logger.debug("找不到指定的持有加密貨幣");
                    propertyToAdd = new Property();
                    propertyToAdd.setUser(user);
                    propertyToAdd.setAsset(tradingPair);
                    propertyToAdd.setAssetName(request.getSymbol().toUpperCase());
                    propertyToAdd.setQuantity(quantity);
                } else {
                    logger.debug("找到指定的持有加密貨幣");
                    propertyToAdd = properties.getFirst();
                    propertyToAdd.setQuantity(quantity.add(propertyToAdd.getQuantity()));
                    logger.debug("新增時的數量: " + quantity.add(propertyToAdd.getQuantity()));
                }

                if (description!= null) {
                    propertyToAdd.setDescription(description);
                } else {
                    logger.debug("沒有備註，使用預設值");
                    propertyToAdd.setDescription("");
                }
                propertyRepository.save(propertyToAdd);
                logger.debug("新增成功");
                recordTransaction(user, propertyToAdd, request.formatOperationTypeEnum());
                subscribeMethod.subscribeProperty(propertyToAdd, user);
                break;

            case REMOVE:
                logger.debug("刪除");
                if (id == null) {
                    logger.debug("刪除時必須有 id");
                    throw new RuntimeException("刪除時必須有 id");
                }

                Property propertyToRemove = propertyRepository.findById(id).orElseThrow(() ->new IllegalStateException("找不到指定的持有加密貨幣"));

                if (!propertyToRemove.getUser().equals(user)) {
                    logger.debug("無法刪除其他人的持有加密貨幣");
                    throw new RuntimeException("無法刪除其他人的持有加密貨幣");
                }

                subscribeMethod.unsubscribeProperty(propertyToRemove, user);
                recordTransaction(user, propertyToRemove, request.formatOperationTypeEnum());
                propertyRepository.delete(propertyToRemove);
                logger.debug("刪除成功");
                break;

            case UPDATE:
                logger.debug("更新");
                if (id == null) {
                    logger.debug("更新時必須有 id");
                    throw new RuntimeException("更新時必須有 id");
                }

                Property propertyToUpdate = propertyRepository.findById(id).orElseThrow(() -> new IllegalStateException("找不到指定的持有加密貨幣"));

                if (!propertyToUpdate.getUser().equals(user)) {
                    logger.debug("無法更新其他人的持有加密貨幣");
                    throw new RuntimeException("無法更新其他人的持有加密貨幣");
                }

                logger.debug("更新加密貨幣");

                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    logger.debug("數量必須大於 0");
                    throw new RuntimeException("數量必須大於 0");
                } else {
                    propertyToUpdate.setQuantity(quantity);
                }

                if (description!= null) {
                    propertyToUpdate.setDescription(description);
                } else {
                    logger.debug("沒有備註，使用預設值");
                    propertyToUpdate.setDescription("");
                }

                propertyRepository.save(propertyToUpdate);
                logger.debug("更新成功");
                recordTransaction(user, propertyToUpdate, request.formatOperationTypeEnum());
                break;

            default:
                logger.debug("不支援的操作類型");
                throw new RuntimeException("不支援的操作類型");

        }
    }
    @Async
    protected void recordTransaction(User user, Property property, OperationType operationType) {
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
        } else if (Objects.equals(operationType.toString(), "REMOVE")) {
            transaction.setType(TransactionType.WITHDRAW);
            logger.debug("設定交易類型: 取款");
            transaction.setDescription("系統自動備註: 刪除持有" + property.getAssetName() + "數量: " + property.getQuantity().stripTrailingZeros().toPlainString());
        } else if (Objects.equals(operationType.toString(), "UPDATE")) {
            transaction.setType(TransactionType.UPDATE);
            logger.debug("設定交易類型: 更新");
            transaction.setDescription("系統自動備註: 更新持有" + property.getAssetName() + "數量: " + property.getQuantity().stripTrailingZeros().toPlainString());
        }
        logger.debug("設定交易金額: " + property.getQuantity());

        transaction.setAsset(property.getAsset());
        transaction.setAssetName(property.getAssetName());
        transaction.setAmount(BigDecimal.valueOf(0));
        transaction.setQuantity(property.getQuantity());
        transaction.setUnitCurrency(user.getPreferredCurrency());
        transaction.setUnitCurrencyName(user.getPreferredCurrency().getCurrency());
        transaction.setTransactionDate(LocalDateTime.now());
        transactionRepository.save(transaction);
        logger.debug("儲存交易紀錄");
    }


    public List<PropertyListDto.getAllPropertiesDto> getUserAllProperties(User user, boolean isFormattedToPreferredCurrency) {
        List<Property> propertyList = propertyRepository.findAllByUser(user);
        logger.debug("格式資料: " + propertyList);
        Currency Usd = currencyRepository.findByCurrency("USD").orElseThrow(() -> new RuntimeException("系統找不到 USD 貨幣兌，請聯繫管理員"));
        Map<String, Property> propertyMap = propertyList.stream()
                .collect(Collectors.toMap(
                        property -> property.getAsset().getId().toString(),
                        property -> property,
                        (existing, replacement) -> {
                            existing.setQuantity(existing.getQuantity().add(replacement.getQuantity()));
                            return existing;
                        })
                );
        return new ArrayList<>(propertyMap.values()).stream()
                .map(property -> {
                    BigDecimal currentPrice = getCurrentPrice(property.getAsset());
                    logger.debug("目前價格: " + currentPrice);
                    BigDecimal exchangeRate;
                    if (isFormattedToPreferredCurrency) {
                        exchangeRate = assetHandler.exrateToPreferredCurrency(property.getAsset(), currentPrice, user.getPreferredCurrency());
                    } else {
                        exchangeRate = assetHandler.exrateToPreferredCurrency(property.getAsset(), currentPrice, Usd);
                    }

                    BigDecimal currentTotalPrice;
                    BigDecimal currentPropertyValue;
                    if (exchangeRate.compareTo(BigDecimal.valueOf(-1)) == 0) {
                        currentPropertyValue = BigDecimal.valueOf(0);
                        currentTotalPrice = BigDecimal.valueOf(0);
                    } else {
                        BigDecimal quantity = property.getQuantity();
                        currentPropertyValue = exchangeRate.stripTrailingZeros().setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
                        currentTotalPrice = exchangeRate.multiply(quantity).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
                    }
                    return new PropertyListDto.getAllPropertiesDto(property, currentPropertyValue, currentTotalPrice);
                }).collect(Collectors.toList());

    }

    public List<PropertyListDto.writeToInfluxPropertyDto> convertGetAllPropertiesDtoToWriteToInfluxPropertyDto(List<PropertyListDto.getAllPropertiesDto> getUserAllProperties) {
        return getUserAllProperties.stream()
                .map(prop -> new PropertyListDto.writeToInfluxPropertyDto(
                        prop.getUserId(),
                        prop.getAssetId(),
                        prop.getAssetType(),
                        0L,
                        prop.getCurrentPrice(),
                        prop.getQuantity(),
                        prop.getCurrentTotalPrice())
                ).toList();


    }
    public String writeAllPropertiesToJson (List<PropertyListDto.getAllPropertiesDto> getAllPropertiesDto) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Hibernate5JakartaModule());
        String json;
        try {
            json = mapper.writeValueAsString(getAllPropertiesDto);
        } catch (JsonProcessingException e) {
            logger.error("轉換 JSON 失敗", e);
            throw new RuntimeException("轉換 JSON 失敗", e);
        }
        logger.debug("取得 JSON 格式資料: " + json);
        return json;
    }


    public void writeAllPropertiesToInflux (List<PropertyListDto.writeToInfluxPropertyDto> writeToInfluxPropertyDto, User user) {
        boolean success = propertyInfluxService.writeToInflux(writeToInfluxPropertyDto, user);
        if (!success) {
            logger.error("寫入 InfluxDB 失敗");
            throw new RuntimeException("寫入 InfluxDB 失敗");
        }
    }

    public Map<String, String> getAllNameByPropertyType (String propertyType) {
        logger.debug("取得所有 " + propertyType + " 的名稱");
        Map<String, String> map = new LinkedHashMap<>();


        switch (propertyType) {
            case "STOCK_TW" -> {
                List<Object[]> stocks = stockTwRepository.findAllByOrderByStockCode();
                for (Object[] stock : stocks) {
                    String stockCode = (String) stock[0];
                    String stockName = (String) stock[1];
                    if (stockName != null && stockCode != null && stockCode.matches(".*\\d+.*")) {
                        map.put(stockCode, stockName);
                    }
                }
                logger.debug("取得排序後的股票名稱: " + map);
            }
            case "CURRENCY" -> {
                List<Currency> currencies = currencyRepository.findAllByOrderByCurrencyAsc();
                for (Currency currency : currencies) {
                    map.put(currency.getCurrency(), currency.getCurrency());
                }
                logger.debug("取得排序後的幣別名稱: " + map);
            }
            case "CRYPTO" -> {
                List<String> baseAssets = cryptoRepository.findAllBaseAssetByOrderByBaseAssetAsc();
                logger.debug("取得加密貨幣列表: " + baseAssets);
                for (String baseAsset : baseAssets) {
                    logger.debug("加密貨幣: " + baseAsset);
                    map.put(baseAsset, baseAsset);
                }
                logger.debug("取得排序後的加密貨幣: " + map);
            }
            default -> {
                logger.debug("不支援的類型");
                throw new RuntimeException("不支援的類型");
            }
        }
        logger.debug("取得名稱: " + map);
        return map;
    }



    public BigDecimal getCurrentPrice(Asset asset) {
        return switch (asset) {
            case StockTw stockTw -> assetInfluxMethod.getLatestPrice(stockTw);
            case CryptoTradingPair crypto -> assetInfluxMethod.getLatestPrice(crypto);
            case Currency currency -> assetInfluxMethod.getLatestPrice(currency);
            case null, default -> throw new IllegalArgumentException("不支援的資產類型");
        };
    }

    public String getPropertySummary(User user) {
        Map<String, List<FluxTable>> userSummary = propertyInfluxService.queryUserPropertySum(user);
        logger.debug("取得 InfluxDB 的資料: " + userSummary);
        Map<String, List<Map<String, Object>>>formatToChartData = chartMethod.formatToChartData(userSummary);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String json = mapper.writeValueAsString(formatToChartData);
            logger.debug("轉換成 JSON 的資料: " + json);
            return json;
        } catch (JsonProcessingException e) {
            logger.error("轉換 JSON 時發生錯誤", e);
            throw new RuntimeException("轉換 JSON 時發生錯誤", e);
        }
    }

}
