package xyz.dowob.stockweb.Component.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Repository.User.PropertyRepository;

import java.util.List;

/**
 * @author yuan
 */
@Component
public class CombineMethod {
    private final PropertyRepository propertyRepository;

    private static final Logger logger = LoggerFactory.getLogger(CombineMethod.class);

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
        logger.debug("合併持有資產");
        Property combinedProperty;
        logger.debug("合併持有資產數量: {}", properties.size());
        if (properties.isEmpty()) {
            logger.debug("沒有持有資產");
            combinedProperty = null;
        } else {
            logger.debug("有持有資產");
            combinedProperty = properties.getLast();
            logger.debug("合併持有資產: {}", combinedProperty);
            for (int i = 0; i < properties.size() - 1; i++) {
                Property addProperty = properties.get(i);
                combinedProperty.setQuantity(combinedProperty.getQuantity().add(addProperty.getQuantity()));
                propertyRepository.delete(addProperty);
            }
            propertyRepository.save(combinedProperty);
        }
        logger.debug("合併持有資產完成");
        return combinedProperty;
    }
}
