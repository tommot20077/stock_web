package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.io.Serializable;

@Entity
@Data
@Table(name = "user_subscribe")
public class Subscribe implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    private Asset asset;

    @Column(name = "is_user_subscribed", nullable = false, columnDefinition = "boolean default false")
    private boolean isUserSubscribed = false;

    @Column(columnDefinition = "varchar(100)")
    private String channel;

    @Column(columnDefinition = "boolean default false")
    private boolean removeAble = false;

}