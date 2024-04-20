package xyz.dowob.stockweb.Exception;

import lombok.Getter;

public class RetryException extends Exception {
    @Getter
    private Exception lastException;
    public RetryException(String message, Exception lastException) {
        super(message);
        this.lastException = lastException;
    }

}
