package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Subscribe;

import java.util.Optional;

public interface SubscribeRepository extends JpaRepository<Subscribe, Long> {
    Optional<Subscribe> findByUserIdAndAssetId(Long userId, Long assetId);
    Optional<Subscribe> findByUserIdAndAssetIdAndChannel(Long userId, Long assetId, String Channel);

}
