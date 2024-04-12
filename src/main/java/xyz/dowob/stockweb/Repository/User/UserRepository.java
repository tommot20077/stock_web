package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);




}
