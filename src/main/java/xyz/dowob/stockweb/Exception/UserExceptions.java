package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName UserExceptions
 * @description
 * @create 2024-09-12 03:09
 * @Version 1.0
 **/
public class UserExceptions extends CustomAbstractExceptions {
    public UserExceptions(ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        PASSWORD_MUST_CONTAIN_DIGIT("密碼必須包含數字"),
        PASSWORD_MUST_CONTAIN_LOWERCASE_LETTER("密碼必須包含小寫字母"),
        PASSWORD_MUST_CONTAIN_UPPERCASE_LETTER("密碼必須包含大寫字母"),
        PASSWORD_MAX_LENGTH_LIMIT("密碼最多100個字元"),
        PASSWORD_MIN_LENGTH_LIMIT("密碼最少8個字元"),
        EMAIL_ALREADY_EXISTS("電子郵件已經存在"),
        LOGIN_DATA_NOT_FOUND("找不到登錄數據"),
        USERNAME_OR_PASSWORD_WRONG("用戶名或密碼錯誤"),
        USER_ALREADY_VERIFIED("用戶已經驗證信箱"),
        USER_TOKEN_INVALID("用戶Token無效"),
        USER_NOT_FOUND("找不到用戶 %s"),
        INVALID_EMAIL_CANNOT_USE("未驗證信箱無法使用無法使用功能: %s"),
        USER_SEND_EMAIL_USAGE_LIMIT_EXCEEDED("目前已到達每小時發送電子郵件的限制，請稍後再試。");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
