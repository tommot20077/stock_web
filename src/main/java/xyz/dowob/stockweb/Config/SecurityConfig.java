package xyz.dowob.stockweb.Config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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
import xyz.dowob.stockweb.Service.User.UserService;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenService tokenService;

    @Autowired
    public SecurityConfig(TokenService tokenService) {


        this.tokenService = tokenService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();

        http

                .csrf((csrf) -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/**")
                )

                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionConcurrency((concurrency) -> concurrency
                                .maximumSessions(1)
                                .maxSessionsPreventsLogin(false)
                                .expiredSessionStrategy(event -> event.getResponse().sendRedirect("/login"))
                        )
                ).addFilterBefore(rememberMeAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class
                ).addFilterAfter(jwtAuthenticationFilter(), RememberMeAuthenticationFilter.class)

                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/error")
                        .permitAll()
                ).exceptionHandling((exception) -> exception
                        .accessDeniedPage("/login")
                ).authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers ("/api/user/common/login","/api/user/common/register","/api/user/common/verifyEmail","/login","/login_p", "/register","/error").permitAll()
                        .requestMatchers("/static/**").permitAll()
                        .requestMatchers("/api/admin/updateCurrencyData").hasRole("ADMIN")
                        .anyRequest().authenticated()
                ).logout((logout) -> logout
                        .addLogoutHandler((request, response, authentication) -> {
                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                Cookie[] cookies = request.getCookies();
                                tokenService.deleteRememberMeCookie(response, session, cookies);
                                session.invalidate();
                            }
                        })
                );

        return http.build();
    }

    @Bean
    public RememberMeAuthenticationFilter rememberMeAuthenticationFilter() {
        return new RememberMeAuthenticationFilter();
    }
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }


}

