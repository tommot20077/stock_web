package xyz.dowob.stockweb.Dto.Property;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author yuan
 * 用於傳遞資產ROI的資料
 * 1. date: 日期
 * 2. totalSum: 總金額
 * 3. netCashFlow: 淨現金流
 * 4. roi: ROI
 */
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
