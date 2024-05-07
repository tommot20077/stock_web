package xyz.dowob.stockweb.Dto.Subscription;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yuan
 * 用於傳遞用戶訂閱的資料
 * 1. assetId: 資產ID
 * 2. assetType: 資產類型
 * 3. subscribeName: 訂閱名稱
 * 4. removeAble: 是否可移除
 */
@Data
@AllArgsConstructor
public class UserSubscriptionDto {
    private String assetId;

    private String assetType;

    private String subscribeName;

    private Boolean removeAble;
}
