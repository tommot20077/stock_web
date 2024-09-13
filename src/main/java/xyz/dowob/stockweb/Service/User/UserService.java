package xyz.dowob.stockweb.Service.User;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Annotation.MeaninglessData;
import xyz.dowob.stockweb.Component.Provider.MailTokenProvider;
import xyz.dowob.stockweb.Dto.Subscription.UserSubscriptionDto;
import xyz.dowob.stockweb.Dto.User.LoginUserDto;
import xyz.dowob.stockweb.Dto.User.RegisterUserDto;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Enum.Gender;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Exception.AssetExceptions;
import xyz.dowob.stockweb.Exception.FormatExceptions;
import xyz.dowob.stockweb.Exception.UserExceptions;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Token;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.Currency.CurrencyRepository;
import xyz.dowob.stockweb.Repository.User.SubscribeRepository;
import xyz.dowob.stockweb.Repository.User.TokenRepository;
import xyz.dowob.stockweb.Repository.User.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static xyz.dowob.stockweb.Exception.AssetExceptions.ErrorEnum.CURRENCY_DATA_UPDATING;
import static xyz.dowob.stockweb.Exception.AssetExceptions.ErrorEnum.DEFAULT_CURRENCY_NOT_FOUND;
import static xyz.dowob.stockweb.Exception.FormatExceptions.ErrorEnum.JSON_FORMAT_ERROR;
import static xyz.dowob.stockweb.Exception.UserExceptions.ErrorEnum.*;

/**
 * 用戶相關服務
 *
 * @author yuan
 */
@Service
public class UserService {
    private final UserRepository userRepository;

    private final TokenRepository tokenRepository;

    private final TokenService tokenService;

    private final CurrencyRepository currencyRepository;

    private final SubscribeRepository subscribeRepository;

    private final PasswordEncoder passwordEncoder;

    private final MailTokenProvider mailTokenProvider;

