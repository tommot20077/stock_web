package xyz.dowob.stockweb.Service.Common;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yuan
 * 管理動態線程池的服務。
 */
@Service
public class DynamicThreadPoolService {
    @Value("${common.global_thread_limit:6}")
    private int globalThreadLimit;

    private ThreadPoolExecutor executorService;

    @Getter
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    Logger logger = LoggerFactory.getLogger(DynamicThreadPoolService.class);


    /**
     * 初始化線程池,並受限於globalThreadLimit。
     */
    @PostConstruct
    public void init() {
        this.executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(globalThreadLimit);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * 根據活動任務數量調整線程池大小。
     * 如果活動任務數量等於核心線程數，並且小於全局線程限制，則增加一個核心線程。
     * 利用synchronized關鍵字保證線程安全。
     */
    public synchronized void adjustThreadPoolBasedOnLoad() {
        int activeTaskCount = this.executorService.getActiveCount();
        int corePoolSize = this.executorService.getCorePoolSize();
        if (activeTaskCount == corePoolSize && activeTaskCount < globalThreadLimit) {
            this.executorService.setCorePoolSize(corePoolSize + 1);
            logger.debug("線程池增加一個線程");
        } else if (activeTaskCount < corePoolSize) {
            this.executorService.setCorePoolSize(Math.max(corePoolSize - 1, 1));
            logger.debug("線程池減少一個線程");
        }
        logger.debug("線程池現在有" + activeTaskCount + "個線程");
    }

    /**
     * 當任務開始時，增加活動任務數量。
     */
    public void onTaskStart() {
        activeTasks.incrementAndGet();
        adjustThreadPoolBasedOnLoad();
        logger.debug("現在有" + activeTasks + "個任務");
    }

    /**
     * 當任務完成時，減少活動任務數量。
     */
    public void onTaskComplete() {
        activeTasks.decrementAndGet();
        adjustThreadPoolBasedOnLoad();
        logger.debug("現在有" + activeTasks + "個任務");
    }

    /**
     * 獲取當前核心線程數。
     *
     * @return 當前核心線程數。
     */
    public int getCurrentCorePoolSize() {
        return executorService.getCorePoolSize();
    }

    /**
     * 關閉線程池。
     *
     * @param timeout 超時時間。
     * @param unit    時間單位。
     */
    public void shutdown(long timeout, TimeUnit unit) {

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(timeout, unit)) {
                List<Runnable> tasks = executorService.shutdownNow();
                logger.warn("任務關閉超過預定時間，強制關閉");
                logger.info("剩餘任務: " + tasks.size());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
