package xyz.dowob.stockweb.Model.User;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Fetch;
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

    @OneToOne(fetch = FetchType.LAZY)
    private Asset asset;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "price_per_unit")
    private BigDecimal pricePerUnit;

    @Column(name = "transaction_time")
    private LocalDateTime transactionDate;

    @Column(columnDefinition = "TEXT")
    private String description;


}
