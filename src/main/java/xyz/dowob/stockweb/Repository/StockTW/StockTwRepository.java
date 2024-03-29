package xyz.dowob.stockweb.Repository.StockTW;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Stock.StockTw;

import java.util.List;
import java.util.Optional;

public interface StockTwRepository extends JpaRepository<StockTw, Long> {
    Optional<StockTw> findByStockCode(String stockCode);

    @Query("SELECT DISTINCT s.stockCode, s.stockName FROM StockTw s order by s.stockCode")
    List<Object[]> findDistinctStockCodeAndName();

    List<StockTw> findAllByOrderByStockCode();
}
