package xyz.dowob.stockweb.Model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "crypto_subscription")
public class Crypto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String channel;

}
