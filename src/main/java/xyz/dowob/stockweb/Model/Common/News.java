package xyz.dowob.stockweb.Model.Common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.NewsType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author yuan
 * 新聞
 * 實現Serializable, 用於序列化
 * 1. id : 新聞編號
 * 2. sourceName : 新聞來源名稱
 * 3. title : 新聞標題
 * 4. url : 新聞連結
 * 5. urlToImage : 新聞圖片連結
 * 6. publishedAt : 新聞發布時間
 * 7. author : 新聞作者
 * 8. newsType : 新聞種類
 * 9. asset : 新聞所屬的資產
 */
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Asset asset = null;
}
