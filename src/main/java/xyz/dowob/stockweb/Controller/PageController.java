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
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;


@Controller
public class PageController {

    private final UserService userService;
    private final TokenService tokenService;
    @Autowired
    public PageController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }




    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login_p")
    public ResponseEntity<?> loginUser(@RequestBody LoginUserDto loginUserDto, HttpServletResponse response, HttpSession session){
        try {
            User user = userService.loginUser(loginUserDto, response);
            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            session.setAttribute("currentUserId", user.getId());

            return ResponseEntity.ok().body("登入成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }





    @GetMapping("/register")
    public String register() {
        return "register";
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


    @GetMapping({"/index", "/"})
    public String index2 (HttpSession session, Model model) {
        if (session.getAttribute("currentUserId") != null) {
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            model.addAttribute(user);
        }
        return "index";
    }

    @GetMapping("/profile")
    public String profile () {
        return "profile";
    }


    @GetMapping("/logout")
    public void logout(HttpSession session, HttpServletRequest request, HttpServletResponse response, @RequestParam(required = false, name = "redirection", defaultValue = "true") boolean redirection) {
        Cookie[] cookies = request.getCookies();
        tokenService.deleteRememberMeCookie(response, session, cookies);
        session.invalidate();
        if (redirection) {
            response.setHeader("Location", "/login");
            response.setStatus(302);
        }
    }

    @GetMapping("/property_info")
    public String propertyEdit () {
        return "propertyInfo";
    }

    @GetMapping("/transaction_info")
    public String transactionEdit () {
        return "transactionInfo";
    }

    @GetMapping("/user_subscribe")
    public String userSubscribe () {
        return "subscribeInfo";
    }

    @GetMapping("/asset_info/{assetId}")
    public String assetInfo (@PathVariable Long assetId) {
        return "assetInfo";
    }



}
