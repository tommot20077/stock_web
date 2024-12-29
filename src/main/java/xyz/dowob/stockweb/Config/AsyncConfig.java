package xyz.dowob.stockweb.Config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import xyz.dowob.stockweb.Component.Handler.CustomAsyncExceptionHandler;

import java.util.concurrent.Executor;

/**
 * 這是一個用於配置異步任務的配置類，配置異步任務的執行緒池
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName AsyncConfig
 * @description
 * @create 2024-12-28 00:58
 * @Version 1.0
 **/
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 異步任務的核心執行緒數量，當配置文件中未配置時，默認為CPU核心數量
     */
    @Value("${async.corePoolSize:#{null}}")
    private Integer corePoolSize;

    /**
     * 異步任務的最大執行緒數量，當配置文件中未配置時，默認為CPU核心數量的兩倍
     */
    @Value("${async.maxPoolSize:#{null}}")
    private Integer maxPoolSize;

    /**
     * 異步任務的等待隊列容量，默認為25
     */
    @Value("${async.queueCapacity:25}")
    private Integer queueCapacity;

    /**
     * 配置異步任務的執行緒池，並返回執行緒池
     *
     * @return 執行緒池
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(corePoolSize != null ? corePoolSize : availableProcessors);
        executor.setMaxPoolSize(maxPoolSize != null ? maxPoolSize : availableProcessors * 2);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }

    /**
     * 配置異步任務異常處理器 {@link CustomAsyncExceptionHandler}
     *
     * @return 異常處理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }
}
