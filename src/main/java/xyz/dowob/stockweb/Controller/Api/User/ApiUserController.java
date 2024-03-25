package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.User.LoginUserDto;
import xyz.dowob.stockweb.Dto.User.RegisterUserDto;
import xyz.dowob.stockweb.Model.User.User;
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
    @Autowired
    public ApiUserController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
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


}
