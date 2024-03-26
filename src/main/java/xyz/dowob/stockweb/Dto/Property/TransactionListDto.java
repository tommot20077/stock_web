package xyz.dowob.stockweb.Dto.Property;

import lombok.Data;
import xyz.dowob.stockweb.Enum.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class TransactionListDto {
    private List<TransactionDto> transactionList;

    @Data
    public static class TransactionDto {
        private String symbol;
        private String type;
        private String quantity;
        private String unit;
        private String amount;
        private String date;
        private String description;

        public BigDecimal getQuantityAsBigDecimal() {
            BigDecimal amountAsBigDecimal = new BigDecimal(quantity);
            return amountAsBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        public BigDecimal getAmountAsBigDecimal() {
            BigDecimal totalAsBigDecimal = new BigDecimal(amount);
            return totalAsBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        public TransactionType getOperationTypeEnum() {
            return TransactionType.valueOf(type.toUpperCase());
        }

        public LocalDateTime getTransactionDate() {
            return LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
