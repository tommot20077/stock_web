package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Common.Asset;

public interface AssetRepository extends JpaRepository<Asset, Long> {


}
