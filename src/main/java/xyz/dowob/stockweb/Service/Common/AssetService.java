package xyz.dowob.stockweb.Service.Common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Method.AssetInfluxMethod;
import xyz.dowob.stockweb.Dto.Common.AssetKlineDataDto;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Repository.Common.AssetRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AssetService {
    private final AssetRepository assetRepository;
    private final AssetInfluxMethod assetInfluxMethod;
    private final ObjectMapper objectMapper;
    private final RedisService redisService;
    Logger logger = LoggerFactory.getLogger(AssetService.class);
    @Autowired
    public AssetService(AssetRepository assetRepository, AssetInfluxMethod assetInfluxMethod, ObjectMapper objectMapper, RedisService redisService) {
        this.assetRepository = assetRepository;
        this.assetInfluxMethod = assetInfluxMethod;
        this.objectMapper = objectMapper;
        this.redisService = redisService;
    }


    @Async
    public void getAssetHistoryInfo(Asset asset, String type, String timestamp) {
        Map<String, List<FluxTable>> tableMap;
        String key = String.format("kline_%s_%s", type, asset.getId());
        logger.info("開始處理資產: " + asset.getId());
        try {
            switch (type){
                case "history":
                    redisService.saveHashToCache(key, "status", "processing", 168);
                    tableMap = assetInfluxMethod.queryByAsset(asset, true, timestamp);
                    if (tableMap.get("%s_%s".formatted(asset.getId(), type)).isEmpty()) {
                        logger.info("無資料");
                        redisService.saveHashToCache(key, "status", "success", 168);
                        return;
                    }
                    saveAssetInfoToRedis(tableMap, key, "history");
                    break;
                case "current":
                    redisService.saveHashToCache(key, "status", "processing", 168);
                    tableMap = assetInfluxMethod.queryByAsset(asset, false, timestamp);
                    if (tableMap.get("%s_%s".formatted(asset.getId(), type)).isEmpty()) {
                        logger.info("無資料");
                        redisService.saveHashToCache(key, "status", "success", 168);
                        return;
                    }
                    saveAssetInfoToRedis(tableMap, key, "current");
                    break;
                default:
                    throw new RuntimeException("錯誤的查詢類型");
            }
        } catch (Exception e) {
            redisService.saveHashToCache(key, "status", "fail", 168);
            throw new RuntimeException("發生錯誤: ", e);
        }
    }


    private void saveAssetInfoToRedis(Map<String, List<FluxTable>> tableMap, String key, String type) {
        try {
            Map<String, AssetKlineDataDto> klineDataMap = new LinkedHashMap<>();
            String lastTimePoint  = null;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

            for (Map.Entry<String, List<FluxTable>> entry : tableMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                for (FluxTable fluxTable : entry.getValue()) {
                    if (fluxTable.getRecords().isEmpty()) {
                        continue;
                    }
                    for (FluxRecord record : fluxTable.getRecords()) {
                        Instant recordTimestamp = ((Instant) Objects.requireNonNull(record.getValueByKey("_time")));
                        String timestamp = formatter.format(recordTimestamp);

                        if (lastTimePoint == null || Instant.parse(lastTimePoint).isBefore(Instant.parse(timestamp))) {
                            lastTimePoint = timestamp;
                        }
                        String field = (String) record.getValueByKey("_field");
                        Double value = null;
                        String formattedValue = null;
                        Object valueObj = record.getValueByKey("_value");
                        if (valueObj instanceof Double) {
                            value = ((Double) valueObj);
                            formattedValue = String.format("%.2f", value);
                        }
                        AssetKlineDataDto dataDto = klineDataMap.getOrDefault(timestamp, new AssetKlineDataDto());
                        dataDto.setTimestamp(timestamp);
                        if (value != null && field != null) {
                            {
                                switch (field) {
                                    case "open":
                                        dataDto.setOpen(formattedValue);
                                        break;
                                    case "high":
                                        dataDto.setHigh(formattedValue);
                                        break;
                                    case "low":
                                        dataDto.setLow(formattedValue);
                                        break;
                                    case "close":
                                        dataDto.setClose(formattedValue);
                                        break;
                                    case "volume":
                                        dataDto.setVolume(formattedValue);
                                        break;
                                    case "rate":
                                        dataDto.setClose(formattedValue);
                                        dataDto.setOpen(formattedValue);
                                        dataDto.setHigh(formattedValue);
                                        dataDto.setLow(formattedValue);
                                        dataDto.setVolume("0");
                                        break;
                                    default:
                                        throw new RuntimeException("資產資料轉換錯誤: " + field);
                                }
                            }
                        }
                        klineDataMap.put(timestamp, dataDto);
                    }
                }
            }
            objectMapper.registerModule(new JavaTimeModule());
            String kLineJson = objectMapper.writeValueAsString(klineDataMap.values());
            redisService.rPushToCacheList(key + ":data", kLineJson, 168);
            redisService.saveHashToCache(key, "type", type, 168);
            redisService.saveHashToCache(key, "last_timestamp", lastTimePoint, 168);
            redisService.saveHashToCache(key, "status", "success", 168);
        } catch (Exception e) {
            throw new RuntimeException("資產資料處理錯誤: ", e);
        }
    }


    public Asset getAssetById(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> new RuntimeException("找不到資產"));
    }


    public String formatRedisAssetInfoCacheToJson(List<String> cacheList, String type, String timestamp) {
        try {
            ArrayNode mergeArray = objectMapper.createArrayNode();
            Map <String, Object> resultMap = new HashMap<>();
            for (String item : cacheList) {
                ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(item);
                mergeArray.addAll(arrayNode);
            }
            resultMap.put("data", mergeArray);
            resultMap.put("type", type);
            resultMap.put("timestamp", timestamp);
            return objectMapper.writeValueAsString(resultMap);
        } catch (JsonProcessingException e) {
            logger.error("資產資料處理錯誤: ", e);
            throw new RuntimeException("資產資料處理錯誤: ", e);
        }
    }
}


