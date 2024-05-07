package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.Subscribe;
import xyz.dowob.stockweb.Model.User.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author yuan
 * 用戶訂閱與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface SubscribeRepository extends JpaRepository<Subscribe, Long> {
    /**
     * 透過用戶ID與資產ID尋找用戶訂閱
     *
     * @param userId  用戶ID
     * @param assetId 資產ID
     *
     * @return
     */
    Optional<Subscribe> findByUserIdAndAssetId(Long userId, Long assetId);

    /**
     * 透過用戶ID與資產ID與補充訊息尋找用戶訂閱
     *
     * @param userId  用戶ID
     * @param assetId 資產ID
     * @param channel 頻道
     *
     * @return 用戶訂閱
     */
    Optional<Subscribe> findByUserIdAndAssetIdAndChannel(Long userId, Long assetId, String channel);

    /**
     * 透過用戶與資產類型尋找用戶訂閱列表
     *
     * @param user 用戶
     *
     * @return 用戶訂閱列表
     */
    List<Subscribe> findAllByUserAndAssetAssetType(User user, AssetType assetType);

    /**
     * 查詢所有用戶訂閱列表
     *
     * @return 用戶訂閱列表
     */
    @Query("SELECT distinct s.asset FROM Subscribe s")
    Set<Asset> findAllAsset();

    /**
     * 透過用戶查詢用戶訂閱列表,並取得補充資訊、資產、是否可移除
     *
     * @param user 用戶
     *
     * @return 用戶訂閱列表 Object[0]: 頻道, Object[1]: 資產, Object[2]: 是否可移除
     */
    @Query("SELECT s.channel, s.asset, s.removeAble FROM Subscribe s WHERE s.user = :user")
    List<Object[]> getChannelAndAssetAndRemoveAbleByUserId(
            @Param("user") User user);

}
