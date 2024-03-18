package xyz.dowob.stockweb.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.CurrencyHistory;

import java.util.List;

public interface CurrencyHistoryRepository extends JpaRepository<CurrencyHistory, Long> {

    @Query("SELECT DISTINCT c.currency FROM CurrencyHistory c")
    List<String> findAllDistinctCurrencies();
}
