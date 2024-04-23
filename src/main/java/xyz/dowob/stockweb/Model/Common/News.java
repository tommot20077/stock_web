package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.redis.core.RedisHash;
import xyz.dowob.stockweb.Enum.NewsType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
public class News implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String sourceName;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String urlToImage;

    private LocalDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String author;

    @Enumerated(value = EnumType.STRING)
    private NewsType newsType;
}
