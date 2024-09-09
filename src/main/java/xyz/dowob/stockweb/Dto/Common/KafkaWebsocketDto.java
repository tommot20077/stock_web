package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;
import xyz.dowob.stockweb.Enum.AssetType;

/**
 * 用於傳遞Kafka Websocket的資料
 * 1. assetId: 資產ID
 * 2. assetType: 資產類型
 * 3. assetName: 資產名稱
 * 4. data: 資產K線圖資料
 *
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
