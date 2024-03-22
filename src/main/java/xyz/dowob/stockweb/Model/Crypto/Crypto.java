package xyz.dowob.stockweb.Model.Crypto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;

@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "asset_id")
@Entity
@Data
@Table(name = "crypto_subscription")
public class Crypto extends Asset {

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String channel;

    @Column(name = "subscribe_number", nullable = false)
    private int subscribeNumber = 0;

}
