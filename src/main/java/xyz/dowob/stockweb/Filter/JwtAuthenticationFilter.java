package xyz.dowob.stockweb.Filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.dowob.stockweb.Component.Provider.JwtTokenProvider;
import xyz.dowob.stockweb.Service.User.CustomUserDetailsService;

import java.io.IOException;

/**
 * @author yuan
 * Jwt驗證過濾器繼承OncePerRequestFilter
 * 當請求到達時，檢查是否有JWT，如果有，則驗證JWT，並將用戶設置為已驗證
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /**
     * 注入Jwt令牌相關的方法
     */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 注入自定義用戶詳情服務
     */
    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    /**
     * 驗證JWT
     * 收到請求時，檢查是否有JWT，如果有，則驗證JWT，並將用戶設置為已驗證
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
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {

                Long userId = Long.parseLong(jwtTokenProvider.getClaimsFromJwt(jwt).getSubject());
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails,
                                                                                                             null,
                                                                                                             userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("無法使用你的令牌設置權限", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 從請求中獲取JWT
     *
     * @param request 請求
     *
     * @return JWT
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}