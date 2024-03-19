package xyz.dowob.stockweb.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.CurrencyHistory;

import java.util.List;
import java.util.Set;

public interface CurrencyHistoryRepository extends JpaRepository<CurrencyHistory, Long> {

    List<CurrencyHistory> findByCurrencyOrderByUpdateTimeDesc(String currency);
}
