package xyz.dowob.stockweb.Dto.Property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.OperationType;
import xyz.dowob.stockweb.Model.User.Property;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyListDto {

    private List<PropertyDto> propertyList;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertyDto {
        private Long id;
        private String symbol;
        private String quantity;
        private String description;
        private String operationType;

        public BigDecimal formatQuantityBigDecimal() {
            if (quantity == null) {
                return null;
            }
            BigDecimal quantityBigDecimal = new BigDecimal(quantity);
            return quantityBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        public OperationType formatOperationTypeEnum() {
            try {
                return OperationType.valueOf(operationType.toUpperCase());
            } catch (Exception e) {
                return OperationType.OTHER;
            }
        }

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
            this.userId = property.getId();
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

    @Data
    public static class writeToInfluxPropertyDto {
        private Long userId;
        private Long assetId;
        private AssetType assetType;
        private Long timeMillis;
        private BigDecimal quantity;
        private BigDecimal currentPrice;
        private BigDecimal currentTotalPrice;

        public writeToInfluxPropertyDto(Long userId, Long assetId, AssetType assetType, Long timeMillis, BigDecimal currentPrice,BigDecimal quantity, BigDecimal currentTotalPrice) {
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
