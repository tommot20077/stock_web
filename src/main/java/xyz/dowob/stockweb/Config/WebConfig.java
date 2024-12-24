package xyz.dowob.stockweb.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.dowob.stockweb.Interceptor.HttpInterceptor;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName WebConfig
 * @description
 * @create 2024-12-24 22:05
 * @Version 1.0
 **/
@RequiredArgsConstructor
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final HttpInterceptor httpInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpInterceptor).addPathPatterns("/**");
    }

}
