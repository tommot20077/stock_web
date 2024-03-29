package xyz.dowob.stockweb.Repository.Crypto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;

import java.util.List;
import java.util.Optional;

public interface CryptoRepository extends JpaRepository<CryptoTradingPair, Long> {
    Optional<CryptoTradingPair> findByTradingPair(String tradingPair);

    @Query("SELECT c FROM CryptoTradingPair c WHERE c.subscribeNumber > 0")
    List<CryptoTradingPair> findAllByHadSubscribed();

    @Query("SELECT SUM(c.subscribeNumber) FROM CryptoTradingPair c")
    int countAllSubscribeNumber();

    List<CryptoTradingPair> findAllByOrderByBaseAssetAsc();



}
