package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;

import java.io.Serializable;

/**
 * @author yuan
 * 資產種類 (股票、加密貨幣、貨幣)
 * 實現Serializable, 用於序列化
 * 繼承此類別的類別需實作以下屬性
 * 1. id : 資產編號
 * 2. assetType : 資產種類
 */
@Entity
@Data
@Inheritance(strategy = InheritanceType.JOINED)
public class Asset implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetType assetType;
}
