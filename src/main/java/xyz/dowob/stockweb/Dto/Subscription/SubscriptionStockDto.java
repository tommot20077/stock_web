package xyz.dowob.stockweb.Dto.Subscription;

import lombok.Data;

import java.util.List;

/**
 * @author yuan
 * 用於傳遞訂閱股票的資料
 * 1. subscriptions: 訂閱列表
 * 其中: String: 股票代碼
 */
@Data
public class SubscriptionStockDto {
    private List<String> subscriptions;
}
