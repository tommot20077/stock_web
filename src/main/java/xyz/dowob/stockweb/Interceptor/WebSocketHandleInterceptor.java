package xyz.dowob.stockweb.Interceptor;

import jakarta.servlet.http.HttpSession;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import xyz.dowob.stockweb.Repository.User.UserRepository;

import java.util.Map;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName WebSocketInterceptor
 * @description
 * @create 2024-08-24 12:03
 * @Version 1.0
 **/
@Log4j2
@Component
public class WebSocketHandleInterceptor extends HttpSessionHandshakeInterceptor {
    @Autowired
    private UserRepository userRepository;


    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) throws Exception {
        log.debug("攔截器前置觸發");

        HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession();
        userRepository.findById((Long) session.getAttribute("currentUserId")).ifPresent(user -> attributes.put("user", user));
        log.debug("當前請求用戶Id: " + session.getAttribute("currentUserId"));

        return super.beforeHandshake(request, response, wsHandler, attributes);
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception ex) {
        log.debug("攔截器後置觸發");
        super.afterHandshake(request, response, wsHandler, ex);
    }

}
