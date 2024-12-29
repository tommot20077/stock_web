package xyz.dowob.stockweb.Interceptor;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Http攔截器，預先處理Http請求，並將不合法的請求進行過濾
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName HttpMethodFilter
 * @description
 * @create 2024-12-24 21:49
 * @Version 1.0
 **/
@Component
public class HttpMethodFilter implements Filter {
    /**
     * 預先處理Http請求，將不合法的請求進行過濾
     *
     * @param request  Http請求
     * @param response Http響應
     * @param chain    過濾器鏈
     *
     * @throws IOException      IO異常
     * @throws ServletException Servlet異常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String method = httpRequest.getMethod();
            if (!isValidMethodName(method)) {
                response.setContentType("text/plain");
                response.getWriter().write("Invalid HTTP method: " + method);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 判斷Http方法名是否合法
     *
     * @param method Http方法名
     *
     * @return 是否合法
     */
    private boolean isValidMethodName(String method) {
        return method != null && method.matches("^[A-Z]+$");
    }

}
