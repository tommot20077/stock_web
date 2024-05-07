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
 * 貨幣與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    /**
     * 透過貨幣名稱尋找貨幣
     *
     * @param currency 貨幣名稱
     *
     * @return 貨幣
     */
    Optional<Currency> findByCurrency(String currency);

    /**
     * 查詢所有貨幣的名稱,去除重複
     *
     * @return 貨幣名稱列表
     */
    @Query("SELECT DISTINCT c.currency FROM Currency c")
    List<String> findAllDistinctCurrencies();

    /**
     * 查詢所有貨幣的名稱,去除重複(分頁)
     *
     * @param pageable 分頁
     *
     * @return 貨幣名稱列表
     */
    @Query("SELECT DISTINCT c.currency FROM Currency c")
    Page<String> findAllCurrenciesByPage(Pageable pageable);

    /**
     * 查詢所有貨幣列表,並依照名稱排序
     *
     * @return 貨幣名稱列表
     */
    List<Currency> findAllByOrderByCurrencyAsc();
}
