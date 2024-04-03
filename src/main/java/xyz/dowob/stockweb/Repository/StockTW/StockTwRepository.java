package xyz.dowob.stockweb.Repository.StockTW;

import lombok.Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Stock.StockTw;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockTwRepository extends JpaRepository<StockTw, Long> {
    Optional<StockTw> findByStockCode(String stockCode);

    @Query("SELECT DISTINCT s.stockCode, s.stockName FROM StockTw s order by s.stockCode")
    List<Object[]> findDistinctStockCodeAndName();
    @Query("SELECT s.stockCode, s.stockName FROM StockTw s ORDER BY s.stockCode")
    List<Object[]> findAllByOrderByStockCode();

    @Query("SELECT COUNT(s.subscribers) FROM StockTw s WHERE s = :stockTw")
    int countStockTwSubscribersNumber(StockTw stockTw);

    @Query("SELECT DISTINCT s.stockCode, s.stockType FROM StockTw s JOIN s.subscribers subscriber")
    Set<Object[]> findAllAssetIdsWithSubscribers();

}
