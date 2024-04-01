package xyz.dowob.stockweb.Repository.Crypto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;

import java.util.List;
import java.util.Optional;

public interface CryptoRepository extends JpaRepository<CryptoTradingPair, Long> {
    Optional<CryptoTradingPair> findByTradingPair(String tradingPair);

    @Query("SELECT c FROM CryptoTradingPair c WHERE COUNT(c.subscribers) > 0")
    List<CryptoTradingPair> findAllByHadSubscribed();

    @Query("SELECT count(c.subscribers) FROM CryptoTradingPair c")
    int countAllSubscribeNumber();

    @Query("SELECT c.baseAsset FROM CryptoTradingPair c ORDER BY c.baseAsset")
    List<String> findAllBaseAssetByOrderByBaseAssetAsc();


    @Query("SELECT COUNT(c.subscribers) FROM CryptoTradingPair c WHERE c = :cryptoTradingPair")
    int countCryptoSubscribersNumber(CryptoTradingPair cryptoTradingPair);



}
