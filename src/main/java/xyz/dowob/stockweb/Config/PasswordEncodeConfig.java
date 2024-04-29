package xyz.dowob.stockweb.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import xyz.dowob.stockweb.Component.Method.CustomArgon2PasswordEncoderMethod;

/**
 * @author yuan
 */
@Configuration
public class PasswordEncodeConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CustomArgon2PasswordEncoderMethod();
    }
}
