package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Token;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author yuan
 * 用戶Token與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface TokenRepository extends JpaRepository<Token, Long> {
    /**
     * 利用email api token尋找token
     *
     * @param token email api token
     *
     * @return token
     */
    Optional<Token> findByEmailApiToken(String token);

    /**
     * 利用remember me token尋找token
     *
     * @param rememberMeToken remember me token
     *
     * @return token
     */
    Optional<Token> findByRememberMeToken(String rememberMeToken);

    /**
     * 尋找所有email過期時間點在expiryTime之前的token列表
     *
     * @param expiryTime 過期時間
     *
     * @return token列表
     */
    List<Token> findAllByEmailApiTokenExpiryTimeIsBefore(OffsetDateTime expiryTime);

    /**
     * 尋找所有RememberMe時間點在expiryTime之前的token列表
     *
     * @param expiryTime 過期時間
     *
     * @return token列表
     */
    List<Token> findAllByRememberMeTokenExpireTimeIsBefore(OffsetDateTime expiryTime);
}
