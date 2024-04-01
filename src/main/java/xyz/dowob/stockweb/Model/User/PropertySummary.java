package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Data
@Table(name = "property_summary")
public class PropertySummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;

    @Column(name = "asset_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(precision = 25, scale = 8, name = "total_value", nullable = false)
    private BigDecimal totalValue;

    private OffsetDateTime updateTime;

    @PreUpdate
    public void preUpdate() {
        updateTime = OffsetDateTime.now(ZoneId.of(user.getTimezone()));
    }


}
