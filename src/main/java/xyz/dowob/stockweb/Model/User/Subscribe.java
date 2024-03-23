package xyz.dowob.stockweb.Model.User;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Model.Common.Asset;

@Entity
@Data
@Table(name = "user_subscribe")
public class Subscribe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    private String assetDetail;

}