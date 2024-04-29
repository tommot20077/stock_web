package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;

import java.io.Serializable;

/**
 * @author yuan
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
