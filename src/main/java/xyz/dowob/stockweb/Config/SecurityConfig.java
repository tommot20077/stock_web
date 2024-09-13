package xyz.dowob.stockweb.Config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import xyz.dowob.stockweb.Filter.JwtAuthenticationFilter;
import xyz.dowob.stockweb.Filter.RememberMeAuthenticationFilter;
import xyz.dowob.stockweb.Service.User.TokenService;

/**
 * @author yuan
 * Security設定
 * 1. 設定csrf
 * 2. 設定session
 * 3. 設定filter
 * 4. 設定formLogin
 * 5. 設定exceptionHandling
 * 6. 設定authorizeHttpRequests
 * 7. 設定logout
 * 8. 設定filterChain
 * 9. 設定rememberMeAuthenticationFilter
 * 10. 設定jwtAuthenticationFilter
 * 11. 設定filterChain
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final TokenService tokenService;

    public SecurityConfig(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * 設定filterChain, 設定csrf, session, filter, formLogin, exceptionHandling, authorizeHttpRequests, logout
     *
     * @param http HttpSecurity
     *
     * @return SecurityFilterChain
     *
     * @throws Exception Exception
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        http.csrf((csrf) -> csrf.csrfTokenRepository(csrfTokenRepository).ignoringRequestMatchers("/api/**", "/ws/**", "/sockjs/**"))
            .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                                   .sessionConcurrency((concurrency) -> concurrency.maximumSessions(1)
                                                                                                   .maxSessionsPreventsLogin(false)
                                                                                                   .expiredSessionStrategy(event -> event.getResponse()
                                                                                                                                         .sendRedirect(
                                                                                                                                                 "/login"))))
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rememberMeAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .formLogin((form) -> form.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/error").permitAll())
            .exceptionHandling((exception) -> exception.accessDeniedPage("/login"))
            .authorizeHttpRequests((authorize) -> authorize.requestMatchers("/api/user/common/**",
                                                                            "/login",
                                                                            "/login_p",
                                                                            "/register",
                                                                            "/reset_password",
                                                                            "/error")
                                                           .permitAll()
                                                           .requestMatchers("/static/**")
                                                           .permitAll()
                                                           .requestMatchers("/api/admin/**")
                                                           .hasRole("ADMIN")
                                                           .anyRequest()
                                                           .authenticated())
            .logout((logout) -> logout.addLogoutHandler((request, response, authentication) -> {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    Cookie[] cookies = request.getCookies();
                    tokenService.deleteRememberMeCookie(response, session, cookies);
                    session.invalidate();
                }
            }));
        return http.build();
    }

    /**
     * 設定rememberMeAuthenticationFilter
     *
     * @return RememberMeAuthenticationFilter
     */
    @Bean
    public RememberMeAuthenticationFilter rememberMeAuthenticationFilter() {
        return new RememberMeAuthenticationFilter();
    }

    /**
     * 設定jwtAuthenticationFilter
     *
     * @return JwtAuthenticationFilter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
