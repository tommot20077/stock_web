package xyz.dowob.stockweb.Model.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author yuan
 * 貨幣
 * 繼承Asset, 用於保存貨幣
 * 實現Serializable, 用於序列化
 * 利用EqualsAndHashCode, 用於比較Asset是否相同
 * 1. currency : 貨幣名稱
 * 2. exchangeRate : 匯率
 * 3. updateTime : 更新時間
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Table(name = "currency_data")
@PrimaryKeyJoinColumn(name = "asset_id")
public class Currency extends Asset implements Serializable {
    @Column(unique = true)
    private String currency;

    @Column(name = "exchange_rate",
            precision = 12,
            scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
