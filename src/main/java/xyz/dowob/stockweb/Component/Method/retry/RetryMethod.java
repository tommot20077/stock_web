package xyz.dowob.stockweb.Component.Method.retry;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Exception.RetryException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yuan
 */
@Component
public class RetryMethod {
    @Value("${common.max_retryTimes}")
    private int maxRetryTimesValue;
    private AtomicInteger maxRetryTimes;

    @PostConstruct
    private void init() {
        maxRetryTimes = new AtomicInteger(maxRetryTimesValue);
    }


    public RetryContent getRetryContent() {
        return new RetryContent(maxRetryTimes.get());
    }

    public class RetryContent {
        private final AtomicInteger currentRetryTimes;
        private final int maxRetryTimes;

        /**
         * 創建一個重試內容。
         * @param maxRetryTimes 最大重試次數
         */
        public RetryContent(int maxRetryTimes) {
            this.currentRetryTimes = new AtomicInteger(0);
            this.maxRetryTimes = maxRetryTimes;
        }

        /**
         * 檢查並重試。
         * @param lastException 最後一次錯誤
         * @return 是否重試
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
