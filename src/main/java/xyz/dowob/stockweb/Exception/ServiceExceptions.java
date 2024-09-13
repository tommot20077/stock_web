package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName ServiceExceptions
 * @description
 * @create 2024-09-12 14:33
 * @Version 1.0
 **/
public class ServiceExceptions extends CustomAbstractExceptions {
    public ServiceExceptions(ServiceExceptions.ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        CLEAR_EXPIRED_TOKEN_ERROR("清除過期Token錯誤: %s"),
        TREAD_POOL_CLOSE_ERROR("關閉線程池錯誤: %s"),
        NEWS_REQUEST_ERROR("新聞請求錯誤: %s"),
        TRACK_CRYPTO_HISTORY_ERROR("追蹤加密貨幣歷史價格錯誤: %s"),
        WEBSOCKET_SEND_MESSAGE_ERROR("WebSocket發送錯誤: %s");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
