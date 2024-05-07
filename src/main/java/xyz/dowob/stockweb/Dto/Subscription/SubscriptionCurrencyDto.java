package xyz.dowob.stockweb.Dto.Subscription;

import lombok.Data;

import java.util.List;

/**
 * @author yuan
 * 用於傳遞訂閱貨幣的資料
 * 1. subscriptions: 訂閱列表
 */
@Data
public class SubscriptionCurrencyDto {
    private List<Subscription> subscriptions;

    /**
     * 用於傳遞訂閱的資料
     * 1. from: 原始貨幣
     * 2. to: 交易對象貨幣
     */
    @Data
    public static class Subscription {
        private String from;

        private String to;
    }
}
