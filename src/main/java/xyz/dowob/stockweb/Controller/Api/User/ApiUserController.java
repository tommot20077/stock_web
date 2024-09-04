package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import xyz.dowob.stockweb.Dto.User.TodoDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.News;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.NewsService;
import xyz.dowob.stockweb.Service.Common.RedisService;
import xyz.dowob.stockweb.Service.User.TodoService;
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 這是一個用於處理用戶相關的控制器
 *
 * @author yuan
 */
@RequestMapping("/api/user/common")
@RestController
public class ApiUserController {
    private final UserService userService;

    private final TokenService tokenService;

    private final NewsService newsService;

    private final RedisService redisService;

    private final AssetService assetService;

    private final TodoService todoService;


    /**
     * 這是一個構造函數
     *
     * @param userService  用戶相關服務
     * @param tokenService 用戶令牌相關服務
     * @param newsService  新聞相關服務
     * @param redisService 緩存服務
     * @param assetService 資產相關服務
     * @param todoService  待辦事項相關服務
     */
    @Autowired
    public ApiUserController(UserService userService, TokenService tokenService, NewsService newsService, RedisService redisService, AssetService assetService, TodoService todoService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.newsService = newsService;
        this.redisService = redisService;
        this.assetService = assetService;
        this.todoService = todoService;
    }


