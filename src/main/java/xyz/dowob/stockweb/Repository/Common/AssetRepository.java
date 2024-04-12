package xyz.dowob.stockweb.Repository.Common;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    @NotNull
    @Override
    Optional<Asset> findById(@NotNull Long assetId);
}
