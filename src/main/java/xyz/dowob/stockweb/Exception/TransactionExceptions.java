package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName TransactionExcpetions
 * @description
 * @create 2024-09-12 13:01
 * @Version 1.0
 **/
public class TransactionExceptions extends CustomAbstractExceptions {
    public TransactionExceptions(TransactionExceptions.ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        USER_HAS_NOT_ENOUGH_ASSET("用戶資產中沒有足夠的 %s 來完成交易"),
        TRANSACTION_TYPE_NOT_FOUND("找不到交易類型");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
