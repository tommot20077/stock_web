package xyz.dowob.stockweb.Interceptor;

import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import xyz.dowob.stockweb.Repository.User.UserRepository;

import java.util.Map;

/**
 * 這是一個用於處理WebSocket請求的攔截器
 * 這個攔截器會在WebSocket請求到達時觸發，並對請求進行預處理
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName WebSocketInterceptor
 * @description
 * @create 2024-08-24 12:03
 * @Version 1.0
 **/
public class WebSocketHandleInterceptor extends HttpSessionHandshakeInterceptor {
    @Autowired
    private UserRepository userRepository;

    /**
     * 在連線握手之前呼叫，攔截器檢查請求，並選擇是否繼續。
     *
     * @param request    當前的請求
     * @param response   當前的回應
     * @param wsHandler  要使用的WebSocket處理程序
     * @param attributes 用於WebSocket會話的屬性映射。可以在這裡添加屬性，以便在WebSocket會話中使用。提供的屬性是複製的，不使用原始映射。
     *
     * @return 如果應該繼續握手，則為true；否則為false。如果為false，則不會調用afterHandshake方法。
     *
     * @throws Exception 如果握手應該被拒絕，則拋出異常
     */
    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) throws Exception {
        HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession();
        userRepository.findById((Long) session.getAttribute("currentUserId")).ifPresent(user -> attributes.put("user", user));
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }

    /**
     * 在連線握手之後呼叫，攔截器可以在這裡進行任何後處理。
     *
     * @param request   當前的請求
     * @param response  當前的回應
     * @param wsHandler 要使用的WebSocket處理程序
     * @param ex        任何異常，如果有的話
     */
    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception ex) {
        super.afterHandshake(request, response, wsHandler, ex);
    }
}
