package xyz.dowob.stockweb.Dto.Subscription;

import lombok.Data;

import java.util.List;

@Data
public class SubscriptionCurrencyDto {
    private List<Subscription> subscriptions;

    @Data
    public static class Subscription {
        private String from;
        private String to;
    }
}
