package xyz.dowob.stockweb.Filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.User.TokenService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.io.IOException;
@Component
public class RememberMeAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private  UserService userService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private  CacheManager cacheManager;




    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        User user = null;
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("currentUserId") == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals("REMEMBER_ME")) {
                        Long userId = tokenService.verifyRememberMeToken(cookie.getValue());
                        if (userId != null) {
                            Cache userCache = cacheManager.getCache("user");
                            if (userCache != null) {
                                user = userCache.get(userId, User.class);
                                if (user == null) {
                                    user = userService.getUserById(userId);
                                    userCache.put(userId, user);
                                }
                            }
                            if (user != null) {
                                session = request.getSession(true);
                                Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
                                session.setAttribute("currentUserId", user.getId());
                            }
                        }
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
