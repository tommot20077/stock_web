package xyz.dowob.stockweb.Dto;

import lombok.Data;

import java.util.List;
@Data
public class StockSubscriptionDto {
    private List<String> stockId;
}
