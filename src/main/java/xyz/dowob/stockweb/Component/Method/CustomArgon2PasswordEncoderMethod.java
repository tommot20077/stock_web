package xyz.dowob.stockweb.Component.Method;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 這是一個自定義Argon2PasswordEncoder方法，用於加密和比對密碼。
 *
 * @author yuan
 */
public class CustomArgon2PasswordEncoderMethod implements PasswordEncoder {
    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    /**
     * 加密操作
     *
     * @param rawPassword 原始密碼
     *
     * @return 加密後的密碼
     */
    @Override
    public String encode(CharSequence rawPassword) {
        char[] originalChars = rawPassword.toString().toCharArray();
        try {
            return argon2.hash(2, 1024 * 1024, 1, originalChars);
        } finally {
            argon2.wipeArray(originalChars);
        }
    }

    /**
     * 比對密碼
     *
     * @param rawPassword     原始密碼
     * @param encodedPassword 加密後的密碼
     *
     * @return 是否相符
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        char[] originalChars = rawPassword.toString().toCharArray();
        try {
            return argon2.verify(encodedPassword, originalChars);
        } finally {
            argon2.wipeArray(originalChars);
        }
    }
}
