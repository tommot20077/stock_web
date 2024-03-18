package xyz.dowob.stockweb.Controller.Api;

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
import xyz.dowob.stockweb.Dto.LoginUserDto;
import xyz.dowob.stockweb.Dto.RegisterUserDto;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Service.TokenService;
import xyz.dowob.stockweb.Service.UserService;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequestMapping("/api/common")
@RestController
public class ApiCommonController {
    /*
    private final UserService userService;
    private final TokenService tokenService;
    @Autowired
    public ApiCommonController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

     */

    @GetMapping("/common/getTimeZoneList")
    public ResponseEntity<?> getTimeZoneList() {

        List<String> timezones = ZoneId.getAvailableZoneIds()
                .stream()
                .sorted()
                .collect(Collectors.toList());

        return ResponseEntity.ok().body(timezones);
    }

}
