package xyz.dowob.stockweb.Dto.User;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yuan
 * 用於傳遞用戶註冊的資料
 * 1. password: 密碼
 * 2. email: 電子郵件
 * 3. firstName: 名
 * 4. lastName: 姓
 */
@Data
@AllArgsConstructor
public class RegisterUserDto {
    private String password;

    private String email;

    private String firstName;

    private String lastName;
}
