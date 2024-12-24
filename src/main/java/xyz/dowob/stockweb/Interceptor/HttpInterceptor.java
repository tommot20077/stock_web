package xyz.dowob.stockweb.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Http攔截器，預先處理Http請求，並將不合法的請求進行過濾
 * @author yuan
 * @program Stock-Web
 * @ClassName HttpInterceptor
 * @description
 * @create 2024-12-24 21:49
 * @Version 1.0
 **/
@Component
public class HttpInterceptor implements HandlerInterceptor {
    /**
     * 預先處理Http請求，將不合法的請求進行過濾
     * @param request Http請求
     * @param response Http響應
     * @param handler 請求處理器
     * @return 是否繼續執行後續操作
     */
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String method = request.getMethod();
        if (!isValidMethodName(method)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return false;
        }
        return true;
    }

    /**
     * 判斷Http方法名是否合法
     * @param method Http方法名
     * @return 是否合法
     */
    private boolean isValidMethodName(String method) {
        return method != null && method.matches("^[A-Z]+$");
    }

}
