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

/**
 * @author yuan
 * 記住我驗證過濾器繼承OncePerRequestFilter
 * 當請求到達時，檢查是否有記住我Cookie，如果有，則驗證Cookie，並將用戶設置為已驗證
 */
@Component
public class RememberMeAuthenticationFilter extends OncePerRequestFilter {
    /**
     * 注入用戶服務
     */
    @Autowired
    private UserService userService;

    /**
     * 注入用戶令牌服務
     */
    @Autowired
    private TokenService tokenService;

    /**
     * 注入緩存管理器
     */
    @Autowired
    private CacheManager cacheManager;

    /**
     * 驗證Cookie
     * 收到請求時，檢查是否有記住我Cookie，如果有，則驗證Cookie，並將用戶設置為已驗證
     *
     * @param request     請求
     * @param response    響應
     * @param filterChain 過濾器鏈
     *
     * @throws ServletException 錯誤
     * @throws IOException      錯誤
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        User user = null;
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("currentUserId") == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("REMEMBER_ME".equals(cookie.getName())) {
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
                                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                                     SecurityContextHolder.getContext());
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
