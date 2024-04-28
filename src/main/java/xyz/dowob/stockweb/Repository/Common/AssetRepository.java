package xyz.dowob.stockweb.Repository.Common;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    @NotNull
    @Override
    Optional<Asset> findById(@NotNull Long assetId);
    Page<Asset> findAllByAssetType(AssetType assetType, Pageable pageable);

    @Override
    @NotNull
    Page<Asset> findAll(@NotNull Pageable pageable);
}