    /**
     * UserService構造函數
     *
     * @param userRepository      用戶數據庫
     * @param tokenRepository     憑證數據庫
     * @param tokenService        憑證服務
     * @param currencyRepository  貨幣數據庫
     * @param subscribeRepository 訂閱數據庫
     * @param passwordEncoder     密碼加密器
     * @param mailTokenProvider   郵件憑證提供者
     */
    public UserService(
            UserRepository userRepository, TokenRepository tokenRepository, @Lazy TokenService tokenService, CurrencyRepository currencyRepository, SubscribeRepository subscribeRepository, PasswordEncoder passwordEncoder, MailTokenProvider mailTokenProvider) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.currencyRepository = currencyRepository;
        this.subscribeRepository = subscribeRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailTokenProvider = mailTokenProvider;
    }

    /**
     * 註冊用戶
     *
     * @param userDto 用戶資料 Dto物件 {@link RegisterUserDto}
     */
    @Transactional(rollbackFor = {Exception.class})
    public void registerUser(RegisterUserDto userDto) throws UserExceptions, AssetExceptions {
        User user = new User();
        validatePassword(userDto.getPassword());
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new UserExceptions(EMAIL_ALREADY_EXISTS);
        }
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setUsername(user.extractUsernameFromEmail(userDto.getEmail()));
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setPreferredCurrency(currencyRepository.findByCurrency("USD").orElseThrow(() -> new AssetExceptions(CURRENCY_DATA_UPDATING)));
        if (userRepository.findAll().isEmpty()) {
            user.setRole(Role.ADMIN);
        }
        userRepository.save(user);
        Token token = new Token();
        token.setUser(user);
        tokenRepository.save(token);
    }

    /**
     * 登入用戶
     *
     * @param userDto 用戶資料 Dto物件
     * @param re      HttpServletResponse
     *
     * @return User
     */
    public User loginUser(LoginUserDto userDto, HttpServletResponse re) throws UserExceptions {
        if (userDto == null) {
            throw new UserExceptions(LOGIN_DATA_NOT_FOUND);
        } else {
            User user = userRepository.findByEmail(userDto.getEmail()).orElseThrow(() -> new UserExceptions(USERNAME_OR_PASSWORD_WRONG));
            if (!passwordEncoder.matches(userDto.getPassword(), user.getPassword())) {
                throw new UserExceptions(USERNAME_OR_PASSWORD_WRONG);
            }
            if (userDto.isRememberMe()) {
                tokenService.generateRememberMeToken(re, user);
            }
            return user;
        }
    }

    /**
     * 更新用戶資料
     *
     * @param user     用戶
     * @param userInfo 用戶資料 Map 物件
     *                 originalPassword: 原密碼
     *                 newPassword: 新密碼
     *                 email: 信箱
     *                 firstName: 名
     *                 lastName: 姓
     *                 timeZone: 時區
     */
    public void updateUserDetail(User user, Map<String, String> userInfo) throws UserExceptions, AssetExceptions {
        String originalPassword = userInfo.get("originalPassword");
        if (originalPassword != null && !originalPassword.isBlank() && passwordEncoder.matches(userInfo.get("originalPassword"),
                                                                                               user.getPassword())) {
            if (userInfo.get("newPassword") != null && !userInfo.get("newPassword").isBlank()) {
                validatePassword(userInfo.get("newPassword"));
                user.setPassword(passwordEncoder.encode(userInfo.get("newPassword")));
            }
            user.setFirstName(userInfo.get("firstName"));
            user.setLastName(userInfo.get("lastName"));
            if (userInfo.get("email") != null && !userInfo.get("email").isBlank() && !user.getEmail().equals(userInfo.get("email"))) {
                if (userRepository.findByEmail(userInfo.get("email")).isPresent()) {
                    throw new UserExceptions(EMAIL_ALREADY_EXISTS);
                }
                user.setEmail(userInfo.get("email"));
                user.setRole(Role.UNVERIFIED_USER);
                user.setUsername(user.extractUsernameFromEmail(userInfo.get("email")));
                mailTokenProvider.sendVerificationEmail(user);
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
            Currency currency = currencyRepository.findByCurrency(userInfo.get("preferredCurrency")).orElse(null);
            if (currency != null && !currency.getCurrency().equals(user.getPreferredCurrency().getCurrency())) {
                try {
                    user.setPreferredCurrency(currency);
                    subscribeRepository.findAllByUserAndAssetAssetType(user, AssetType.CURRENCY).forEach(subscribe -> {
                        subscribe.setChannel(currency.getCurrency());
                        subscribeRepository.save(subscribe);
                    });
                } catch (Exception e) {
                    user.setPreferredCurrency(currencyRepository.findByCurrency("USD")
                                                                .orElseThrow(() -> new AssetExceptions(DEFAULT_CURRENCY_NOT_FOUND, "USD")));
                }
            }
            userRepository.save(user);
        } else {
            throw new UserExceptions(USERNAME_OR_PASSWORD_WRONG);
        }
    }

    /**
     * 根據userId尋找用戶
     *
     * @param id 用戶ID
     *
     * @return User
     */
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    /**
     * 獲取所有用戶
     *
     * @return List<User>
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 驗證密碼
     *
     * @param userPassword 用戶密碼
     *
     * @throws UserExceptions 當密碼不符合規定時拋出
     *                        當密碼不包含英文字母時拋出
     *                        當密碼不包含數字時拋出
     *                        當密碼長度不足時拋出
     *                        當密碼長度過長時拋出
     */
    public void validatePassword(String userPassword) throws UserExceptions {
        if (userPassword.length() < 8) {
            throw new UserExceptions(PASSWORD_MIN_LENGTH_LIMIT);
        }
        if (userPassword.length() > 100) {
            throw new UserExceptions(PASSWORD_MAX_LENGTH_LIMIT);
        }
        if (!userPassword.matches(".*[A-Za-z].*")) {
            throw new UserExceptions(PASSWORD_MUST_CONTAIN_LOWERCASE_LETTER);
        }
        if (!userPassword.matches(".*\\d.*")) {
            throw new UserExceptions(PASSWORD_MUST_CONTAIN_DIGIT);
        }
    }

    /**
     * 根據JWT Token或Session獲取用戶
     *
     * @param session HttpSession
     *
     * @return User
     */
    public User getUserFromJwtTokenOrSession(HttpSession session) {
        Long userId = (Long) session.getAttribute("currentUserId");
        if (userId == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserDetails userDetails) {
                userId = ((User) userDetails).getId();
            }
        }
        return getUserById(userId);
    }

    /**
     * 獲取用戶訂閱資料
     *
     * @param user 用戶
     *
     * @return String
     */
    @MeaninglessData
    public String getChannelAndAssetAndRemoveAbleByUserId(User user) throws FormatExceptions {
        List<UserSubscriptionDto> subscriptionDtoList = new ArrayList<>();
        subscribeRepository.getChannelAndAssetAndRemoveAbleByUserId(user).forEach(objects -> {
            String channel = null;
            if (objects[0] != null) {
                channel = objects[0].toString();
            }
            Asset asset = (Asset) objects[1];
            boolean removeAble = (boolean) objects[2];
            String subscribeName = switch (asset.getAssetType()) {
                case STOCK_TW -> {
                    StockTw stockTw = (StockTw) asset;
                    yield stockTw.getStockCode() + "⎯" + stockTw.getStockName();
                }
                case CURRENCY -> {
                    Currency currency = (Currency) asset;
                    yield currency.getCurrency() + " ⇄ " + channel;
                }
                case CRYPTO -> {
                    CryptoTradingPair cryptoTradingPair = (CryptoTradingPair) asset;
                    yield cryptoTradingPair.getTradingPair();
                }
            };
            subscriptionDtoList.add(new UserSubscriptionDto(asset.getId().toString(),
                                                            asset.getAssetType().toString(),
                                                            subscribeName,
                                                            removeAble));
        });
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(subscriptionDtoList);
        } catch (JsonProcessingException e) {
            throw new FormatExceptions(JSON_FORMAT_ERROR);
        }
    }
}
