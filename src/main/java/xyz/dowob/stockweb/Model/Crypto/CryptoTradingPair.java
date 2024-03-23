package xyz.dowob.stockweb.Model.Crypto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;

@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "asset_id")
@Entity
@Data
@Table(name = "crypto_data")
public class CryptoTradingPair extends Asset {

    @Column(nullable = false)
    private String tradingPair;

    @Column(nullable = false)
    private String baseAsset;

    @Column(nullable = false)
    private String quoteAsset;

    @Column(name = "subscribe_number", nullable = false)
    private int subscribeNumber = 0;


}
