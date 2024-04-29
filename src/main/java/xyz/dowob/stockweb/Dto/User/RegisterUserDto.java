package xyz.dowob.stockweb.Dto.User;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yuan
 */
@Data
@AllArgsConstructor
public class RegisterUserDto {
    private String password;
    private String email;
    private String firstName;
    private String lastName;
}
