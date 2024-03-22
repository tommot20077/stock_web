package xyz.dowob.stockweb.Model.Stock;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.dowob.stockweb.Model.Common.Asset;

import java.time.LocalDate;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@PrimaryKeyJoinColumn(name = "asset_id")
@Table(name = "stock_tw_data")
public class StockTw extends Asset {

    @Column(name = "stock_code", unique = true, nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "stock_type")
    private String stockType;

    @Column(name = "industry_category")
    private String industryCategory;

    @Column(name = "subscribe_number", nullable = false)
    private int subscribeNumber = 0;

    @Column(name = "update_time")
    private LocalDate updateTime;


}
