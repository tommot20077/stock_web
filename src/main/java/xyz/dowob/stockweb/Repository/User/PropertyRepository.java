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
 * 用戶資產與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface PropertyRepository extends JpaRepository<Property, Long> {

    /**
     * 透過ID尋找用戶資產
     *
     * @param id 不為null的ID
     *
     * @return 用戶資產
     */
    @Override
    @NotNull
    Optional<Property> findById(
            @NotNull Long id);

    /**
     * 透過用戶與資產尋找用戶財產
     *
     * @param asset 資產
     * @param user  用戶
     *
     * @return 用戶財產
     */
    List<Property> findByAssetAndUser(Asset asset, User user);

    /**
     * 透過用戶尋找用戶財產列表
     *
     * @param user 用戶
     *
     * @return 用戶財產列表
     */
    @Query("SELECT p FROM Property p JOIN FETCH p.asset a WHERE p.user = :user")
    List<Property> findAllByUser(
            @Param("user") User user);
}
