package xyz.dowob.stockweb.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import xyz.dowob.stockweb.Component.Method.CustomArgon2PasswordEncoderMethod;

/**
 * @author yuan
 * PasswordEncoder設定
 * 使用CustomArgon2PasswordEncoderMethod
 */
@Configuration
public class PasswordEncodeConfig {
    /**
     * 創建PasswordEncoder, 使用CustomArgon2PasswordEncoderMethod
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CustomArgon2PasswordEncoderMethod();
    }
}
