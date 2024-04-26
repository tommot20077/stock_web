package xyz.dowob.stockweb.Controller.Api.User;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.User.LoginUserDto;
import xyz.dowob.stockweb.Dto.User.RegisterUserDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.News;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.NewsService;
import xyz.dowob.stockweb.Service.Common.RedisService;
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequestMapping("/api/user/common")
@RestController
public class ApiUserController {
    private final UserService userService;
    private final TokenService tokenService;
    private final NewsService newsService;
    private final RedisService redisService;
    private final AssetService assetService;
    @Autowired
    public ApiUserController(UserService userService, TokenService tokenService, NewsService newsService, RedisService redisService, AssetService assetService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.newsService = newsService;
        this.redisService = redisService;
        this.assetService = assetService;
    }


    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterUserDto registerUserDto) {
        try {
            userService.registerUser(registerUserDto);
            return ResponseEntity.ok().body("註冊成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginUserDto loginUserDto, HttpServletResponse response, HttpSession session){
        try {
            User user = userService.loginUser(loginUserDto, response);
            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            session.setAttribute("currentUserId", user.getId());


            String jwt = tokenService.generateJwtToken(user, response);
            Map<String, String> tokenMap = new HashMap<>();
            tokenMap.put("token", jwt);

            return ResponseEntity.ok().body(tokenMap);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/getUserDetail")
    public ResponseEntity<?> getUserDetail(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("email", user.getEmail());
                userInfo.put("firstName", user.getFirstName());
                userInfo.put("lastName", user.getLastName());
                userInfo.put("id", user.getId());
                userInfo.put("role", user.getRole());
                userInfo.put("gender", user.getGender().toString());
                userInfo.put("timeZone", user.getTimezone());
                userInfo.put("preferredCurrency", user.getPreferredCurrency().getCurrency());

                return ResponseEntity.ok(userInfo);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到用戶");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }

    @PostMapping("/updateUserDetail")
    public ResponseEntity<?> updateUserDetail(@RequestBody Map<String, String> userInfo, HttpSession session) {

        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user != null) {
                userService.updateUserDetail(user, userInfo);
                return ResponseEntity.ok().body("更新成功");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到用戶");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("錯誤: "+e.getMessage());
        }

    }


    @PostMapping("/sendVerificationEmail")
    public ResponseEntity<?> verifyEmail(HttpSession session) {
        try {
            tokenService.sendVerificationEmail(session);
            return ResponseEntity.ok().body("已寄出驗證信");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }

    @GetMapping("/verifyEmail")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            tokenService.verifyEmail(token);
            return ResponseEntity.ok().body("驗證成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("驗證失敗: "+e.getMessage());
        }
    }

    @GetMapping("/getTimeZoneList")
    public ResponseEntity<?> getTimeZoneList() {

        List<String> timezones = ZoneId.getAvailableZoneIds()
                .stream()
                .sorted()
                .collect(Collectors.toList());

        return ResponseEntity.ok().body(timezones);
    }


    @GetMapping("/getCsrfToken")
    public ResponseEntity<?> getCsrfToken(HttpServletRequest request) {
        try {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return ResponseEntity.ok().body(csrfToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }

    @GetMapping("/getUserSubscriptionsList")
    public ResponseEntity<?> getUserSubscriptionsList(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user != null) {
                return ResponseEntity.ok().body(userService.getChannelAndAssetAndRemoveAbleByUserId(user));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到用戶");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }

    @GetMapping("/getNews")
    public ResponseEntity<?> getNews(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "asset", required = false) Long assetId,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page) {
        String key;
        Asset asset = null;
        if (assetId != null) {
            asset = assetService.getAssetById(assetId);
            type = asset.getId().toString();
        }
        if (type != null && !type.isEmpty() && asset != null) {
            key = "news_" + asset.getId() + "_page_" + page;
        } else if (type != null && !type.isEmpty()) {
            key = "news_" + type.toLowerCase() + "_page_" + page;
        } else {
            return ResponseEntity.badRequest().body("沒有任何查詢參數可以使用");
        }

        try {
            String cachedNewsJson = redisService.getCacheValueFromKey(key);
            if (cachedNewsJson != null) {
                System.out.println("取得緩存");
                return ResponseEntity.ok().body(cachedNewsJson);
            } else {
                Page<News> news;
                if (asset != null){
                    news = newsService.getAllNewsByAsset(asset, page);
                } else {
                    news = newsService.getAllNewsByType(type, page);
                }

                String newsJson = newsService.formatNewsListToJson(news);
                if (newsJson == null) {
                    Map<String, String> result = new HashMap<>();
                    result.put("result", "沒有新聞");
                    return ResponseEntity.ok().body(result);
                } else {
                    redisService.saveValueToCache(key, newsJson, 4);
                    return ResponseEntity.ok().body(newsJson);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }
}
