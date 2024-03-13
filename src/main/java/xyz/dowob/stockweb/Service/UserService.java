package xyz.dowob.stockweb.Service;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Dto.LoginUserDto;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Dto.RegisterUserDto;
import xyz.dowob.stockweb.Repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void registerUser(RegisterUserDto userDto) throws RuntimeException {
        User user = new User();
        validatePassword(userDto.getPassword());
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new RuntimeException("此信箱已經被註冊");
        }
        RegisterUserDto.registerUserDtoToUser(user, userDto);
        user.setPassword(argon2Hash(userDto.getPassword()));
        userRepository.save(user);

    }

    public User loginUser(LoginUserDto userDto, HttpServletResponse re) {
        User user = userRepository.findByEmail(userDto.getEmail())
                .orElseThrow(() -> new RuntimeException("帳號或密碼錯誤"));

        if (!argon2Verify(user.getPassword(), userDto.getPassword())) {
            throw new RuntimeException("帳號或密碼錯誤");
        }

        if (userDto.isRemember_me()) {
            generateRememberMeToken(re, user);
        }
        return user;
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

    private String argon2Hash(String original) {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] originalChars = original.toCharArray();
        try {
            return argon2.hash(2, 1024 * 1024, 1, originalChars);
        } finally {
            argon2.wipeArray(originalChars);
        }
    }

    private boolean argon2Verify(String inRepository, String needToVerify) {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] passwordChars = needToVerify.toCharArray();
        try {
            return argon2.verify(inRepository, passwordChars);
        } finally {
            argon2.wipeArray(passwordChars);
        }
    }


    private void generateRememberMeToken(HttpServletResponse response, User user) {
        if (user.getRememberMeToken() == null || user.getRememberMeToken().isBlank()) {
            String token = UUID.randomUUID().toString();
            String hashedToken = argon2Hash(token);
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
}
