package xyz.dowob.stockweb.Controller.Api.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.RedisService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/user/asset")
public class ApiAssetController {
    private final AssetService assetService;
    private final RedisService redisService;
    @Autowired
    public ApiAssetController(AssetService assetService, RedisService redisService) {this.assetService = assetService;
        this.redisService = redisService;
    }


    @GetMapping("/handleAssetInfo/{assetId}")
    public ResponseEntity<?> handleAssetInfo(@PathVariable Long assetId, @RequestParam(name = "type", defaultValue = "current") String type) {
        type = type.toLowerCase();
        if (!Objects.equals(type, "current") && !Objects.equals(type, "history")) {
            return ResponseEntity.badRequest().body("錯誤的查詢類型");
        }
        String key = String.format("kline_%s_%s", type, assetId);
        try {
            Asset asset = assetService.getAssetById(assetId);
            if (asset instanceof CryptoTradingPair cryptoTradingPair && !cryptoTradingPair.isHasAnySubscribed()) {
                return ResponseEntity.badRequest().body("此資產尚未有任何訂閱，請先訂閱後再做請求");
            } else if (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed()) {
                return ResponseEntity.badRequest().body("此資產尚未有任何訂閱，請先訂閱後再做請求");
            }

            List<String> dataList = redisService.getCacheListValueFromKey(key + ":data");
            if ("processing".equals(redisService.getHashValueFromKey(key, "status"))) {
                return ResponseEntity.badRequest().body("資產資料已經在處理中");
            }
            if (!dataList.isEmpty()) {
                String lastTimestamp = redisService.getHashValueFromKey(key, "last_timestamp");
                Instant lastInstant = Instant.parse(lastTimestamp);
                Instant offsetInstant = lastInstant.plus(Duration.ofMillis(1));
                String offsetTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(offsetInstant.atZone(ZoneOffset.UTC));
                assetService.getAssetHistoryInfo(asset, type, offsetTimestamp);
            } else {
                assetService.getAssetHistoryInfo(asset, type, null);
            }
            return ResponseEntity.ok().body("開始處理資產資料，稍後用/getAssetInfo/assetId取得結果");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/getAssetInfo/{assetId}")
    public ResponseEntity<?> getAssetInfo(@PathVariable Long assetId, @RequestParam(name = "type", defaultValue = "current") String type) {
        type = type.toLowerCase();
        if (!Objects.equals(type, "current") && !Objects.equals(type, "history")) {
            return ResponseEntity.badRequest().body("錯誤的查詢類型");
        }

        String key = String.format("kline_%s_%s", type, assetId);
        try {
            String status = redisService.getHashValueFromKey(key, "status");

            if ("processing".equals(status)) {
                return ResponseEntity.badRequest().body("資產資料已經在處理中");
            } else if (status == null) {
                return ResponseEntity.badRequest().body("沒有請求過資產資料");
            } else if ("error".equals(status)) {
                return ResponseEntity.badRequest().body("資產資料處理錯誤，請重新使用/handleAssetInfo/[assetId]處理資產資料");
            } else if ("no_data".equals(status)) {
                return ResponseEntity.badRequest().body("無此資產的價格圖");
            }

            String json = assetService.formatRedisAssetInfoCacheToJson(type, key);
            return ResponseEntity.ok().body(json);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("錯誤: "+ e.getMessage());
        }
    }
}
