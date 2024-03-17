package xyz.dowob.stockweb.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);


}
