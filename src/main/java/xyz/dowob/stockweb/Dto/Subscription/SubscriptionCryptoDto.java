package xyz.dowob.stockweb.Dto.Subscription;

import lombok.Data;

import java.util.List;

/**
 * @author yuan
 * 用於傳遞訂閱加密貨幣的資料
 * 1. subscriptions: 訂閱列表
 */
@Data
public class SubscriptionCryptoDto {
    private List<Subscription> subscriptions;

    /**
     * 用於傳遞訂閱的資料
     * 1. tradingPair: 交易對
     */
    @Data
    public static class Subscription {
        private String tradingPair;
    }
}
