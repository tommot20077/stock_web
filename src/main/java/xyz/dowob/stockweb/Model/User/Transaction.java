package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.TransactionType;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_asset_transactions")
public class Transaction implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    private User user;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(name = "asset_name")
    private String assetName;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    private Asset asset;

    @Column(name = "amount", precision = 25, scale = 8)
    private BigDecimal amount;

    @Column(name = "quantity", precision = 25, scale = 8)
    private BigDecimal quantity;


    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_currency_id", referencedColumnName = "id")
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    private Asset unitCurrency;

    @Column(name = "unit_currency_name")
    private String unitCurrencyName;

    @Column(name = "transaction_time")
    private LocalDateTime transactionDate;

    @Column(columnDefinition = "TEXT")
    private String description;


}
