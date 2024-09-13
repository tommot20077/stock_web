package xyz.dowob.stockweb.Exception;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName AssetExceptions
 * @description
 * @create 2024-09-12 03:35
 * @Version 1.0
 **/
public class AssetExceptions extends CustomAbstractExceptions {
    public AssetExceptions(AssetExceptions.ErrorEnum errorEnum, Object... args) {
        super(String.format(errorEnum.getMessage(), args));
    }

    public enum ErrorEnum {
        DEFAULT_CURRENCY_NOT_FOUND("找不到默認資產: %s"),
        ASSET_NOT_FOUND("找不到資產: %s"),
        ASSET_TYPE_NOT_FOUND("找不到資產類型: %s"),
        OPERATION_INVALID("操作無效: %s"),
        CURRENCY_DATA_UPDATING("貨幣資料更新中"),
        STOCK_NOT_FOUND("沒有找到指定的股票 %s"),
        STOCK_DATA_RESOLVE_ERROR("解析股票資料錯誤"),
        STOCK_DATA_UPDATE_ERROR("更新股票列表失敗"),
        STOCK_DATA_EMPTY("股票: %s 沒有資料"),
        DELETE_ASSET_DATA_ERROR("刪除資產: %s 資料失敗: %s"),
        CURRENCY_DATA_UPDATE_ERROR("更新貨幣列表失敗"),
        CURRENCY_CONVERT_ERROR("貨幣轉換失敗，原始貨幣: %s, 目標貨幣: %s"),
        DEBT_DATA_UPDATE_ERROR("更新債券列表失敗: %s"),
        DEBT_DATA_RESOLVE_ERROR("解析債券資料錯誤: %s"),
        CRYPTO_NOT_FOUND("找不到加密貨幣: %s"),
        TRACK_ASSET_DATA_FAILED("追蹤資產資料錯誤: %s"),
        ASSET_IMMEDIATE_PRICE_NOT_FOUND("找不到資產即時價格: %s");

        private final String errorMessage;

        ErrorEnum(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getMessage() {
            return errorMessage;
        }
    }
}
