package xyz.dowob.stockweb.Exception;

import lombok.Getter;

/**
 * @author yuan
 * 重試異常, 用於重試機制保存上一次的異常
 * 繼承Exception, 用於拋出異常
 */
@Getter
public class RetryException extends Exception {
    private final Exception lastException;

    public RetryException(String message, Exception lastException) {
        super(message);
        this.lastException = lastException;
    }
}
