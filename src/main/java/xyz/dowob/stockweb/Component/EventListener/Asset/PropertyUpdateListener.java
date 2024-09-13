package xyz.dowob.stockweb.Component.EventListener.Asset;

import lombok.NonNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Event.Asset.PropertyUpdateEvent;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;
import xyz.dowob.stockweb.Component.Method.retry.RetryTemplate;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Exception.RetryException;
import xyz.dowob.stockweb.Service.Common.Property.PropertyService;

import java.util.List;

/**
 * 當PropertyUpdateEvent事件發生時，此類別將被調用。
 * 實現ApplicationListener接口。並以PropertyUpdateEvent作為參數。
 * 此類別用於更新用戶的資產。
 *
 * @author yuan
 */
@Component
public class PropertyUpdateListener implements ApplicationListener<PropertyUpdateEvent> {
    private final RetryTemplate retryTemplate;

    private final PropertyService propertyService;

    private final CrontabMethod crontabMethod;

    /**
     * PropertyUpdateListener類別的構造函數。
     *
     * @param retryTemplate   重試模板
     * @param propertyService 資產相關服務方法
     * @param crontabMethod   定時任務相關方法
     */
    public PropertyUpdateListener(RetryTemplate retryTemplate, PropertyService propertyService, CrontabMethod crontabMethod) {
        this.retryTemplate = retryTemplate;
        this.propertyService = propertyService;
        this.crontabMethod = crontabMethod;
    }

    /**
     * 當PropertyUpdateEvent事件發生時，此方法將被調用。
     * 如果事件中的用戶不為null，則進行以下操作：
     * 1.獲取該用戶的所有資產。
     * 2.如果該用戶沒有資產，則停止記錄該用戶，並重置該用戶的資產資料庫。
     * 3.如果該用戶有資產，則將所有資產寫入Influx。
     * 4.如果事件中的用戶為null，則更新所有用戶的資產。
     *
     * @param event PropertyUpdateEvent事件對象
     *
     * @throws RuntimeException 如果重試失敗，則拋出異常
     */
    @Override
    public void onApplicationEvent(
            @NonNull PropertyUpdateEvent event) {
        try {
            retryTemplate.doWithRetry(() -> {
                if (event.getUser() != null) {
                    List<PropertyListDto.getAllPropertiesDto> getAllPropertiesDtoList = propertyService.getUserAllProperties(event.getUser(),
                                                                                                                             false);
                    if (getAllPropertiesDtoList == null || getAllPropertiesDtoList.isEmpty()) {
                        propertyService.resetUserPropertySummary(event.getUser());
                        return;
                    }
                    List<PropertyListDto.writeToInfluxPropertyDto> toInfluxPropertyDto = propertyService.convertGetAllPropertiesDtoToWriteToInfluxPropertyDto(
                            getAllPropertiesDtoList);
                    propertyService.writeAllPropertiesToInflux(toInfluxPropertyDto, event.getUser());
                } else {
                    crontabMethod.recordUserPropertySummary();
                }
            });
        } catch (RetryException e) {
            throw new RuntimeException("寫入資料失敗");
        }
    }
}
