package xyz.dowob.stockweb.Component.Method.retry;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Exception.RetryException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 這是一個重試方法，用於控制重試次數。
 * 這個類的目的是為了在重試次數達到最大值時拋出RetryException異常。
 *
 * @author yuan
 */
@Log4j2
@Component
public class RetryMethod {
    @Value("${common.max_retryTimes:3}")
    private int maxRetryTimesValue;

    private AtomicInteger maxRetryTimes;

    /**
     * 初始化方法，用於設置最大重試次數。
     */
    @PostConstruct
    private void init() {
        try {
            maxRetryTimes = new AtomicInteger(maxRetryTimesValue);
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }

    public RetryContent getRetryContent() {
        return new RetryContent(maxRetryTimes.get());
    }

    /**
     * 這是一個重試內容，用於控制重試次數。
     */
    public class RetryContent {
        private final AtomicInteger currentRetryTimes;

        private final int maxRetryTimes;

        /**
         * 創建一個重試內容。
         *
         * @param maxRetryTimes 最大重試次數
         */
        public RetryContent(int maxRetryTimes) {
            this.currentRetryTimes = new AtomicInteger(0);
            this.maxRetryTimes = maxRetryTimes;
        }

        /**
         * 檢查並重試。
         *
         * @param lastException 最後一次錯誤
         *
         * @return 是否重試
         *
         * @throws RetryException 如果已達到最大重試次數
         */
        public boolean checkAndRetry(Exception lastException) throws RetryException {
            if (currentRetryTimes.get() < maxRetryTimes) {
                currentRetryTimes.getAndIncrement();
                return true;
            }
            throw new RetryException("已達到最大重試次數，最後一次錯誤信息：" + lastException.getMessage(), lastException);
        }
    }
}
