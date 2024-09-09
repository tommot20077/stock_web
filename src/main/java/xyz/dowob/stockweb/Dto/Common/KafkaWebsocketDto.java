package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName KafkaWebsocketDto
 * @description
 * @create 2024-09-09 00:36
 * @Version 1.0
 **/
@Data
public class KafkaWebsocketDto {
    private Long assetId;

    private AssetType assetType;

    private String assetName;

    private AssetKlineDataDto data;
}
