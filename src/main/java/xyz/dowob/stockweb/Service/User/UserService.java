package xyz.dowob.stockweb.Service.User;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Provider.MailTokenProvider;
import xyz.dowob.stockweb.Dto.User.LoginUserDto;
import xyz.dowob.stockweb.Enum.Gender;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Model.User.Token;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Dto.User.RegisterUserDto;
import xyz.dowob.stockweb.Repository.User.TokenRepository;
import xyz.dowob.stockweb.Repository.User.UserRepository;

import java.util.*;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final TokenService tokenService;

    private final PasswordEncoder passwordEncoder;
    private final MailTokenProvider mailTokenProvider;


    Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public UserService(UserRepository userRepository, TokenRepository tokenRepository, @Lazy TokenService tokenService, PasswordEncoder passwordEncoder, MailTokenProvider mailTokenProvider) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
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
                tokenService.generateRememberMeToken(re, user);
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

                mailTokenProvider.sendVerificationEmail(user);
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

}


