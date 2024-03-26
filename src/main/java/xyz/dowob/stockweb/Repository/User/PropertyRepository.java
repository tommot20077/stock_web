package xyz.dowob.stockweb.Repository.User;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.User;

import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @NotNull
    Optional<Property> findById(Long id);
    Optional<Property> findByAssetAndUser(Asset asset, User user);
}
