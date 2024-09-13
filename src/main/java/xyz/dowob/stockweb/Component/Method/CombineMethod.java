package xyz.dowob.stockweb.Component.Method;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Repository.User.PropertyRepository;

import java.util.List;

/**
 * 這是一個合併方法，用於合併用戶持有資產。
 *
 * @author yuan
 */
@Component
public class CombineMethod {
    private final PropertyRepository propertyRepository;

    /**
     * 這是一個構造函數，用於注入用戶財產資料庫。
     *
     * @param propertyRepository 用戶財產資料庫
     */
    public CombineMethod(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * 合併用戶持有資產
     *
     * @param properties 持有資產
     *
     * @return 合併後的持有資產
     */
    @Transactional
    public Property combinePropertyValues(List<Property> properties) {
        Property combinedProperty;
        if (properties.isEmpty()) {
            combinedProperty = null;
        } else {
            combinedProperty = properties.getLast();
            for (int i = 0; i < properties.size() - 1; i++) {
                Property addProperty = properties.get(i);
                combinedProperty.setQuantity(combinedProperty.getQuantity().add(addProperty.getQuantity()));
                propertyRepository.delete(addProperty);
            }
            propertyRepository.save(combinedProperty);
        }
        return combinedProperty;
    }
}
