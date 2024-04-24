package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Model.User.Property;
import xyz.dowob.stockweb.Model.User.User;

import java.io.Serializable;
import java.math.BigDecimal;

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
