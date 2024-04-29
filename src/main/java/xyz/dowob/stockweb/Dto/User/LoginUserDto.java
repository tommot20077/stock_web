package xyz.dowob.stockweb.Dto.User;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yuan
 */
@Data
@AllArgsConstructor
public class LoginUserDto {
    private String email;
    private String password;
    private boolean rememberMe;
}
