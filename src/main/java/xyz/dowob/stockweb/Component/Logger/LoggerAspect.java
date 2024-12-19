package xyz.dowob.stockweb.Component.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.dowob.stockweb.Component.Annotation.MeaninglessData;
import xyz.dowob.stockweb.Component.Annotation.SensitiveData;

import java.lang.reflect.Method;

/**
 * 日誌的切面方法: 用於記錄Service層的方法執行情況，當方法執行成功時，記錄debug信息，當方法執行失敗時，記錄error信息
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName
 * @description
 * @create 2024-09-12 02:28
 * @Version 1.0
 **/
@Aspect
@Component
@Log4j2
@RequiredArgsConstructor
public class LoggerAspect {
    private final HttpServletRequest request;

    @Pointcut("within(xyz.dowob.stockweb.Service..*)")
    public void serviceLayerPointcut() {}

    @Pointcut("within(xyz.dowob.stockweb.Component..*)")
    public void componentLayerPointcut() {}

    @Around("serviceLayerPointcut() || componentLayerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String userMail = "No Session";
        if (attributes != null) {
            HttpSession session = attributes.getRequest().getSession(false);
            if (session != null) {
                userMail = (String) session.getAttribute("userMail");
            }
        }
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Method method = joinPoint.getTarget()
                                 .getClass()
                                 .getMethod(methodName, ((MethodSignature) joinPoint.getSignature()).getParameterTypes());
        try {
            Object result = joinPoint.proceed();
            String value = methodSignature(method, result, joinPoint);
            log.debug("請求者: {}, Service: {} 方法: {} 執行成功，返回值: {}", userMail, className, methodName, value);
            return result;
        } catch (Throwable throwable) {
            log.error("請求者: {}, Service: {} 方法: {} 發生錯誤: {}", userMail, className, methodName, throwable);
            throw throwable;
        }
    }

    private String methodSignature(Method method, Object result, ProceedingJoinPoint joinPoint) {
        boolean isSensitive = method.isAnnotationPresent(SensitiveData.class);
        boolean isMeaningless = method.isAnnotationPresent(MeaninglessData.class) || joinPoint.getTarget()
                                                                                              .getClass()
                                                                                              .isAnnotationPresent(MeaninglessData.class);
        if (isSensitive) {
            return "[HIDDEN]";
        }
        if (isMeaningless) {
            return "忽略";
        }
        if (result == null) {
            return "無返回值";
        } else {
            return result.toString();
        }
    }
}
