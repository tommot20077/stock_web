package xyz.dowob.stockweb.Dto.Property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.OperationType;
import xyz.dowob.stockweb.Model.User.Property;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * @author yuan
 * 用於傳遞資產列表的資料
 * 1. propertyList: 資產列表
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyListDto {
    private List<PropertyDto> propertyList;

    /**
     * 用於傳遞資產的資料
     * 1. id: 資產ID
     * 2. symbol: 資產代號 (股票代號、加密貨幣交易對、貨幣代號)
     * 3. quantity: 數量
     * 4. description: 描述
     * 5. operationType: 操作類型
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertyDto {
        private Long id;

        private String symbol;

        private String quantity;

        private String description;

        private String operationType;

        /**
         * 將數量轉換為BigDecimal
         *
         * @return 數量BigDecimal
         */
        public BigDecimal formatQuantityBigDecimal() {
            if (quantity == null) {
                return null;
            }
            BigDecimal quantityBigDecimal = new BigDecimal(quantity.replace(",", ""));
            return quantityBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        /**
         * 將操作類型轉換為OperationType
         *
         * @return OperationType
         */
        public OperationType formatOperationTypeEnum() {
            try {
                return OperationType.valueOf(operationType.toUpperCase());
            } catch (Exception e) {
                return OperationType.OTHER;
            }
        }

        /**
         * 提取股票代號
         *
         * @param symbol 資產代號
         *
         * @return 股票代號
         */
        public String extractStockCode(String symbol) {
            if (symbol == null) {
                return null;
            }
            if (symbol.contains("-")) {
                return symbol.split("-")[0];
            }
            return symbol;
        }
    }

    /**
     * 用於傳遞所有資產的資料
     * 1. userId: 使用者ID
     * 2. preferredCurrency: 使用者偏好貨幣
     * 3. preferredCurrencyRate: 使用者偏好貨幣匯率
     * 4. propertyId: 資產ID
     * 5. assetId: 資產ID
     * 6. assetType: 資產類型
     * 7. assetName: 資產名稱
     * 8. quantity: 數量
     * 9. currentPrice: 當前價格
     * 10. currentTotalPrice: 當前總價
     * 11. description: 描述
     */
    @Data
    public static class getAllPropertiesDto {
        private Long userId;

        private String preferredCurrency;

        private BigDecimal preferredCurrencyRate;

        private Long propertyId;

        private Long assetId;

        private AssetType assetType;

        private String assetName;

        private BigDecimal quantity;

        private BigDecimal currentPrice;

        private BigDecimal currentTotalPrice;

        private String description;

        public getAllPropertiesDto(Property property, BigDecimal currentPrice, BigDecimal currentTotalPrice) {
            this.userId = property.getUser().getId();
            this.preferredCurrency = property.getUser().getPreferredCurrency().getCurrency();
            this.preferredCurrencyRate = property.getUser().getPreferredCurrency().getExchangeRate();
            this.propertyId = property.getId();
            this.assetType = property.getAsset().getAssetType();
            this.assetId = property.getAsset().getId();
            this.assetName = property.getAssetName();
            this.quantity = property.getQuantity();
            this.description = property.getDescription();
            this.currentPrice = currentPrice;
            this.currentTotalPrice = currentTotalPrice;
        }
    }

    /**
     * 用於傳遞寫入InfluxDB的資料
     * 1. userId: 使用者ID
     * 2. assetId: 資產ID
     * 3. assetType: 資產類型
     * 4. timeMillis: 時間戳
     * 5. currentPrice: 當前價格
     * 6. quantity: 數量
     * 7. currentTotalPrice: 當前總價
     */
    @Data
    public static class writeToInfluxPropertyDto {
        private Long userId;

        private Long assetId;

        private AssetType assetType;

        private Long timeMillis;

        private BigDecimal quantity;

        private BigDecimal currentPrice;

        private BigDecimal currentTotalPrice;

        public writeToInfluxPropertyDto(Long userId, Long assetId, AssetType assetType, Long timeMillis, BigDecimal currentPrice, BigDecimal quantity, BigDecimal currentTotalPrice) {
            this.userId = userId;
            this.assetId = assetId;
            this.assetType = assetType;
            this.timeMillis = timeMillis;
            this.currentPrice = currentPrice;
            this.quantity = quantity;
            this.currentTotalPrice = currentTotalPrice;
        }
    }
}
