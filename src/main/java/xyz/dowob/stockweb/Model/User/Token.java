package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * @author yuan
 * 使用者Token
 * 實現Serializable, 用於序列化
 * 1. id : Token編號
 * 2. user : 使用者
 * 3. jwtApiCount : JWT API 請求次數
 * 4. jwtApiKeyExpiryTime : JWT API 金鑰重置時間
 * 5. emailApiToken : Email API 金鑰
 * 6. emailApiRequestCount : Email API 請求次數
 * 7. emailApiTokenResetTime : Email API 金鑰重置時間
 * 8. emailApiTokenExpiryTime : Email API 金鑰過期時間
 * 9. rememberMeToken : 記住我金鑰
 * 10. rememberMeTokenExpireTime : 記住我金鑰過期時間
 * 11. expirationMs : JWT API 金鑰過期時間
 */
@Entity
@Data
@Table(name = "user_token")
public class Token implements Serializable {
    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    private User user;

    @Column(name = "jwt_api_request_count",
            nullable = false)
    private int jwtApiCount = 0;

    @Column(name = "jwt_api_key_reset_time")
    private OffsetDateTime jwtApiKeyExpiryTime = OffsetDateTime.now();

    @Column(name = "email_api_token")
    private String emailApiToken;

    @Column(name = "email_api_request_count",
            nullable = false)
    private int emailApiRequestCount = 0;

    @Column(name = "email_api_key_reset_time")
    private OffsetDateTime emailApiTokenResetTime = OffsetDateTime.now();

    @Column(name = "email_api_key_expiry_time")
    private OffsetDateTime emailApiTokenExpiryTime = OffsetDateTime.now();

    private String rememberMeToken;

    private OffsetDateTime rememberMeTokenExpireTime;


    @Value(value = "${security.jwt.expiration:120}")
    private int expirationMs;

    /**
     * 取得並增加JWT API 請求次數
     *
     * @return JWT API 請求次數
     */
    public int getAndIncrementJwtApiCount() {
        if (jwtApiKeyExpiryTime.isBefore(OffsetDateTime.now())) {
            jwtApiCount = 0;
        }
        jwtApiCount++;
        jwtApiKeyExpiryTime = OffsetDateTime.now().plusMinutes(expirationMs);
        return jwtApiCount;
    }

    /**
     * 建立RememberMe金鑰
     *
     * @param token          金鑰
     * @param expireTimeDays 過期時間(天)
     */
    public void createRememberMeToken(String token, int expireTimeDays) {
        setRememberMeToken(token);
        setRememberMeTokenExpireTime(OffsetDateTime.now(ZoneId.of(user.getTimezone())).plusDays(expireTimeDays));
    }


    /**
     * 重寫toString
     * 隱藏user, 避免循環參考
     *
     * @return 字串
     */
    @Override
    public String toString() {
        return (new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE) {
            @Override
            protected boolean accept(java.lang.reflect.Field f) {
                return super.accept(f) && !"user".equals(f.getName());
            }
        }).toString();
    }

    /**
     * 重寫equals
     * 比較id
     *
     * @param o 物件
     *
     * @return 是否相同
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Token token = (Token) o;
        return Objects.equals(id, token.id);
    }

    /**
     * 重寫hashCode
     *
     * @return hashCode
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


}
