package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.RedisService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/user/asset")
public class ApiAssetController {
    private final AssetService assetService;
    private final RedisService redisService;
    private final UserService userService;
    @Autowired
    public ApiAssetController(AssetService assetService, RedisService redisService, UserService userService) {this.assetService = assetService;
        this.redisService = redisService;
        this.userService = userService;
    }


    @GetMapping("/handleKlineInfo/{assetId}")
    public ResponseEntity<?> handleAssetInfo(@PathVariable Long assetId, @RequestParam(name = "type", defaultValue = "current") String type) {
        type = type.toLowerCase();
        if (!Objects.equals(type, "current") && !Objects.equals(type, "history")) {
            return ResponseEntity.badRequest().body("錯誤的查詢類型");
        }
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("kline_%s", hashInnerKey);

        try {
            Asset asset = assetService.getAssetById(assetId);
            if (asset instanceof CryptoTradingPair cryptoTradingPair && !cryptoTradingPair.isHasAnySubscribed()) {
                return ResponseEntity.badRequest().body("此資產尚未有任何訂閱，請先訂閱後再做請求");
            } else if (asset instanceof StockTw stockTw && !stockTw.isHasAnySubscribed()) {
                return ResponseEntity.badRequest().body("此資產尚未有任何訂閱，請先訂閱後再做請求");
            }

            List<String> dataList = redisService.getCacheListValueFromKey(listKey + ":data");
            if ("processing".equals(redisService.getHashValueFromKey("kline", hashInnerKey + "status"))) {
                return ResponseEntity.badRequest().body("資產資料已經在處理中");
            }
            if (!dataList.isEmpty()) {
                String lastTimestamp = redisService.getHashValueFromKey("kline", hashInnerKey + "last_timestamp");
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

    @GetMapping("/getKlineInfo/{assetId}")
    public ResponseEntity<?> getAssetInfo(@PathVariable Long assetId, @RequestParam(name = "type", defaultValue = "current") String type) {
        type = type.toLowerCase();
        if (!Objects.equals(type, "current") && !Objects.equals(type, "history")) {
            return ResponseEntity.badRequest().body("錯誤的查詢類型");
        }
        String hashInnerKey = String.format("%s_%s:", type, assetId);
        String listKey = String.format("kline_%s", hashInnerKey);

        try {
            String status = redisService.getHashValueFromKey("kline", hashInnerKey+ "status");

            if ("processing".equals(status)) {
                return ResponseEntity.badRequest().body("資產資料已經在處理中");
            } else if (status == null) {
                return ResponseEntity.badRequest().body("沒有請求過資產資料");
            } else if ("error".equals(status)) {
                return ResponseEntity.badRequest().body("資產資料處理錯誤，請重新使用/handleAssetInfo/[assetId]處理資產資料");
            } else if ("no_data".equals(status)) {
                return ResponseEntity.badRequest().body("無此資產的價格圖");
            }

            String json = assetService.formatRedisAssetKlineCacheToJson(type, listKey, hashInnerKey);
            return ResponseEntity.ok().body(json);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("錯誤: "+ e.getMessage());
        }
    }

    @GetMapping("/getAssetInfo")
    public ResponseEntity<?> getAssetInfo(@RequestParam(name = "id") Long assetId, HttpSession session) {
        try {
            String assetKey = String.format("asset_%s:", assetId);
            User user = userService.getUserFromJwtTokenOrSession(session);
            Asset asset = assetService.getAssetById(assetId);
            List<String> cachedAssetJson = redisService.getCacheListValueFromKey(assetKey + "statistics");
            if (cachedAssetJson == null || cachedAssetJson.isEmpty()) {
                cachedAssetJson = assetService.getAssetStatisticsAndSaveToRedis(asset, assetKey);
            }
            return ResponseEntity.ok().body(assetService.formatRedisAssetInfoCacheToJson(cachedAssetJson, asset, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }

    @GetMapping("/getAssetList/{category}")
    public ResponseEntity<?> getAssetList(@RequestParam(name = "page", required = false, defaultValue = "1") int page,
                                          @RequestParam(name = "isCache", required = false, defaultValue = "true") boolean isCache,
                                          @RequestParam(name = "isFrontEnd", required = false, defaultValue = "false") boolean isFrontEnd,
                                          @PathVariable(name = "category") String category
    ) {
        try {
            if (category == null || category.isEmpty()) {
                return ResponseEntity.badRequest().body("沒有任何查詢參數可以使用");
            }
            String formatCategory = category.toLowerCase();
            String innerKey = formatCategory + "_page_" + page;
           ;
            String cacheAssetJson;
            if (isFrontEnd) {
                cacheAssetJson = redisService.getHashValueFromKey("frontendAssetList", innerKey);
            } else {
                cacheAssetJson = redisService.getHashValueFromKey("asset", innerKey);
            }

            if (cacheAssetJson == null) {
                List<Asset> assetsList = assetService.findAssetPageByType(formatCategory, page, isCache);
                if (isFrontEnd) {
                    return ResponseEntity.ok().body(assetService.formatStringAssetListToFrontendType(assetsList, innerKey));
                } else {
                    return ResponseEntity.ok().body(assetsList);
                }
            } else {
                if (isFrontEnd) {
                    List<Map<String, Object>>assetsStringList = assetService.formatJsonToAssetList(cacheAssetJson);
                    return ResponseEntity.ok().body(assetService.formatStringAssetListToFrontendType(assetsStringList, innerKey));
                } else {
                    return ResponseEntity.ok().body(cacheAssetJson);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: "+e.getMessage());
        }
    }
}
