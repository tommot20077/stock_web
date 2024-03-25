package xyz.dowob.stockweb.Model.User;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Data
@Entity
@Table(name = "user_property")
public class Property {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    private String assetName;

    @Column(precision = 25, scale = 8)
    private BigDecimal quantity;

    @Column(columnDefinition = "TEXT")
    private String description;

    private OffsetDateTime createTime;

    private OffsetDateTime updateTime;

    @PreUpdate
    protected void onUpdate() {
        updateTime = OffsetDateTime.now(ZoneId.of(user.getTimezone()));
    }
    @PrePersist
    protected void onCreate() {
        updateTime = OffsetDateTime.now(ZoneId.of(user.getTimezone()));
        createTime = OffsetDateTime.now(ZoneId.of(user.getTimezone()));
    }

}
