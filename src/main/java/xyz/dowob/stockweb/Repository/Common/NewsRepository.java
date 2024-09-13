package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Enum.NewsType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.News;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author yuan
 * 新聞與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface NewsRepository extends JpaRepository<News, String> {
    /**
     * 透過發布時間刪除在這時間點之前的所有新聞
     *
     * @param publishedAt 不可為null, 發布時間
     */
    void deleteAllByPublishedAtBefore(LocalDateTime publishedAt);

    /**
     * 查詢所有新聞的標題
     *
     * @return 新聞標題列表
     */
    @Query("select n.title from News n")
    List<String> getAllNewsWithPublishedAtAndTitle();

    /**
     * 透過新聞種類尋找新聞
     *
     * @param newsType    新聞種類
     * @param pageRequest 分頁
     *
     * @return 新聞分頁
     */
    Page<News> findAllByNewsTypeOrderByPublishedAtDesc(NewsType newsType, PageRequest pageRequest);

    /**
     * 透過資產尋找新聞
     *
     * @param asset       資產
     * @param pageRequest 分頁
     *
     * @return 新聞分頁
     */
    Page<News> findAllByAssetOrderByPublishedAtDesc(Asset asset, PageRequest pageRequest);
}
