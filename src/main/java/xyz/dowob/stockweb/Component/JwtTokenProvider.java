package xyz.dowob.stockweb.Component;

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
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Repository.UserRepository;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {
    @Value(value = "${security.jwt.secret}")
    private String jwtSecret;
    @Value(value = "${security.jwt.expiration}")
    private int expirationMinute ;
    private SecretKey key;

    //暫放logger
    Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final UserRepository userRepository;
    @Autowired
    public JwtTokenProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        byte[] encodedSecret = Decoders.BASE64.decode(jwtSecret);
        this.key = Keys.hmacShaKeyFor(encodedSecret);
    }
    public Claims getClaimsFromJWT(String token) {

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String authToken) {
        try {
            Claims claims = getClaimsFromJWT(authToken);
            Long UserId = Long.parseLong(claims.getSubject());
            System.out.println("UserId: " + UserId);
            User user = userRepository.findById(UserId).orElseThrow(() -> new RuntimeException("找不到使用者"));
            Integer tokenVersionInDb = user.getToken().getJwtApiCount();
            Integer tokenVersionInToken = claims.get("token_version", Integer.class);
            return tokenVersionInDb.equals(tokenVersionInToken);

            //Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
        } catch (SignatureException | IllegalArgumentException | UnsupportedJwtException | ExpiredJwtException |
                 MalformedJwtException ex) {
            logger.error("不合法的Jwt Token: " + ex.getMessage());
            throw new RuntimeException(ex.getMessage());
        }
    }

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