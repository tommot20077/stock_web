package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
