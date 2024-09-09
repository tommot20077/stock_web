package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Component.Method.AssetTrie.Trie;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.RedisService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 這是一個用於處理資產相關請求的控制器
 *
 * @author yuan
 */
@RestController
@RequestMapping("/api/user/asset")
public class ApiAssetController {
    private final AssetService assetService;

    private final RedisService redisService;

    private final UserService userService;

    /**
     * 這是一個構造函數，用於注入AssetService, RedisService, UserService, ObjectMapper
     *
     * @param assetService 資產相關服務
     * @param redisService 緩存服務
     * @param userService  用戶服務
     */
    @Autowired
    public ApiAssetController(AssetService assetService, RedisService redisService, UserService userService) {
        this.assetService = assetService;
        this.redisService = redisService;
        this.userService = userService;
    }


    /**
     * 取得資產資訊, 並存入Redis
     *
     * @param assetId 資產ID
     * @param session HttpSession
     *
     * @return ResponseEntity
     */
    @GetMapping("/getAssetInfo")
    public ResponseEntity<?> getAssetInfo(
            @RequestParam(name = "id") Long assetId, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            Asset asset = assetService.getAssetById(assetId);
            List<String> cachedAssetJson = assetService.getAssetStatisticsAndSaveToRedis(asset);
            return ResponseEntity.ok().body(assetService.formatRedisAssetInfoCacheToJson(cachedAssetJson, asset, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 取得資產列表
     *
     * @param page       頁數
     * @param isCache    是否使用後將資料存入快取
     * @param isFrontEnd 是否為轉換成前端格式
     * @param category   資產類型
     *
     * @return ResponseEntity
     */
    @GetMapping("/getAssetList/{category}")
    public ResponseEntity<?> getAssetList(
            @RequestParam(name = "page",
                          required = false,
                          defaultValue = "1") int page, @RequestParam(name = "isCache",
                                                                      required = false,
                                                                      defaultValue = "true") boolean isCache, @RequestParam(name = "isFrontEnd",
                                                                                                                            required = false,
                                                                                                                            defaultValue = "false") boolean isFrontEnd, @PathVariable(name = "category") String category) {
        try {
            if (category == null || category.isEmpty()) {
                return ResponseEntity.badRequest().body("沒有任何查詢參數可以使用");
            }
            String formatCategory = category.toLowerCase();
            String innerKey = formatCategory + "_page_" + page;
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
                    List<Map<String, Object>> assetsStringList = assetService.formatJsonToAssetList(cacheAssetJson);
                    return ResponseEntity.ok().body(assetService.formatStringAssetListToFrontendType(assetsStringList, innerKey));
                } else {
                    return ResponseEntity.ok().body(cacheAssetJson);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 取得政府債券資料
     *
     * @param isFormatByTime 是否轉換成時間格式
     *
     * @return ResponseEntity
     */
    @GetMapping("/getGovernmentBond")
    public ResponseEntity<?> getGovernmentBond(
            @RequestParam(name = "formatByTime",
                          required = false,
                          defaultValue = "false") boolean isFormatByTime) {
        try {
            String cacheValue = redisService.getCacheValueFromKey("governmentBond");
            if (cacheValue != null) {
                if (isFormatByTime) {
                    return ResponseEntity.ok().body(assetService.formatGovernmentBondDataByTime(cacheValue));
                }
                return ResponseEntity.ok().body(cacheValue);
            }
            Map<String, Map<String, BigDecimal>> result = assetService.getGovernmentBondData();
            String json = assetService.cacheValueDataToRedis("governmentBond", result, 4);
            if (isFormatByTime) {
                return ResponseEntity.ok().body(assetService.formatGovernmentBondDataByTime(json));
            }
            return ResponseEntity.ok().body(json);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 依照前綴詞搜尋符合名稱的資產
     *
     * @param query 查詢字串
     *
     * @return ResponseEntity
     */
    @GetMapping("/searchAsset")
    public ResponseEntity<?> searchAsset(
            @RequestParam(name = "query",
                          required = false) String query) {
        try {
            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest().body("請輸入查詢字串");
            }
            Trie trie = assetService.getAssetTrie();
            return ResponseEntity.ok().body(assetService.searchAssetList(query, trie));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("發生錯誤: " + e.getMessage());
        }
    }
}