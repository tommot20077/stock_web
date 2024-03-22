package xyz.dowob.stockweb.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterUserDto {
    private String password;
    private String email;
    private String first_name;
    private String last_name;


}
