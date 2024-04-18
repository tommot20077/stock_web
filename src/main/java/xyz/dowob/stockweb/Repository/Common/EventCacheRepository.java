package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.EventCache;

import java.util.List;
import java.util.Optional;

public interface EventCacheRepository extends JpaRepository<EventCache, String> {
    List<EventCache> findEventCacheByPropertyAsset(Asset asset);
}
