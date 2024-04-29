package xyz.dowob.stockweb.Repository.Currency;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.dowob.stockweb.Model.Currency.Currency;

import java.util.List;
import java.util.Optional;

/**
 * @author yuan
 */
public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    Optional<Currency> findByCurrency(String currency);
    @Query("SELECT DISTINCT c.currency FROM Currency c")
    List<String> findAllDistinctCurrencies();

    @Query("SELECT DISTINCT c.currency FROM Currency c")
    Page<String> findAllCurrenciesByPage(Pageable pageable);

    List<Currency> findAllByOrderByCurrencyAsc();
}
