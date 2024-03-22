package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;

import java.math.BigDecimal;

@Entity
@Data
@Inheritance(strategy = InheritanceType.JOINED)
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetType assetType;


}
