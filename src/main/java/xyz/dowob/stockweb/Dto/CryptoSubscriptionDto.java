package xyz.dowob.stockweb.Dto;

import lombok.Data;

import java.util.List;

@Data
public class CryptoSubscriptionDto {
    private List<Subscription> subscriptions;

    @Data
    public static class Subscription {
        private String tradingPair;
        private String channel;
    }
}
