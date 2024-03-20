package xyz.dowob.stockweb.Repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.dowob.stockweb.Model.Crypto;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CryptoRepository extends JpaRepository<Crypto, Long> {
    Optional<Crypto> findBySymbolAndChannel(String symbol, String channel);

    @Query("SELECT c.channel FROM Crypto c WHERE c.symbol = :symbol")
    Set<Crypto> findBySymbol(@Param("symbol") String symbol);

    @NotNull
    List<Crypto> findAll();

    @Query("SELECT DISTINCT c.symbol FROM Crypto c")
    Set<String> findAllDistinctSymbols();


}
