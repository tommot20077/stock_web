package xyz.dowob.stockweb.Dto.User;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginUserDto {
    private String email;
    private String password;
    private boolean remember_me;
}
