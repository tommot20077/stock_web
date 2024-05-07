package xyz.dowob.stockweb.Service.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Component.Provider.JwtTokenProvider;
import xyz.dowob.stockweb.Component.Provider.MailTokenProvider;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Exception.RetryException;
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

/**
 * @author yuan
 * token相關的業務邏輯
 * 1驗證信箱
 * 2重置密碼
 * 3生成JWT token
 * 4生成RememberMe token
 */
@Service
public class TokenService {
    private final UserRepository userRepository;

    private final TokenRepository tokenRepository;

    private final JwtTokenProvider jwtTokenProvider;

    private final PasswordEncoder passwordEncoder;

    private final MailTokenProvider mailTokenProvider;

    private final UserService userService;

    private final RetryTemplate retryTemplate;

    Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public TokenService(UserRepository userRepository, TokenRepository tokenRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder, MailTokenProvider mailTokenProvider, UserService userService, RetryTemplate retryTemplate) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.mailTokenProvider = mailTokenProvider;
        this.userService = userService;
        this.retryTemplate = retryTemplate;
    }

    /**
     * 發送驗證信到註冊信箱
     *
     * @param session HttpSession
     *                用戶未驗證時發送驗證信
     *
     * @throws RuntimeException 用戶已經完成驗證
     */

    public void sendVerificationEmail(HttpSession session) {
        User user = userService.getUserFromJwtTokenOrSession(session);
        if (user.getRole() == Role.UNVERIFIED_USER) {
            mailTokenProvider.sendVerificationEmail(user);
        } else {
            throw new RuntimeException("用戶已經完成驗證");
        }
    }


    /**
     * 驗證信箱
     *
     * @param base128Token base128Token
     *
     * @throws RuntimeException 密鑰不存在
     */
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

    /**
     * 發送重置密碼信到註冊信箱
     *
     * @param email email
     *
     * @throws RuntimeException 找不到用戶
     */
    public void sendResetPasswordEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("找不到用戶: " + email));
        if (user.getRole().equals(Role.UNVERIFIED_USER)) {
            throw new RuntimeException("未驗證信箱用戶，無法重置密碼");
        }
        mailTokenProvider.sendResetPasswordEmail(user);
    }

    /**
     * 重置密碼 (忘記密碼)
     *
     * @param email       email
     * @param token       token
     * @param newPassword 設定的新密碼
     */
    public void resetPassword(String email, String token, String newPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("找不到用戶: " + email));
        String confirmToken = new String(Base64.getDecoder().decode(user.getToken().getEmailApiToken()), StandardCharsets.UTF_8);
        if (!confirmToken.equals(token)) {
            logger.warn(user.getEmail() + " 重置密碼的驗證碼不正確");
            throw new RuntimeException("驗證碼不正確");
        }
        userService.validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("用戶 " + user.getEmail() + " 重置密碼成功");
    }


    /**
     * 生成RememberMe token, 並存入cookie
     *
     * @param response HttpServletResponse
     * @param user     User
     */
    public void generateRememberMeToken(HttpServletResponse response, User user) {
        Token userToken = user.getToken();
        if (userToken.getRememberMeToken() == null || userToken.getRememberMeToken().isBlank() || userToken.getRememberMeTokenExpireTime()
                                                                                                           .isBefore(OffsetDateTime.now(
                                                                                                                   ZoneId.of(user.getTimezone())))) {
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

    /**
     * 驗證RememberMe token
     *
     * @param base64Token base64Token
     *
     * @return Long userId
     *
     * @throws RuntimeException token不存在
     */
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

    /**
     * 刪除RememberMe cookie
     *
     * @param response HttpServletResponse
     * @param session  HttpSession
     * @param cookies  Cookie[]
     */
    public void deleteRememberMeCookie(HttpServletResponse response, HttpSession session, Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REMEMBER_ME".equals(cookie.getName())) {
                    User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
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

    /**
     * 生成JWT token
     *
     * @param user     User
     * @param response HttpServletResponse
     *
     * @return String jwt
     */
    public String generateJwtToken(User user, HttpServletResponse response) {
        String jwt = jwtTokenProvider.generateToken(user.getId(), user.getToken().getAndIncrementJwtApiCount());
        tokenRepository.save(user.getToken());
        Cookie jwtCookie = new Cookie("JWT", jwt);
        jwtCookie.setPath("/");
        jwtCookie.setHttpOnly(true);
        response.addCookie(jwtCookie);

        return jwt;
    }

    /**
     * 刪除過期的emailApiToken
     *
     * @throws RuntimeException 清理過期的token失敗
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeExpiredTokens() {
        try {
            retryTemplate.doWithRetry(() -> {
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
            });
        } catch (RetryException e) {
            logger.error("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage(), e);
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }
}
