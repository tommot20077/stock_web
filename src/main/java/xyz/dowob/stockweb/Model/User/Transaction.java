package xyz.dowob.stockweb.Model.User;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.TransactionType;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_asset_transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(name = "asset_name")
    private String assetName;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Asset asset;

    @Column(name = "amount", precision = 25, scale = 8)
    private BigDecimal amount;

    @Column(name = "quantity", precision = 25, scale = 8)
    private BigDecimal quantity;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_currency")
    private Asset unitCurrency;

    @Column(name = "transaction_time")
    private LocalDateTime transactionDate;

    @Column(columnDefinition = "TEXT")
    private String description;


}
