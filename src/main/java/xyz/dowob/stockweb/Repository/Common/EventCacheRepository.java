package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Model.User.Property;

import java.util.List;

/**
 * @author yuan
 * 事件快取與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface EventCacheRepository extends JpaRepository<EventCache, String> {
    /**
     * 透過資產尋找事件快取
     *
     * @param asset 資產
     *
     * @return 事件快取列表
     */
    List<EventCache> findEventCacheByPropertyAsset(Asset asset);

    /**
     * 透過用戶財產尋找事件快取
     *
     * @param property 資產
     *
     * @return 事件快取列表
     */
    List<EventCache> findEventCacheByProperty(Property property);


    /**
     * 查詢所有事件快取，去除重複
     *
     * @return 事件快取列表
     */
    @Query("SELECT distinct e FROM EventCache e")
    List<EventCache> findAllEventCachesProperty();
}
