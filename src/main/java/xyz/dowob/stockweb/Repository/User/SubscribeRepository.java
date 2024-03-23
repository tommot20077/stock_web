package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Subscribe;

import java.util.Optional;

public interface SubscribeRepository extends JpaRepository<Subscribe, Long> {
    Optional<Subscribe> findByUserIdAndAssetId(Long userId, Long assetId);
    Optional<Subscribe> findByUserIdAndAssetIdAndAssetDetail(Long userId, Long assetId, String assetDetail);
    void deleteByUserIdAndAssetId(Long userId, Long assetId);
}
