package xyz.dowob.stockweb.Dto.Subscription;

import lombok.Data;

import java.util.List;

@Data
public class SubscriptionCryptoDto {
    private List<Subscription> subscriptions;

    @Data
    public static class Subscription {
        private String tradingPair;
        private String channel;
    }
}
