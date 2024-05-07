package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.User;

import java.util.Optional;

/**
 * @author yuan
 * 用戶與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 透過email尋找用戶
     *
     * @param email email
     *
     * @return 用戶
     */
    Optional<User> findByEmail(String email);
}
