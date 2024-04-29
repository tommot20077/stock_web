package xyz.dowob.stockweb.Repository.User;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.User;

import java.util.List;
import java.util.Optional;

/**
 * @author yuan
 */
public interface PropertyRepository extends JpaRepository<Property, Long> {

    @Override
    @NotNull
    Optional<Property> findById(
            @NotNull Long id);

    List<Property> findByAssetAndUser(Asset asset, User user);

    @Query("SELECT p FROM Property p JOIN FETCH p.asset a WHERE p.user = :user")
    List<Property> findAllByUser(
            @Param("user") User user);

    @Query("SELECT p FROM Property p WHERE p.user = :user ORDER BY p.asset.assetType, p.assetName")
    List<Property> findAllByUserAndOrderByAssetTypeAndOrderByAssetName(User user);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.user = :user AND p.asset = :asset")
    int getUserSpecifyAssetCount(User user, Asset asset);
}
