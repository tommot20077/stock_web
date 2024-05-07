package xyz.dowob.stockweb.Dto.User;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yuan
 * 用於傳遞用戶登入的資料
 * 1. email: 電子郵件
 * 2. password: 密碼
 * 3. rememberMe: 是否記住我
 */
@Data
@AllArgsConstructor
public class LoginUserDto {
    private String email;

    private String password;

    private boolean rememberMe;
}
