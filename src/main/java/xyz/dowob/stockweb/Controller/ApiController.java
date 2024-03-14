package xyz.dowob.stockweb.Controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.LoginUserDto;
import xyz.dowob.stockweb.Dto.RegisterUserDto;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Service.UserService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequestMapping("/api")
@RestController
public class ApiController {
    private final UserService userService;
    @Autowired
    public ApiController(UserService userService) {
        this.userService = userService;
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
            SecurityContextHolder.getContext().setAuthentication(auth);;
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            session.setAttribute("currentUserId", user.getId());

            String jwt = userService.generateJwtToken(user, response);
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
            User user = userService.getUserById((Long) session.getAttribute("currentUserId"));
            return ResponseEntity.ok().body(user);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
