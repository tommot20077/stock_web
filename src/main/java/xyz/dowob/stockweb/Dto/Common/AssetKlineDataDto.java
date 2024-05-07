package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;

/**
 * @author yuan
 * 用於傳遞資產K線圖的資料
 * 1. timestamp: 時間戳
 * 2. open: 開盤價
 * 3. high: 最高價
 * 4. low: 最低價
 * 5. close: 收盤價
 * 6. volume: 成交量
 */
@Data
public class AssetKlineDataDto {
    private String timestamp;

    private String open;

    private String high;

    private String low;

    private String close;

    private String volume;
}
