package xyz.dowob.stockweb.Model;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "user_assets")
public class Asset {
    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetType assetType = AssetType.CASH;

    @Column(name = "asset_symbol")
    private String assetSymbol;

    @Column(name = "asset_quantity")
    private BigDecimal Quantity;
}
