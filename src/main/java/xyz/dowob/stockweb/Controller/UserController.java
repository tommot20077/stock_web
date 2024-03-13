package xyz.dowob.stockweb.Controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Service.UserService;



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
    @GetMapping("/register")
    public String register() {
        return "register";
    }
    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        if (session.getAttribute("currentUserId") != null) {
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            model.addAttribute(user);
        }
        return "index";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        userService.deleteRememberMeCookie(response, session, cookies);
        session.invalidate();
        return "redirect:/login";
    }

}
