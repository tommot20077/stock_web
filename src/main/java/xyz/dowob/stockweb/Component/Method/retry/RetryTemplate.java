package xyz.dowob.stockweb.Component.Method.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Exception.RetryException;

/**
 * 這是一個重試模板，用於執行重試操作。
 *
 * @author yuan
 */
@Component
public class RetryTemplate {
    private final RetryMethod retryMethod;

    Logger logger = LoggerFactory.getLogger(RetryTemplate.class);

    public RetryTemplate(RetryMethod retryMethod) {
        this.retryMethod = retryMethod;
    }

    /**
     * 定義RetryableOperation的函數式接口，該接口有一個名為execute的方法
     * FunctionalInterface  這是一個函數式接口，它只有一個抽象方法
     */
    @FunctionalInterface
    public interface RetryableOperation {
        void execute() throws Exception;
    }

    /**
     * doWithRetry方法接受一個RetryableOperation作為參數，並使用RetryMethod.RetryContent來控制重試的邏輯。
     * 在每次重試中，它會嘗試執行operation.execute()。如果execute方法拋出異常，則會捕獲該異常，記錄一條錯誤日誌，並在下一次迴圈中再次嘗試。
     * 如果execute方法成功執行（即沒有拋出異常），則會跳出迴圈。如果達到最大重試次數，checkAndRetry方法將拋出RetryException異常。
     *
     * @param operation 一個RetryableOperation對象
     *
     * @throws RetryException 如果達到最大重試次數
     */
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
