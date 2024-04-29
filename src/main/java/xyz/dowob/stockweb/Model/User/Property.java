package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * @author yuan
 */
@Data
@Entity
@Table(name = "user_property")
public class Property implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    private Asset asset;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(precision = 25, scale = 8, nullable = false)
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
