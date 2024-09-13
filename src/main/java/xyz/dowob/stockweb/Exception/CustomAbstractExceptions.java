package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName CustomAbstractExceptions
 * @description
 * @create 2024-09-12 22:15
 * @Version 1.0
 **/
public abstract class CustomAbstractExceptions extends RuntimeException {
    public CustomAbstractExceptions(String message, Object... args) {
        super(String.format(message, args));
    }

    public enum ErrorEnum {}
}
