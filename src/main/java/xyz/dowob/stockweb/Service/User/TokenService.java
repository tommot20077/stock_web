package xyz.dowob.stockweb.Service.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Provider.JwtTokenProvider;
import xyz.dowob.stockweb.Component.Provider.MailTokenProvider;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Model.User.Token;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.User.TokenRepository;
import xyz.dowob.stockweb.Repository.User.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final MailTokenProvider mailTokenProvider;
    private final UserService userService;

    Logger logger = LoggerFactory.getLogger(UserService.class);
    @Autowired
    public TokenService(UserRepository userRepository, TokenRepository tokenRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder, MailTokenProvider mailTokenProvider, UserService userService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.mailTokenProvider = mailTokenProvider;
        this.userService = userService;
    }


    public void sendVerificationEmail(HttpSession session) {
        User user = userService.getUserFromJwtTokenOrSession(session);
        if (user.getRole() == Role.UNVERIFIED_USER) {
            mailTokenProvider.sendVerificationEmail(user);
        } else {
            throw new RuntimeException("用戶已經完成驗證");
        }
    }


    public void verifyEmail(String base128Token) {
        if (base128Token == null || base128Token.isBlank()) {
            throw new RuntimeException("密鑰不存在");
        }
        User user = mailTokenProvider.validateTokenAndReturnUser(base128Token);
        if (user != null) {
            user.setRole(Role.VERIFIED_USER);
            user.getToken().setEmailApiToken(null);
            userRepository.save(user);
            logger.warn("用戶 " + user.getEmail() + " 完成驗證");

        }
    }


    public void generateRememberMeToken(HttpServletResponse response, User user) {
        Token userToken = user.getToken();
        if (userToken.getRememberMeToken() == null || userToken.getRememberMeToken().isBlank() || userToken.getRememberMeTokenExpireTime().isBefore(OffsetDateTime.now(ZoneId.of(user.getTimezone())))) {
            String token = UUID.randomUUID().toString();
            String hashedToken = passwordEncoder.encode(token);
            String base64Token = Base64.getEncoder().encodeToString(hashedToken.getBytes(StandardCharsets.UTF_8));

            int expireTimeDays = 7;
            userToken.createRememberMeToken(hashedToken, expireTimeDays);
            userRepository.save(user);

            Cookie rememberMeCookie = new Cookie("REMEMBER_ME", base64Token);
            rememberMeCookie.setMaxAge(expireTimeDays * 24 * 60 * 60);
            rememberMeCookie.setPath("/");
            rememberMeCookie.setHttpOnly(true);
            rememberMeCookie.setSecure(true);
            response.addCookie(rememberMeCookie);
        }
    }

    @Transactional
    public Long verifyRememberMeToken(String base64Token) throws RuntimeException {
        if (base64Token == null) {
            return null;
        }
        String argonToken = new String(Base64.getDecoder().decode(base64Token), StandardCharsets.UTF_8);
        Token userToken = tokenRepository.findByRememberMeToken(argonToken).orElse(null);
        if (userToken != null) {
            User user = userToken.getUser();
            if (user.getToken().getRememberMeTokenExpireTime().isAfter(OffsetDateTime.now())) {
                return user.getId();
            }
        }
        return null;
    }

    public void deleteRememberMeCookie(HttpServletResponse response, HttpSession session, Cookie[] cookies){
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REMEMBER_ME".equals(cookie.getName())) {
                    User user  = userService.getUserById((Long) session.getAttribute("currentUserId"));
                    Token userToken = user.getToken();
                    if (userToken.getRememberMeToken() != null) {
                        userToken.setRememberMeToken(null);
                        userToken.setRememberMeTokenExpireTime(null);
                        userRepository.save(user);
                    }
                    Cookie rememberMeCookie = new Cookie("REMEMBER_ME", null);
                    rememberMeCookie.setMaxAge(0);
                    rememberMeCookie.setPath("/");
                    rememberMeCookie.setHttpOnly(true);
                    rememberMeCookie.setSecure(true);
                    response.addCookie(rememberMeCookie);
                }
            }
        }
    }

    public String generateJwtToken (User user, HttpServletResponse response) {
        String jwt = jwtTokenProvider.generateToken(user.getId(),user.getToken().getAndIncrementJwtApiCount());
        tokenRepository.save(user.getToken());
        Cookie jwtCookie = new Cookie("JWT", jwt);
        jwtCookie.setPath("/");
        jwtCookie.setHttpOnly(true);
        response.addCookie(jwtCookie);

        return jwt;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeExpiredTokens () {
        logger.info("開始清理過期的token");
        try {
            OffsetDateTime expiredOverDay = OffsetDateTime.now().minusDays(1);
            List<Token> tokens = tokenRepository.findAllByEmailApiTokenExpiryTimeIsBefore(expiredOverDay);
            List<Token> tokens2 = tokenRepository.findAllByRememberMeTokenExpireTimeIsBefore(expiredOverDay);
            for (Token token : tokens) {
                token.setEmailApiToken(null);
                token.setEmailApiTokenExpiryTime(null);
            }
            for (Token token : tokens2) {
                token.setRememberMeToken(null);
                token.setRememberMeTokenExpireTime(null);
            }
            tokenRepository.saveAll(tokens);
            tokenRepository.saveAll(tokens2);
        } catch (Exception e) {
            logger.error("清理過期的token失敗", e);
            throw new RuntimeException("清理過期的token失敗");
        }
    }
}
