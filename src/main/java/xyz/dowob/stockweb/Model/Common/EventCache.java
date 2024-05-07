package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Model.User.Property;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author yuan
 * 伺服器事件快取, 用於保存伺服器事件
 * 當伺服器事件發生時, 會將事件保存至此快取
 * 當事件完成時, 會將事件從快取中移除
 * 實現Serializable, 用於序列化
 * 1. id : 快取編號
 * 2. property : 事件所屬的資產
 * 3. quantity : 事件數量
 * 4. complete : 事件是否完成
 */
@Data
@Entity
public class EventCache implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "property_id")
    private Property property;

    private BigDecimal quantity;

    private boolean complete = false;
}
