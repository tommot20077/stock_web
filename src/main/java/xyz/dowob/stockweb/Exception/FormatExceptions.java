package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName FormatExceptions
 * @description
 * @create 2024-09-12 03:40
 * @Version 1.0
 **/
public class FormatExceptions extends CustomAbstractExceptions {
    public FormatExceptions(FormatExceptions.ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        JSON_FORMAT_ERROR("轉換JSON失敗: %s"),
        ASSET_FORMAT_ERROR("轉換資產失敗: %s"),
        ASSET_LIST_FORMAT_ERROR("轉換資產列表失敗: %s"),
        ASSET_TRIE_FORMAT_ERROR("轉換資產列表字典樹失敗: %s"),
        UNSUPPORTED_DATE_FORMAT("不支持的日期格式: %s"),
        KLINE_FORMAT_ERROR("轉換K線失敗: %s");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
