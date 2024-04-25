package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;

@Data
public class AssetKlineDataDto {
    private String timestamp;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;
}
