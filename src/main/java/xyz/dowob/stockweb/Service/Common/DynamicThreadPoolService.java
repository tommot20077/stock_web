package xyz.dowob.stockweb.Service.Common;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yuan
 * 管理動態線程池的服務。
 */
@Log4j2
@Service
public class DynamicThreadPoolService {
    @Value("${common.global_thread_limit:6}")
    private int globalThreadLimit;

    private ThreadPoolExecutor executorService;

    @Getter
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    /**
     * 初始化線程池,並受限於globalThreadLimit。
     */
    @PostConstruct
    public void init() {
        try {
            this.executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(globalThreadLimit);
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }

    /**
     * 獲取線程池。
     *
     * @return 線程池。
     */
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
        } else if (activeTaskCount < corePoolSize) {
            this.executorService.setCorePoolSize(Math.max(corePoolSize - 1, 1));
        }
    }

    /**
     * 當任務開始時，增加活動任務數量。
     */
    public void onTaskStart() {
        activeTasks.incrementAndGet();
        adjustThreadPoolBasedOnLoad();
    }

    /**
     * 當任務完成時，減少活動任務數量。
     */
    public void onTaskComplete() {
        activeTasks.decrementAndGet();
        adjustThreadPoolBasedOnLoad();
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
                executorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
