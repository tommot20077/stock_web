package xyz.dowob.stockweb.Model.Stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.User;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@PrimaryKeyJoinColumn(name = "asset_id")
@Table(name = "stock_tw_data")
public class StockTw extends Asset implements Serializable {

    @Column(name = "stock_code", unique = true, nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "stock_type")
    private String stockType;

    @Column(name = "industry_category")
    private String industryCategory;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_subscribers", joinColumns = @JoinColumn(name = "asset_id"))
    @Column(name = "user_id")
    @JsonIgnore
    private Set<Long> subscribers = new HashSet<>();

    @Column(name = "has_any_subscribed", nullable = false)
    private boolean hasAnySubscribed = false;

    @Column(name = "update_time")
    private LocalDate updateTime;



    public boolean checkUserIsSubscriber(User user) {
        return subscribers.contains(user.getId());
    }
}
