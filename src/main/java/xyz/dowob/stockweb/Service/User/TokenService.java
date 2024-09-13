package xyz.dowob.stockweb.Service.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Annotation.SensitiveData;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Component.Provider.JwtTokenProvider;
import xyz.dowob.stockweb.Component.Provider.MailTokenProvider;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Exception.ServiceExceptions;
import xyz.dowob.stockweb.Exception.UserExceptions;
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

import static xyz.dowob.stockweb.Exception.UserExceptions.ErrorEnum.USER_NOT_FOUND;

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

    /**
     * TokenService構造函數
     *
     * @param userRepository    用戶數據庫
     * @param tokenRepository   用戶令牌數據庫
     * @param jwtTokenProvider  jwt 令牌提供器
     * @param passwordEncoder   密碼加密器
     * @param mailTokenProvider 郵件令牌提供器
     * @param userService       用戶服務
     * @param retryTemplate     重試模板
     */
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
     */
    public void sendVerificationEmail(HttpSession session) throws UserExceptions {
        User user = userService.getUserFromJwtTokenOrSession(session);
        if (user.getRole() == Role.UNVERIFIED_USER) {
            mailTokenProvider.sendVerificationEmail(user);
        } else {
            throw new UserExceptions(UserExceptions.ErrorEnum.USER_ALREADY_VERIFIED);
        }
    }

    /**
     * 驗證信箱
     *
     * @param base128Token base128Token
     */
    public void verifyEmail(String base128Token) throws UserExceptions {
        if (base128Token == null || base128Token.isBlank()) {
            throw new UserExceptions(UserExceptions.ErrorEnum.USER_TOKEN_INVALID);
        }
        User user = mailTokenProvider.validateTokenAndReturnUser(base128Token);
        if (user != null) {
            user.setRole(Role.VERIFIED_USER);
            user.getToken().setEmailApiToken(null);
            userRepository.save(user);
        }
    }

    /**
     * 發送重置密碼信到註冊信箱
     *
     * @param email email
     *
     * @throws RuntimeException 找不到用戶
     */
    public void sendResetPasswordEmail(String email) throws UserExceptions {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserExceptions(USER_NOT_FOUND, email));
        if (user.getRole().equals(Role.UNVERIFIED_USER)) {
            throw new UserExceptions(UserExceptions.ErrorEnum.INVALID_EMAIL_CANNOT_USE, "重置密碼");
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
    public void resetPassword(String email, String token, String newPassword) throws UserExceptions {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserExceptions(USER_NOT_FOUND, email));
        String confirmToken = new String(Base64.getDecoder().decode(user.getToken().getEmailApiToken()), StandardCharsets.UTF_8);
        if (!confirmToken.equals(token)) {
            throw new UserExceptions(UserExceptions.ErrorEnum.USER_TOKEN_INVALID);
        }
        userService.validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 生成RememberMe token, 並存入cookie
     *
     * @param response HttpServletResponse
     * @param user     User
     */
    @SensitiveData
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
     */
    @Transactional
    public Long verifyRememberMeToken(String base64Token) {
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
    @SensitiveData
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
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeExpiredTokens() {
        try {
            retryTemplate.doWithRetry(() -> {
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
                    throw new ServiceExceptions(ServiceExceptions.ErrorEnum.CLEAR_EXPIRED_TOKEN_ERROR, e.getMessage());
                }
            });
        } catch (RetryException e) {
            throw new RuntimeException("重試失敗，最後一次錯誤信息：" + e.getLastException().getMessage());
        }
    }

    /**
     * 用於授權用戶權限的方法
     * @param user 用戶
     * @param session 當前連接的Session
     */

    public void authenticateUser(User user, HttpSession session) {
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        session.setAttribute("currentUserId", user.getId());
        session.setAttribute("userMail", user.getEmail());
    }
}
