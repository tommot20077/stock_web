package xyz.dowob.stockweb.Repository.Crypto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CryptoRepository extends JpaRepository<CryptoTradingPair, Long> {
    Optional<CryptoTradingPair> findByTradingPair(String tradingPair);

    @Query("SELECT distinct c FROM CryptoTradingPair c join c.subscribers")
    Set<CryptoTradingPair> findAllByHadSubscribed();

    @Query("SELECT count(c.subscribers) FROM CryptoTradingPair c")
    int countAllSubscribeNumber();

    @Query("SELECT c.baseAsset FROM CryptoTradingPair c ORDER BY c.baseAsset")
    List<String> findAllBaseAssetByOrderByBaseAssetAsc();


    @Query("SELECT COUNT(c.subscribers) FROM CryptoTradingPair c WHERE c = :cryptoTradingPair")
    int countCryptoSubscribersNumber(CryptoTradingPair cryptoTradingPair);



}
