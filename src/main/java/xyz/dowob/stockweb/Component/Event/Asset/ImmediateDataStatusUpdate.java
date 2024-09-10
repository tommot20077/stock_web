package xyz.dowob.stockweb.Component.Event.Asset;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Handler.ImmediateDataStatusHandler;

import static xyz.dowob.stockweb.Component.Handler.ImmediateDataStatusHandler.*;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName ImmediateDataStatusUpdate
 * @description
 * @create 2024-09-04 23:24
 * @Version 1.0
 **/
@Component
public class ImmediateDataStatusUpdate implements ApplicationListener<ImmediateDataUpdateEvent> {


    /**
     * 當接收ImmediateDataUpdateEvent事件時，此方法將被調用。
     * 根據事件的類型，更新STATUS。
     *
     * @param event ImmediateDataUpdateEvent事件對象
     */
    @Override
    public void onApplicationEvent(@NotNull ImmediateDataUpdateEvent event) {
        switch (event.getType()) {
            case CRYPTO -> STATUS.put(CRYPTO_STATUS, event.getIsOpen());
            case STOCK_TW -> STATUS.put(STOCK_TW_STATUS, event.getIsOpen());
            default -> {
            }
        }
        SESSION_MAP.forEach((sessionId, session) -> ImmediateDataStatusHandler.sendMessage(session));
    }
}
