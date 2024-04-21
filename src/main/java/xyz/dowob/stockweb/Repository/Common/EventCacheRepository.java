package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.EventCache;
import xyz.dowob.stockweb.Model.User.Property;
import java.util.List;

public interface EventCacheRepository extends JpaRepository<EventCache, String> {
    List<EventCache> findEventCacheByPropertyAsset(Asset asset);
    List<EventCache> findEventCacheByProperty(Property property);



    @Query("SELECT distinct e FROM EventCache e")
    List<EventCache> findAllEventCachesProperty();
}
