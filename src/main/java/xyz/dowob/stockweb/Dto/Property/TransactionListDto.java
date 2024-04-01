package xyz.dowob.stockweb.Dto.Property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import xyz.dowob.stockweb.Enum.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionListDto {
    private List<TransactionDto> transactionList;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionDto {
        private String id;
        private String symbol;
        private String type;
        private String quantity;
        private String unit;
        private String amount;
        private String date;
        private String description;

        public BigDecimal formatQuantityAsBigDecimal() {
            BigDecimal amountAsBigDecimal = new BigDecimal(quantity);
            return amountAsBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        public BigDecimal formatAmountAsBigDecimal() {
            BigDecimal totalAsBigDecimal = new BigDecimal(amount);
            return totalAsBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        public TransactionType formatOperationTypeEnum() {
            try {
                return TransactionType.valueOf(type.toUpperCase());
            } catch (Exception e) {
                return TransactionType.OTHER;
            }

        }

        public LocalDateTime formatTransactionDate() {
            return LocalDateTime.parse(date.replace("T", " "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
    }
}
