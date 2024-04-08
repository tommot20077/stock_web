package xyz.dowob.stockweb.Model.Crypto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.User;

import java.util.HashSet;
import java.util.Set;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crypto_subscribers", joinColumns = @JoinColumn(name = "asset_id"))
    @Column(name = "user_id")
    private Set<Long> subscribers = new HashSet<>();

    @Column(name = "has_any_subscribed", nullable = false)
    private boolean hasAnySubscribed = false;

    public boolean checkUserIsSubscriber(User user) {
        return subscribers.contains(user.getId());
    }




}
