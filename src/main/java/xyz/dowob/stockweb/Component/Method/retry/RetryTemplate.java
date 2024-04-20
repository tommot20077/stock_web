package xyz.dowob.stockweb.Component.Method.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Exception.RetryException;


@Component
public class RetryTemplate {
    private final RetryMethod retryMethod;
    Logger logger = LoggerFactory.getLogger(RetryTemplate.class);

    public RetryTemplate(RetryMethod retryMethod) {
        this.retryMethod = retryMethod;
    }

    @FunctionalInterface
    public interface RetryableOperation {
        void execute() throws Exception;
    }

    public void doWithRetry(RetryableOperation operation) throws RetryException {
        RetryMethod.RetryContent retryContent = retryMethod.getRetryContent();
        Exception lastException = null;
        while (retryContent.checkAndRetry(lastException)) {
            try {
                operation.execute();
                break;
            } catch (Exception e) {
                logger.error("操作失敗, 正在重試...");
                lastException = e;
            }
        }
    }
}
