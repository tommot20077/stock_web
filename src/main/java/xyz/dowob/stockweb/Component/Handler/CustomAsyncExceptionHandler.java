package xyz.dowob.stockweb.Component.Handler;

import org.jetbrains.annotations.NotNull;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

/**
 * 這是一個用於處理異步任務異常的處理器，當異步任務出現異常時，將會調用此處理器
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName CustomAsyncExceptionHandler
 * @description
 * @create 2024-12-28 00:55
 * @Version 1.0
 **/
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    /**
     * 異常處理器，已配置全局AOP日誌處理，此處不做任何處理
     * {@link  xyz.dowob.stockweb.Component.Logger.LoggerAspect}
     *
     * @param ex     異常
     * @param method 方法
     * @param params 參數
     */
    @Override
    public void handleUncaughtException(@NotNull Throwable ex, @NotNull Method method, @NotNull Object... params) {}

}
