package xyz.dowob.stockweb.Service.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    @Autowired
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
            logger.error("登入失敗：郵件地址為空");
            throw new UsernameNotFoundException("郵件地址不能為空");
        }
        User user = userRepository.findByEmail(mail).orElse(null);
        logger.debug("找到用戶為 : " + user);

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
