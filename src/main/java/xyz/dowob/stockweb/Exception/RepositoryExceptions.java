package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName RepositoryExceptions
 * @description
 * @create 2024-09-12 16:26
 * @Version 1.0
 **/
public class RepositoryExceptions extends CustomAbstractExceptions {
    public RepositoryExceptions(RepositoryExceptions.ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        INFLUXDB_WRITE_ERROR("InfluxDB寫入錯誤: %s"),
        REDIS_WRITE_ERROR("Redis寫入錯誤: %s");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
