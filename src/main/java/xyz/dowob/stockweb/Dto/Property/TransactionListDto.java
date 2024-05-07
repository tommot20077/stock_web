package xyz.dowob.stockweb.Dto.Property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import xyz.dowob.stockweb.Enum.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author yuan
 * 用於傳遞交易列表的資料
 * 1. transactionList: 交易列表
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionListDto {
    private List<TransactionDto> transactionList;

    /**
     * 用於傳遞交易的資料
     * 1. id: 交易ID
     * 2. symbol: 交易代號 (股票代號、加密貨幣交易對、貨幣代號)
     * 3. type: 交易類型
     * 4. quantity: 數量
     * 5. unit: 單位
     * 6. amount: 金額
     * 7. date: 日期
     * 8. description: 描述
     */
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

        /**
         * 將數量轉換為BigDecimal
         *
         * @return 數量BigDecimal
         */
        public BigDecimal formatQuantityAsBigDecimal() {
            BigDecimal amountAsBigDecimal = new BigDecimal(quantity);
            return amountAsBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        /**
         * 將金額轉換為BigDecimal
         *
         * @return 金額BigDecimal
         */
        public BigDecimal formatAmountAsBigDecimal() {
            BigDecimal totalAsBigDecimal = new BigDecimal(amount);
            return totalAsBigDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        /**
         * 交易類型轉換為TransactionType
         *
         * @return TransactionType
         */
        public TransactionType formatOperationTypeEnum() {
            try {
                return TransactionType.valueOf(type.toUpperCase());
            } catch (Exception e) {
                return TransactionType.OTHER;
            }
        }

        /**
         * 轉換日期格式，分成兩種格式: yyyy-MM-dd HH:mm:ss、yyyy-MM-dd HH:mm
         *
         * @return LocalDateTime
         */
        public LocalDateTime formatTransactionDate() {
            String dateTimeFormat;
            if (date.length() == "yyyy-MM-dd HH:mm:ss".length()) {
                dateTimeFormat = "yyyy-MM-dd HH:mm:ss";
            } else if (date.length() == "yyyy-MM-dd HH:mm".length()) {
                dateTimeFormat = "yyyy-MM-dd HH:mm";
            } else {
                throw new IllegalArgumentException("不支援的格式: " + date);
            }
            LocalDateTime ldt = LocalDateTime.parse(date.replace("T", " "), DateTimeFormatter.ofPattern(dateTimeFormat));
            return ldt.withSecond(0);
        }
    }
}
