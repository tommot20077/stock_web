package xyz.dowob.stockweb.Component.Method;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.password.PasswordEncoder;

public class CustomArgon2PasswordEncoderMethod implements PasswordEncoder {
    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    @Override
    public String encode(CharSequence rawPassword) {
        char[] originalChars = rawPassword.toString().toCharArray();
        try {
            return argon2.hash(2, 1024 * 1024, 1, originalChars);
        } finally {
            argon2.wipeArray(originalChars);
        }
    }
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
