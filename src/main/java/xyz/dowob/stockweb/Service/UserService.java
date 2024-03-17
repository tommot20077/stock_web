package xyz.dowob.stockweb.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.JwtTokenProvider;
import xyz.dowob.stockweb.Component.MailTokenProvider;
import xyz.dowob.stockweb.Dto.LoginUserDto;
import xyz.dowob.stockweb.Enum.Gender;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Model.Token;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Dto.RegisterUserDto;
import xyz.dowob.stockweb.Repository.TokenRepository;
import xyz.dowob.stockweb.Repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final MailTokenProvider mailTokenProvider;


    Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public UserService(UserRepository userRepository, TokenRepository tokenRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder, MailTokenProvider mailTokenProvider, ApplicationEventPublisher applicationEventPublisher) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.mailTokenProvider = mailTokenProvider;
    }


    @Transactional(rollbackFor = {Exception.class})
    public void registerUser(RegisterUserDto userDto) throws RuntimeException {
        User user = new User();
        validatePassword(userDto.getPassword());
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new RuntimeException("此信箱已經被註冊");
        }
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirst_name());
        user.setLastName(userDto.getLast_name());
        user.setUsername(user.extractUsernameFromEmail(userDto.getEmail()));
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        userRepository.save(user);

        logger.warn("用戶 " + user.getEmail() + " 註冊成功");
        Token token = new Token();
        token.setUser(user);
        tokenRepository.save(token);
        logger.warn("用戶憑證資料庫建立成功");
    }

    public User loginUser(LoginUserDto userDto, HttpServletResponse re) {
        if (userDto == null) {
            throw new RuntimeException("資料格式錯誤");
        } else {
            User user = userRepository.findByEmail(userDto.getEmail())
                    .orElseThrow(() -> new RuntimeException("帳號或密碼錯誤"));

            if (!passwordEncoder.matches(userDto.getPassword(), user.getPassword())) {
                throw new RuntimeException("帳號或密碼錯誤");
            }

            if (userDto.isRemember_me()) {
                generateRememberMeToken(re, user);
            }
            return user;
        }
    }

    public void updateUserDetail(User user, Map<String, String> userInfo) {

        String originalPassword = userInfo.get("originalPassword");
        if (originalPassword != null && !originalPassword.isBlank() && passwordEncoder.matches(userInfo.get("originalPassword"), user.getPassword())) {
            if (userInfo.get("newPassword") != null && !userInfo.get("newPassword").isBlank()) {
                validatePassword(userInfo.get("newPassword"));
                user.setPassword(passwordEncoder.encode(userInfo.get("newPassword")));
                logger.warn("用戶 " + user.getEmail() + " 更改密碼");
            }

            user.setFirstName(userInfo.get("firstName"));
            user.setLastName(userInfo.get("lastName"));

            if (userInfo.get("email") != null && !userInfo.get("email").isBlank() && !user.getEmail().equals(userInfo.get("email"))) {
                if (userRepository.findByEmail(userInfo.get("email")).isPresent()) {
                    throw new RuntimeException("此信箱已經被註冊");
                }
                user.setEmail(userInfo.get("email"));
                user.setRole(Role.UNVERIFIED_USER);
                user.setUsername(user.extractUsernameFromEmail(userInfo.get("email")));

                mailTokenProvider.sendVerificationEmail(user);//還沒檢查
                logger.warn("用戶 " + user.getEmail() + " 更改信箱");
            }


            TimeZone timeZone = TimeZone.getTimeZone(userInfo.get("timeZone"));
            if (timeZone != null && !"GMT".equals(timeZone.getID())) {
                user.setTimezone(timeZone.getID());
            } else {
                user.setTimezone("Etc/UTC");
            }
            user.setTimezone(userInfo.get("timeZone"));

            try {
                user.setGender(Gender.valueOf(userInfo.get("gender")));
            } catch (IllegalArgumentException e) {
                user.setGender(Gender.OTHER);
            }
            userRepository.save(user);
        } else {
            throw new RuntimeException("密碼錯誤");
        }
    }



    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }



    private void validatePassword(String userPassword) {
        if (userPassword.length() < 8) {
            throw new RuntimeException("密碼最少需要8個字元");
        }
        if (userPassword.length() > 100) {
            throw new RuntimeException("密碼最多可以100個字元");
        }
        if (!userPassword.matches(".*[A-Za-z].*")) {
            throw new RuntimeException("密碼必須包含至少一個英文字母");
        }
        if (!userPassword.matches(".*\\d.*")) {
            throw new RuntimeException("密碼必須包含至少一個數字");
        }
    }

    private void generateRememberMeToken(HttpServletResponse response, User user) {
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
                if (cookie.getName().equals("REMEMBER_ME")) {
                    User user  = getUserById((Long) session.getAttribute("currentUserId"));
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


    public User getUserFromJwtTokenOrSession (HttpSession session) {
        Long userId = (Long) session.getAttribute("currentUserId");
        if (userId == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserDetails userDetails) {
                userId = ((User) userDetails).getId();
            }
        }
        return getUserById(userId);
    }

    public void sendVerificationEmail(HttpSession session) {
        User user = getUserFromJwtTokenOrSession(session);
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
}


