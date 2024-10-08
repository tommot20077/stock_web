package xyz.dowob.stockweb.Service.User;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.User.UserRepository;

/**
 * @author yuan
 * 自定義UserDetailsService
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    /**
     * CustomUserDetailsService構造函數
     *
     * @param userRepository 用戶數據庫
     */
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 透過ID取得用戶
     *
     * @param userId 用戶ID
     *
     * @return UserDetails
     *
     * @throws UsernameNotFoundException 找不到用戶
     */
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("找不到用戶為ID : " + userId));
        return new org.springframework.security.core.userdetails.User(user.getEmail(),
                                                                      user.getPassword(),
                                                                      true,
                                                                      true,
                                                                      true,
                                                                      true,
                                                                      user.getAuthorities());
    }

    /**
     * 透過郵件地址取得用戶
     *
     * @param mail 郵件地址
     *
     * @return UserDetails
     *
     * @throws UsernameNotFoundException 找不到用戶
     */
    @Override
    public UserDetails loadUserByUsername(String mail) throws UsernameNotFoundException {
        if (!StringUtils.hasText(mail)) {
            throw new UsernameNotFoundException("郵件地址不能為空");
        }
        User user = userRepository.findByEmail(mail).orElse(null);
        if (user == null) {
            throw new RuntimeException("找不到用戶mail為 : " + mail);
        } else {
            return new org.springframework.security.core.userdetails.User(user.getEmail(),
                                                                          user.getPassword(),
                                                                          true,
                                                                          true,
                                                                          true,
                                                                          true,
                                                                          user.getAuthorities());
        }
    }
}
