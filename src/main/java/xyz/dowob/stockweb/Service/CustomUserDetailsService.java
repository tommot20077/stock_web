package xyz.dowob.stockweb.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.dowob.stockweb.Model.User;
import xyz.dowob.stockweb.Repository.UserRepository;

import java.util.Collection;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;
    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException("找不到用戶為ID : " + userId)
                );

        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(),
                true, true, true, true, user.getAuthorities());
    }

    @Override
    public UserDetails loadUserByUsername(String mail) throws UsernameNotFoundException {
        if (!StringUtils.hasText(mail)) {
            logger.error("登入失敗：郵件地址為空");
            throw new UsernameNotFoundException("郵件地址不能為空");
        }
        User user = userRepository.findByEmail(mail).orElse(null);

        if (user == null ) {
            throw new RuntimeException("找不到用戶mail為 : " + mail);
        } else {
            System.out.println("userId: "+ user.getId());
            return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(),
                    true, true, true, true, user.getAuthorities());
        }
    }
}
