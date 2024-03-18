package xyz.dowob.stockweb.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Currency;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    Optional<Currency> findByCurrency(String currency);
    @Query("SELECT DISTINCT c.currency FROM Currency c")
    List<String> findAllDistinctCurrencies();

    Optional<Currency> findByCurrencyAndUpdateTime(String name, LocalDateTime updateTime);

}
