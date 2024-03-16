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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.dowob.stockweb.Dto.LoginUserDto;
import xyz.dowob.stockweb.Dto.RegisterUserDto;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Service.UserService;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Controller
public class UserController {

    private final UserService userService;
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
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
            SecurityContextHolder.getContext().setAuthentication(auth);;
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




    @GetMapping("/")
    public String index (HttpSession session, Model model) {
        if (session.getAttribute("currentUserId") != null) {
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            model.addAttribute(user);
        }
        return "index";
    }

    @GetMapping("/index")
    public String index2 (HttpSession session, Model model) {
        if (session.getAttribute("currentUserId") != null) {
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            model.addAttribute(user);
        }
        return "index";
    }

    @GetMapping("/profile")
    public String profile (HttpSession session, Model model) {
        if (session.getAttribute("currentUserId")!= null) {
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            model.addAttribute(user);
        }
        return "profile";
    }


    @GetMapping("/logout")
    public void logout(HttpSession session, HttpServletRequest request, HttpServletResponse response, @RequestParam(required = false, name = "redirection", defaultValue = "true") boolean redirection) {
        Cookie[] cookies = request.getCookies();
        userService.deleteRememberMeCookie(response, session, cookies);
        session.invalidate();
        if (redirection) {
            response.setHeader("Location", "/login");
            response.setStatus(302);
        }
    }


}
