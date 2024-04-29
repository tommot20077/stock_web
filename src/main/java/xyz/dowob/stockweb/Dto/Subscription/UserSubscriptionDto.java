package xyz.dowob.stockweb.Dto.Subscription;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yuan
 */
@Data
@AllArgsConstructor
public class UserSubscriptionDto {
    private String assetId;
    private String assetType;
    private String subscribeName;
    private Boolean removeAble;
}
