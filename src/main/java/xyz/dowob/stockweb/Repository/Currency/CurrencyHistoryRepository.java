package xyz.dowob.stockweb.Repository.Currency;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Currency.CurrencyHistory;

import java.util.List;

public interface CurrencyHistoryRepository extends JpaRepository<CurrencyHistory, Long> {

    List<CurrencyHistory> findByCurrencyOrderByUpdateTimeDesc(String currency);
}
