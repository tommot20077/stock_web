package xyz.dowob.stockweb.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.Token;
import xyz.dowob.stockweb.Model.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {
    Optional<Token> findByEmailApiToken(String token);
    Optional<Token> findByRememberMeToken(String rememberMeToken);

    List<Token> findAllByEmailApiTokenExpiryTimeIsBefore(OffsetDateTime expiryTime);
    List<Token> findAllByRememberMeTokenExpireTimeIsBefore(OffsetDateTime expiryTime);
}
