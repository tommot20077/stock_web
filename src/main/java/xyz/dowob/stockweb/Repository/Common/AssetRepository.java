package xyz.dowob.stockweb.Repository.Common;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.util.Optional;

/**
 * @author yuan
 * 資產與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface AssetRepository extends JpaRepository<Asset, Long> {
    /**
     * 透過資產編號尋找資產
     *
     * @param assetId 不可為null, 資產編號
     *
     * @return 資產
     */
    @NotNull
    @Override
    Optional<Asset> findById(
            @NotNull Long assetId);

    Page<Asset> findAllByAssetType(AssetType assetType, Pageable pageable);

    /**
     * 查詢所有資產
     *
     * @param pageable 分頁
     *
     * @return 資產分頁
     */
    @Override
    @NotNull
    Page<Asset> findAll(
            @NotNull Pageable pageable);
}
