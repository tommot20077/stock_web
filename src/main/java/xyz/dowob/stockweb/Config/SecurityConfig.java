package xyz.dowob.stockweb.Config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import xyz.dowob.stockweb.Service.UserService;
import xyz.dowob.stockweb.Filter.RememberMeAuthenticationFilter;


@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final UserService userService;
    @Autowired
    public SecurityConfig(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();

        http
                .csrf((csrf) -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                )


                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionConcurrency((concurrency) -> concurrency
                                .maximumSessions(1)
                                .maxSessionsPreventsLogin(false)
                                .expiredSessionStrategy(event -> event.getResponse().sendRedirect("/login"))
                        )
                ).addFilterBefore(rememberMeAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/error")
                        .permitAll()
                ).exceptionHandling((exception) -> exception
                        .accessDeniedPage("/login")
                ).authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers ("/api/login","/api/register","/login", "/register","/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "favicon", "/assets/**").permitAll()
                        .anyRequest().authenticated()
                ).logout((logout) -> logout
                        .addLogoutHandler((request, response, authentication) -> {
                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                Cookie[] cookies = request.getCookies();
                                userService.deleteRememberMeCookie(response, session, cookies);
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

}
