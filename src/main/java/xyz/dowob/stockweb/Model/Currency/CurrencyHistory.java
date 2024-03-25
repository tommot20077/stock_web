package xyz.dowob.stockweb.Model.Currency;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Table(name = "currency_history_data")
@PrimaryKeyJoinColumn(name = "asset_id")
public class CurrencyHistory extends Asset {
    private String currency;

    @Column(name = "exchange_rate", precision = 12, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

}
