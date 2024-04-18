package xyz.dowob.stockweb.Dto.Property;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
@Data
public class RoiDataDto {
    private LocalDateTime date;
    private BigDecimal totalSum;
    private BigDecimal netCashFlow;
    private BigDecimal roi = BigDecimal.ZERO;

    public RoiDataDto(LocalDateTime date, BigDecimal totalSum, BigDecimal netCashFlow) {
        this.date = date;
        this.totalSum = totalSum;
        this.netCashFlow = netCashFlow;
    }
}
