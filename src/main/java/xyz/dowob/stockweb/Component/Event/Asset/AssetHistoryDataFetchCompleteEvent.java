package xyz.dowob.stockweb.Component.Event.Asset;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.Common.Asset;

@Getter
public class AssetHistoryDataFetchCompleteEvent extends ApplicationEvent {
    private final Boolean success;
    private final Asset asset;

    public AssetHistoryDataFetchCompleteEvent(Object source, Boolean success, Asset asset) {
        super(source);
        this.success = success;
        this.asset = asset;
    }
}
