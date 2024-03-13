package xyz.dowob.stockweb.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import xyz.dowob.stockweb.Model.User;

@Data
@AllArgsConstructor
public class RegisterUserDto {
    private String password;
    private String email;
    private String first_name;
    private String last_name;



    public static void registerUserDtoToUser(User user, RegisterUserDto userDto) {
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirst_name());
        user.setLastName(userDto.getLast_name());

    }

}
