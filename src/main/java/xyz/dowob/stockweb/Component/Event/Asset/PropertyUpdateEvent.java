package xyz.dowob.stockweb.Component.Event.Asset;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import xyz.dowob.stockweb.Model.User.User;

@Getter
public class PropertyUpdateEvent extends ApplicationEvent {
    private final User user;
    public PropertyUpdateEvent(Object source, User user) {
        super(source);
        this.user = user;
    }

    public PropertyUpdateEvent(Object source) {
        super(source);
        this.user = null;
    }
}
