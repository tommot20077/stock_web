package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Transaction;
import xyz.dowob.stockweb.Model.User.User;

import java.util.List;

/**
 * @author yuan
 * 用戶交易與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * 透過用戶尋找用戶交易列表
     *
     * @param user 用戶
     *
     * @return 用戶交易列表
     */
    List<Transaction> findByUserOrderByTransactionDateDesc(User user);
}
