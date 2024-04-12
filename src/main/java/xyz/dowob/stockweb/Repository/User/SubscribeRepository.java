package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SubscribeRepository extends JpaRepository<Subscribe, Long> {
    Optional<Subscribe> findByUserIdAndAssetId(Long userId, Long assetId);
    Optional<Subscribe> findByUserIdAndAssetIdAndChannel(Long userId, Long assetId, String channel);

    List<Subscribe> findAllByUserAndAssetAssetType(User user, AssetType assetType);

    @Query("SELECT distinct s.asset FROM Subscribe s")
    Set<Asset> findAllAsset();
}
