package xyz.dowob.stockweb.Component.Event.Asset;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Common.Asset;

/**
 * 當資產歷史數據的獲取完成時發布的事件。
 * 此事件用於通知AssetHistoryDataFetchCompleteListener進行後續操作。
 *
 * @author yuan
 */
@Getter
public class AssetHistoryDataFetchCompleteEvent extends ApplicationEvent {
    private final Boolean success;

    private final Asset asset;

    /**
     * AssetHistoryDataFetchCompleteEvent的構造函數。
     *
     * @param source  事件發生的對象。
     * @param success 數據獲取操作是否成功。
     * @param asset   進行歷史數據獲取操作的資產。
     */
    public AssetHistoryDataFetchCompleteEvent(@NonNull Object source, @NonNull Boolean success, @NonNull Asset asset) {
        super(source);
        this.success = success;
        this.asset = asset;
    }
}