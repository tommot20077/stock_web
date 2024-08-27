package xyz.dowob.stockweb.Controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.User.LoginUserDto;
import xyz.dowob.stockweb.Dto.User.RegisterUserDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;

import static xyz.dowob.stockweb.Enum.Role.ADMIN;

/**
 * 這是一個用於處理網頁頁面的控制器
 * @author yuan
 */
@Controller
public class PageController {
    private final UserService userService;

    private final TokenService tokenService;

    private final AssetService assetService;

    /**
     * 這是一個構造函數，用於注入UserService和TokenService
     * @param userService 用戶服務
     * @param tokenService 用戶令牌服務
     */
    @Autowired
    public PageController(UserService userService, TokenService tokenService, AssetService assetService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.assetService = assetService;
    }

    /**
     * 前往登入頁面
     *
     * @return 登入頁面
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * 登入(網頁前端使用)
     *
     * @param loginUserDto 登入資訊
     * @param response     response
     * @param session      session
     *
     * @return ResponseEntity
     */
    @PostMapping("/login_p")
    public ResponseEntity<?> loginUser(
            @RequestBody LoginUserDto loginUserDto, HttpServletResponse response, HttpSession session) {
        try {
            User user = userService.loginUser(loginUserDto, response);
            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            session.setAttribute("currentUserId", user.getId());
            session.setAttribute("userMail", user.getEmail());

            return ResponseEntity.ok().body("登入成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    /**
     * 前往註冊頁面
     *
     * @return 註冊頁面
     */
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    /**
     * 註冊請求
     *
     * @param registerUserDto 註冊資訊
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
     * 前往首頁
     *
     * @param session session
     * @param model   model
     *
     * @return 首頁
     */
    @GetMapping({"/index", "/"})
    public String index2(HttpSession session, Model model) {
        if (session.getAttribute("currentUserId") != null) {
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            model.addAttribute(user);
        }
        return "index";
    }

    /**
     * 前往個人資料頁面
     *
     * @return 個人資料頁面
     */
    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    /**
     * 登出
     *
     * @param session     session
     * @param request     request
     * @param response    response
     * @param redirection 是否重導向
     */
    @GetMapping("/logout")
    public void logout(
            HttpSession session, HttpServletRequest request, HttpServletResponse response, @RequestParam(required = false,
                                                                                                         name = "redirection",
                                                                                                         defaultValue = "true") boolean redirection) {
        Cookie[] cookies = request.getCookies();
        tokenService.deleteRememberMeCookie(response, session, cookies);
        session.invalidate();
        if (redirection) {
            response.setHeader("Location", "/login");
            response.setStatus(302);
        }
    }

    /**
     * 前往重設密碼頁面
     *
     * @return 重設密碼頁面
     */
    @GetMapping("/reset_password")
    public String resetPassword() {
        return "resetPassword";
    }

    /**
     * 前往用戶財產頁面
     *
     * @return 用戶財產頁面
     */
    @GetMapping("/property_info")
    public String propertyEdit() {
        return "propertyInfo";
    }

    /**
     * 前往交易資訊頁面
     *
     * @return 交易資訊頁面
     */
    @GetMapping("/transaction_info")
    public String transactionEdit() {
        return "transactionInfo";
    }

    /**
     * 前往用戶訂閱資訊頁面
     *
     * @return 用戶訂閱資訊頁面
     */
    @GetMapping("/user_subscribe")
    public String userSubscribe() {
        return "subscribeInfo";
    }

    /**
     * 前往資產資訊頁面, assetId為資產ID
     * 若資產不存在則重導向至首頁
     *
     * @return 資產資訊頁面
     */
    @GetMapping("/asset_info/{assetId}")
    public String assetInfo(@PathVariable Long assetId) {
        try {
            assetService.getAssetById(assetId);
            return "assetInfo";
        } catch (RuntimeException e) {
            return "redirect:/";
        }
    }

    /**
     * 前往個別種類新聞頁面, category為新聞類別
     *
     * @return 新聞頁面
     */
    @GetMapping("/news/{category}")
    public String news() {
        return "news";
    }

    /**
     * 前往個別種類資產頁面, category為資訊類別
     *
     * @return 資訊頁面
     */
    @GetMapping("/info/{category}")
    public String info() {
        return "info";
    }

    @GetMapping("/debt_info")
    public String debtInfo() {
        return "debtInfo";
    }

    /**
     * 前往管理員管理頁面, 需要管理員權限
     *
     * @param session session
     *
     * @return 管理員管理頁面
     */
    @GetMapping("/admin/serverManage")
    public String adminServerConfig(HttpSession session) {
        User user = userService.getUserFromJwtTokenOrSession(session);
        if (user == null) {
            return "redirect:/login";
        } else if (!user.getRole().equals(ADMIN)) {
            return "redirect:/";
        }
        return "serverManage";
    }

    @GetMapping("/testws/{id}")
    public String testws(@PathVariable String id) {
        return "testws";
    }
}
