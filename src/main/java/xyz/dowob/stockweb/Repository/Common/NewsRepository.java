package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Enum.NewsType;
import xyz.dowob.stockweb.Model.Common.News;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsRepository extends JpaRepository<News, String> {

    void deleteAllByPublishedAtBefore(LocalDateTime publishedAt);

    @Query("select n.title from News n")
    List<String> getAllNewsWithPublishedAtAndTitle();

    Page<News> findAllByNewsType(NewsType newsType, PageRequest pageRequest);
}
