package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName SubscriptionExceptions
 * @description
 * @create 2024-09-12 15:29
 * @Version 1.0
 **/
public class SubscriptionExceptions extends CustomAbstractExceptions {
    public SubscriptionExceptions(SubscriptionExceptions.ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        SUBSCRIPTION_ALREADY_EXIST("已經訂閱過此資產: %s"),
        SUBSCRIPTION_NOT_EXIST("沒有訂閱過此資產: %s"),
        SUBSCRIPTION_NOT_FOUND("沒有找到指定的訂閱"),
        SUBSCRIPTION_CANNOT_UNSUBSCRIBE("此訂閱: %s 為用戶: %s 現在所持有的資產，不可刪除訂閱"),
        SUBSCRIPTION_SAME_ASSET("訂閱兌不可相同: %s");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
