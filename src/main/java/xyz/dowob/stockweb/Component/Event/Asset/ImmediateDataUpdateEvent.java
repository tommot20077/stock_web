package xyz.dowob.stockweb.Component.Event.Asset;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Enum.AssetType;

/**
 * 當資產的開啟或關閉即時數據更新時發布的事件。
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName ImmediateDataUpdateEvent
 * @description
 * @create 2024-09-04 20:50
 * @Version 1.0
 **/
@Getter
public class ImmediateDataUpdateEvent extends ApplicationEvent {
    private final Boolean isOpen;

    private final AssetType type;

    /**
     * ImmediateDataUpdateEvent的構造函數。
     *
     * @param source 事件發生的對象。
     * @param isOpen 資產是否開啟即時數據更新。
     * @param type   資產的類型，使用AssetType枚舉類型。
     */
    public ImmediateDataUpdateEvent(@NonNull Object source, @NonNull Boolean isOpen, @NonNull AssetType type) {
        super(source);
        this.isOpen = isOpen;
        this.type = type;
    }
}
