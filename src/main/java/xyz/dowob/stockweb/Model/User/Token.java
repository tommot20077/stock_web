package xyz.dowob.stockweb.Model.User;

import jakarta.persistence.*;
import lombok.Data;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.beans.factory.annotation.Value;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Data
@Table(name = "user_token")
public class Token {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "jwt_api_request_count", nullable = false)
    private int jwtApiCount = 0;

    @Column(name = "jwt_api_key_reset_time")
    private OffsetDateTime jwtApiKeyExpiryTime = OffsetDateTime.now();

    @Column(name = "email_api_token")
    private String emailApiToken;

    @Column(name = "email_api_request_count", nullable = false)
    private int emailApiRequestCount = 0;

    @Column(name = "email_api_key_reset_time")
    private OffsetDateTime emailApiTokenResetTime = OffsetDateTime.now();

    @Column(name = "email_api_key_expiry_time")
    private OffsetDateTime emailApiTokenExpiryTime = OffsetDateTime.now();

    private String rememberMeToken;

    private OffsetDateTime rememberMeTokenExpireTime;


    @Value(value = "${security.jwt.expiration}")
    private int expirationMs ;
    public int getAndIncrementJwtApiCount() {
        if (jwtApiKeyExpiryTime.isBefore(OffsetDateTime.now())) {
            jwtApiCount = 0;
        }
        jwtApiCount++;
        jwtApiKeyExpiryTime = OffsetDateTime.now().plusMinutes(expirationMs);

        return jwtApiCount;
    }

    public void createRememberMeToken(String token, int expireTimeDays) {
        setRememberMeToken(token);
        setRememberMeTokenExpireTime(OffsetDateTime.now(ZoneId.of(user.getTimezone())).plusDays(expireTimeDays));
    }


    @Override
    public String toString() {
        return (new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE) {
            @Override
            protected boolean accept(java.lang.reflect.Field f) {
                return super.accept(f) && !f.getName().equals("user");
            }
        }).toString();
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(id, token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


}
