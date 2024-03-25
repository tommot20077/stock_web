package xyz.dowob.stockweb.Dto.Property;

import lombok.Data;
import xyz.dowob.stockweb.Enum.OperationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Data
public class PropertyListDto {
    private List<PropertyDto> propertyList;

    @Data
    public static class PropertyDto {
        private Long id;
        private String symbol;
        private String quantity;
        private String description;
        private String operationType;

        public BigDecimal getQuantityBigDecimal() {
            BigDecimal quantityBigDecimal = new BigDecimal(quantity);
            return quantityBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        public OperationType getOperationTypeEnum() {
            return OperationType.valueOf(operationType.toUpperCase());
        }
    }

}
