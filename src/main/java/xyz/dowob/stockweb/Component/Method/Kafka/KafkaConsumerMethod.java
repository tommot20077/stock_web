package xyz.dowob.stockweb.Component.Method.Kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Component.Handler.KlineWebSocketHandler;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Dto.Common.KafkaWebsocketDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Crypto.CryptoInfluxService;
import xyz.dowob.stockweb.Service.Stock.StockTwInfluxService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用於Kafka消費者的方法。
 * 當設定檔中的common.kafka.enable為true時，此類別將被實例化。
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName KafkaConsumerMethod
 * @description
 * @create 2024-09-06 01:27
 * @Version 1.0
 **/
@Component
@ConditionalOnProperty(name = "common.kafka.enable",
                       havingValue = "true")
public class KafkaConsumerMethod {
    private final CryptoInfluxService cryptoInfluxService;

    private final StockTwInfluxService stockTwInfluxService;

    private final KlineWebSocketHandler klineWebSocketHandler;

    private final AssetService assetService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Asset> ASSET_CACHE_MAP = new ConcurrentHashMap<>();

    Logger log = LoggerFactory.getLogger(KafkaConsumerMethod.class);

    public KafkaConsumerMethod(CryptoInfluxService cryptoInfluxService, StockTwInfluxService stockTwInfluxService, KlineWebSocketHandler klineWebSocketHandler, AssetService assetService) {
        this.cryptoInfluxService = cryptoInfluxService;
        this.stockTwInfluxService = stockTwInfluxService;
        this.klineWebSocketHandler = klineWebSocketHandler;
        this.assetService = assetService;
    }


    /**
     * 用於Kafka處理消費者的加密貨幣即時K線資料並儲存到InfluxDB。
     *
     * @param klineData Kafka消費者接收到的加密貨幣即時K線資料
     */
    @KafkaListener(topics = "crypto_kline",
                   groupId = "influxdb")
    public void consumeCryptoKlineDataByInfluxdb(ConsumerRecord<String, Object> klineData) {
        Map<String, Map<String, String>> klineDataMap = formatConsumerRecordToMap(klineData);
        if (klineDataMap != null) {
            cryptoInfluxService.writeToInflux(klineDataMap);
        } else {
            log.debug("加密貨幣即時K線資料為空");
        }
    }

    /**
     * 用於Kafka處理消費者的台灣股票即時K線資料並儲存到InfluxDB。
     *
     * @param klineData Kafka消費者接收到的台灣股票即時K線資料
     */
    @KafkaListener(topics = "stock_tw_kline",
                   groupId = "influxdb")
    public void consumeStockTwKlineDataByInfluxdb(ConsumerRecord<String, Object> klineData) {
        Map<String, Map<String, String>> klineDataMap = formatConsumerRecordToMap(klineData);
        if (klineDataMap != null) {
            stockTwInfluxService.writeToInflux(klineDataMap);
        } else {
            log.debug("台灣股票即時K線資料為空");
        }
    }


    /**
     * 用於Kafka處理消費者的即時K線資料轉換資料格式後發送到WebSocket。
     *
     * @param klineData Kafka消費者接收到的即時K線資料
     */
    @KafkaListener(topics = "crypto_kline",
                   groupId = "websocket")
    @KafkaListener(topics = "stock_tw_kline",
                   groupId = "websocket")
    public void consumeKlineDataByWebsocket(@Header(KafkaHeaders.RECEIVED_TOPIC) String topic, ConsumerRecord<String, Object> klineData) {
        Map<String, Map<String, String>> klineDataMap = formatConsumerRecordToMap(klineData);
        if (klineDataMap != null) {
            for (Map.Entry<String, Map<String, String>> entry : klineDataMap.entrySet()) {
                String assetName = entry.getKey();
                Asset asset;
                if (ASSET_CACHE_MAP.containsKey(assetName)) {
                    asset = ASSET_CACHE_MAP.get(assetName);
                } else {
                    asset = assetService.getAssetByAssetName(assetName);
                    if (asset == null) {
                        log.error("找不到名為{}的資產", entry.getKey());
                        continue;
                    }
                    ASSET_CACHE_MAP.put(assetName, asset);
                }

                KafkaWebsocketDto kafkaWebsocketDto = new KafkaWebsocketDto();
                Map<String, String> assetData = entry.getValue();
                AssetKlineDataDto assetKlineDataDto = formatMapToAssetKlineDataDto(assetData);
                kafkaWebsocketDto.setAssetId(asset.getId());
                kafkaWebsocketDto.setAssetType(asset.getAssetType());
                kafkaWebsocketDto.setAssetName(assetName);
                kafkaWebsocketDto.setData(assetKlineDataDto);
                CompletableFuture.runAsync(() -> klineWebSocketHandler.sendKlineDataByKafka(kafkaWebsocketDto));
            }
        } else {
            log.debug("{}即時K線資料為空", "crypto_kline".equals(topic) ? "虛擬貨幣" : "台灣股票");
        }
    }


    /**
     * 處理Redis中緩存的即時K線資料成Map格式。
     * 其中資產名稱為key，資產資料為value。
     *
     * @param obj Kafka消費者接收到的即時K線資料
     *
     * @return 轉換後的Map格式即時K線資料
     */
    private Map<String, Map<String, String>> formatConsumerRecordToMap(ConsumerRecord<String, Object> obj) {
        try {
            String jsonString = obj.value().toString();
            jsonString = jsonString.replaceAll("([a-zA-Z0-9_]+)=([a-zA-Z0-9_.]+)", "\"$1\":\"$2\"");
            jsonString = jsonString.replaceAll("([a-zA-Z0-9_]+)=", "\"$1\":");
            Map<String, Map<String, String>> formatKlineData = objectMapper.readValue(jsonString, new TypeReference<>() {});
            log.debug("轉換後的Map資料: {}", formatKlineData);
            return formatKlineData;
        } catch (JsonProcessingException e) {
            log.error("Kafka在轉換台灣股票K線資料時發生錯誤 {}", e.getMessage());
            return null;
        }
    }

    /**
     * 將Map格式的資產資料轉換為AssetKlineDataDto格式。
     *
     * @param assetData Map格式的資產資料
     *
     * @return AssetKlineDataDto格式的資產資料
     */
    private AssetKlineDataDto formatMapToAssetKlineDataDto(Map<String, String> assetData) {
        AssetKlineDataDto assetKlineDataDto = new AssetKlineDataDto();
        assetKlineDataDto.setTimestamp(assetData.get("time"));
        assetKlineDataDto.setOpen(assetData.get("open"));
        assetKlineDataDto.setHigh(assetData.get("high"));
        assetKlineDataDto.setLow(assetData.get("low"));
        assetKlineDataDto.setClose(assetData.get("close"));
        assetKlineDataDto.setVolume(assetData.get("volume"));
        return assetKlineDataDto;
    }
}
