package xyz.dowob.stockweb.Dto.Subscription;

import lombok.AllArgsConstructor;
import lombok.Data;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.util.List;

@Data
@AllArgsConstructor
public class UserSubscriptionDto {
    private String assetId;
    private String assetType;
    private String subscribeName;
    private Boolean removeAble;
}
