package xyz.dowob.stockweb.Component.Event.Asset;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.User.User;

/**
 * 當用戶資產更新時發布的事件。
 * 繼承自ApplicationEvent。
 * 此事件用於通知PropertyUpdateListener進行後續操作。
 * 它包含一個User對象，代表其屬性被更新的用戶。
 * 如果User對象為null，則表示屬性更新事件並未與特定用戶相關聯。
 * 在這種情況下，PropertyUpdateListener應該對所有用戶的屬性進行更新。
 *
 * @author yuan
 */
@Getter
public class PropertyUpdateEvent extends ApplicationEvent {
    private final User user;

    /**
     * PropertyUpdateEvent的構造函數。
     *
     * @param source 事件最初發生的對象。
     * @param user   用戶資產更新的用戶。
     */
    public PropertyUpdateEvent(@NonNull Object source, User user) {
        super(source);
        this.user = user;
    }

    /**
     * PropertyUpdateEvent類別的構造函數。
     * 當用戶資產更新事件並未與特定用戶相關聯時使用此構造函數。
     *
     * @param source 事件最初發生的對象。
     */
    public PropertyUpdateEvent(@NonNull Object source) {
        super(source);
        this.user = null;
    }
}