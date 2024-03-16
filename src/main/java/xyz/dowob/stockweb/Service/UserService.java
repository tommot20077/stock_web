package xyz.dowob.stockweb.Service;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.CustomArgon2PasswordEncoder;
import xyz.dowob.stockweb.Component.TokenProvider;
import xyz.dowob.stockweb.Dto.LoginUserDto;
import xyz.dowob.stockweb.Enum.Gender;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Dto.RegisterUserDto;
import xyz.dowob.stockweb.Repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public UserService(UserRepository userRepository, TokenProvider tokenProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
    }



    public void registerUser(RegisterUserDto userDto) throws RuntimeException {
        User user = new User();
        validatePassword(userDto.getPassword());
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new RuntimeException("此信箱已經被註冊");
        }
        RegisterUserDto.registerUserDtoToUser(user, userDto);
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        userRepository.save(user);

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
            user.setEmail(userInfo.get("email"));


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
        if (
                user.getRememberMeToken() == null
                || user.getRememberMeToken().isBlank()
                || user.getRememberMeTokenExpireTime().isBefore(OffsetDateTime.now(ZoneId.of(user.getTimezone())))) {
            String token = UUID.randomUUID().toString();
            String hashedToken = passwordEncoder.encode(token);
            String base64Token = Base64.getEncoder().encodeToString(hashedToken.getBytes(StandardCharsets.UTF_8));

            int expireTimeDays = 7;
            user.createRememberMeToken(hashedToken, expireTimeDays);
            userRepository.save(user);

            Cookie rememberMeCookie = new Cookie("REMEMBER_ME", base64Token);
            rememberMeCookie.setMaxAge(expireTimeDays * 24 * 60 * 60);
            rememberMeCookie.setPath("/");
            rememberMeCookie.setHttpOnly(true);
            rememberMeCookie.setSecure(true);
            response.addCookie(rememberMeCookie);
        }
    }

    public Long verifyRememberMeToken(String base64Token) throws RuntimeException {
        if (base64Token == null) {
            return null;
        }
        String argonToken = new String(Base64.getDecoder().decode(base64Token), StandardCharsets.UTF_8);
        User user = userRepository.findByRememberMeToken(argonToken).orElse(null);
        if (user != null) {
            if (user.getRememberMeTokenExpireTime().isAfter(OffsetDateTime.now())) {
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
                    if (user.getRememberMeToken() != null) {
                        user.setRememberMeToken(null);
                        user.setRememberMeTokenExpireTime(null);
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
        String jwt = tokenProvider.generateToken(user.getId());
        Cookie jwtCookie = new Cookie("JWT", jwt);
        jwtCookie.setPath("/");
        jwtCookie.setHttpOnly(true);
        response.addCookie(jwtCookie);

        return jwt;
    }
}