    /**
     * 註冊用戶
     *
     * @param registerUserDto 註冊用戶資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestBody RegisterUserDto registerUserDto) {
        try {
            userService.registerUser(registerUserDto);
            return ResponseEntity.ok().body("註冊成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 登入用戶
     *
     * @param loginUserDto 登入用戶資料
     * @param response     HttpServletResponse
     * @param session      HttpSession
     *
     * @return ResponseEntity
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @RequestBody LoginUserDto loginUserDto, HttpServletResponse response, HttpSession session) {
        try {
            User user = userService.loginUser(loginUserDto, response);
            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            session.setAttribute("currentUserId", user.getId());
            session.setAttribute("userMail", user.getEmail());


            String jwt = tokenService.generateJwtToken(user, response);
            Map<String, String> tokenMap = new HashMap<>();
            tokenMap.put("token", jwt);

            return ResponseEntity.ok().body(tokenMap);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 重設密碼驗證信
     *
     * @param userInfo 用戶資訊 email
     *
     * @return ResponseEntity
     */
    @PostMapping("/sendResetPasswordEmail")
    public ResponseEntity<?> sendResetPasswordEmail(@RequestBody Map<String, String> userInfo) {
        try {
            String email = userInfo.get("email");
            tokenService.sendResetPasswordEmail(email);

            return ResponseEntity.ok().body("已發送");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 重設密碼
     *
     * @param userInfo 用戶資訊 email, token, newPassword
     *
     * @return ResponseEntity
     */
    @PostMapping("/resetPassword")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> userInfo) {
        try {
            tokenService.resetPassword(userInfo.get("email"), userInfo.get("token"), userInfo.get("newPassword"));
            return ResponseEntity.ok().body("重設密碼成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    /**
     * 取得用戶詳細資訊
     *
     * @param session HttpSession
     *
     * @return ResponseEntity
     */
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 更新用戶詳細資訊
     *
     * @param userInfo 用戶資訊
     * @param session  HttpSession
     *
     * @return ResponseEntity
     */
    @PutMapping("/updateUserDetail")
    public ResponseEntity<?> updateUserDetail(
            @RequestBody Map<String, String> userInfo, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user != null) {
                userService.updateUserDetail(user, userInfo);
                return ResponseEntity.ok().body("更新成功");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到用戶");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("錯誤: " + e.getMessage());
        }
    }


    /**
     * 發送用戶信箱確認信
     *
     * @param session HttpSession
     *
     * @return ResponseEntity
     */
    @PostMapping("/sendVerificationEmail")
    public ResponseEntity<?> verifyEmail(HttpSession session) {
        try {
            tokenService.sendVerificationEmail(session);
            return ResponseEntity.ok().body("已寄出驗證信");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 驗證信箱token
     *
     * @param token 驗證token
     *
     * @return ResponseEntity
     */
    @GetMapping("/verifyEmail")
    public ResponseEntity<?> verifyEmail(
            @RequestParam("token") String token) {
        try {
            tokenService.verifyEmail(token);
            return ResponseEntity.ok().body("驗證成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("驗證失敗: " + e.getMessage());
        }
    }

    /**
     * 獲取時區列表
     *
     * @return ResponseEntity
     */
    @GetMapping("/getTimeZoneList")
    public ResponseEntity<?> getTimeZoneList() {

        List<String> timezones = ZoneId.getAvailableZoneIds().stream().sorted().collect(Collectors.toList());

        return ResponseEntity.ok().body(timezones);
    }


    /**
     * 獲取csrf token
     *
     * @param request HttpServletRequest
     *
     * @return ResponseEntity
     */
    @GetMapping("/getCsrfToken")
    public ResponseEntity<?> getCsrfToken(HttpServletRequest request) {
        try {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return ResponseEntity.ok().body(csrfToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 獲取用戶訂閱列表
     *
     * @param session HttpSession
     *
     * @return ResponseEntity
     */
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 獲取新聞,使用assetId或category查詢
     *
     * @param category 類別
     * @param assetId  資產ID
     * @param page     頁數
     *
     * @return ResponseEntity
     */
    @GetMapping("/getNews")
    public ResponseEntity<?> getNews(
            @RequestParam(name = "category",
                          required = false) String category, @RequestParam(name = "asset",
                                                                           required = false) Long assetId, @RequestParam(name = "page",
                                                                                                                         required = false,
                                                                                                                         defaultValue = "1") int page) {
        String innerKey;
        Asset asset = null;
        if (assetId != null) {
            asset = assetService.getAssetById(assetId);
            category = asset.getId().toString();
        }
        if (category != null && !category.isEmpty() && asset != null) {
            innerKey = asset.getId() + "_page_" + page;
        } else if (category != null && !category.isEmpty()) {
            innerKey = category.toLowerCase() + "_page_" + page;
        } else {
            return ResponseEntity.badRequest().body("沒有任何查詢參數可以使用");
        }

        try {
            String cachedNewsJson = redisService.getHashValueFromKey("news", innerKey);
            if (cachedNewsJson != null) {
                return ResponseEntity.ok().body(cachedNewsJson);
            } else {
                Page<News> news;
                if (asset != null) {
                    news = newsService.getAllNewsByAsset(asset, page);
                } else {
                    news = newsService.getAllNewsByType(category, page);
                }

                String newsJson = newsService.formatNewsListToJson(news);
                if (newsJson == null) {
                    Map<String, String> result = new HashMap<>();
                    result.put("result", "沒有新聞");
                    return ResponseEntity.ok().body(result);
                } else {
                    redisService.saveHashToCache("news", innerKey, newsJson, 4);
                    return ResponseEntity.ok().body(newsJson);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 獲取所有待辦事項列表
     *
     * @param session HttpSession
     *
     * @return ResponseEntity
     */
    @GetMapping("/getTodoList")
    public ResponseEntity<?> getTodoList(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user != null) {
                List<TodoDto> todoListDto = todoService.findAllByUser(user);
                return ResponseEntity.ok().body(todoService.formatToJson(todoListDto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到用戶");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 新增待辦事項
     *
     * @param todoDto 待辦事項
     * @param session HttpSession
     *
     * @return ResponseEntity
     */
    @PostMapping("/addTodoList")
    public ResponseEntity<?> addTodoList(
            @RequestBody TodoDto todoDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user != null) {
                todoService.addTodo(todoDto, user);
                return ResponseEntity.ok().body("新增成功");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到用戶");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 刪除待辦事項
     *
     * @param todoListId 待辦事項ID
     *
     * @return ResponseEntity
     */
    @PostMapping("/deleteTodoList")
    public ResponseEntity<?> deleteTodoList(@RequestBody List<Long> todoListId) {
        try {
            for (Long id : todoListId) {
                todoService.delete(id);
            }
            return ResponseEntity.ok().body("刪除成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }
}
