package xyz.dowob.stockweb.Component.Provider;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.User.UserRepository;


import javax.crypto.SecretKey;
import java.util.Date;

/**
 * 這是一個Jwt令牌提供者，用於生成和驗證JwtToken。
 *
 * @author yuan
 */
@Component
public class JwtTokenProvider {
    private final UserRepository userRepository;

    Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value(value = "${security.jwt.secret}")
    private String jwtSecret;

    @Value(value = "${security.jwt.expiration:120}")
    private int expirationMinute;

    private SecretKey key;

    /**
     * 這是一個構造函數，用於注入UserRepository。
     *
     * @param userRepository 使用者資料庫
     */
    @Autowired
    public JwtTokenProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 初始化JwtTokenProvider, 將jwtSecret解碼為SecretKey
     */
    @PostConstruct
    public void init() {
        byte[] encodedSecret = Decoders.BASE64.decode(jwtSecret);
        this.key = Keys.hmacShaKeyFor(encodedSecret);
    }


    /**
     * 從JwtToken中取得Claims
     *
     * @param token JwtToken
     *
     * @return Claims
     */
    public Claims getClaimsFromJwt(String token) {

        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 驗證JwtToken
     * 根據傳入的JwtToken, 從資料庫中取得使用者, 並比對Token版本
     *
     * @param authToken JwtToken
     *
     * @return boolean 驗證結果
     */

    public boolean validateToken(String authToken) {
        try {
            Claims claims = getClaimsFromJwt(authToken);
            Long userId = Long.parseLong(claims.getSubject());
            User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("找不到使用者"));
            Integer tokenVersionInDb = user.getToken().getJwtApiCount();
            Integer tokenVersionInToken = claims.get("token_version", Integer.class);
            return tokenVersionInDb.equals(tokenVersionInToken);

        } catch (SignatureException | IllegalArgumentException | UnsupportedJwtException | ExpiredJwtException | MalformedJwtException ex) {
            logger.error("不合法的Jwt Token: {}", ex.getMessage());
            throw new RuntimeException(ex.getMessage());
        }
    }

    /**
     * 生成JwtToken
     * expirationMinute透過中的security.jwt.expiration設定，預設為120分鐘
     *
     * @param userId  使用者ID
     * @param version Token版本
     *
     * @return JwtToken
     */
    public String generateToken(Long userId, int version) {
        Date now = new Date();
        int expirationMs = expirationMinute * 60 * 1000;
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                   .subject(Long.toString(userId))
                   .issuedAt(now)
                   .claim("token_version", version)
                   .expiration(expiryDate)
                   .signWith(this.key)
                   .compact();
    }
}