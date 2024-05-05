package xyz.dowob.stockweb.Controller.Api.Admin;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Component.Method.CrontabMethod;
import xyz.dowob.stockweb.Dto.Common.Progress;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Service.Common.AssetService;
import xyz.dowob.stockweb.Service.Common.NewsService;
import xyz.dowob.stockweb.Service.Common.ProgressTracker;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author yuan
 * 管理員操作API
 */
@Controller
@RequestMapping("/api/admin")
public class ApiAdminController {

    private final CurrencyService currencyService;

    private final CryptoService cryptoService;

    private final StockTwService stockTwService;

    private final NewsService newsService;

    private final ProgressTracker progressTracker;

    private final CrontabMethod crontabMethod;

    private final AssetService assetService;

    @Autowired
    public ApiAdminController(CurrencyService currencyService, CryptoService cryptoService, StockTwService stockTwService, NewsService newsService, ProgressTracker progressTracker, CrontabMethod crontabMethod, AssetService assetService) {
        this.currencyService = currencyService;
        this.cryptoService = cryptoService;
        this.stockTwService = stockTwService;
        this.newsService = newsService;
        this.progressTracker = progressTracker;
        this.crontabMethod = crontabMethod;
        this.assetService = assetService;
    }

    /**
     * 管理員-加密貨幣類
     * 1. 更新加密貨幣清單
     * 2. 取得伺服器加密貨幣清單
     * 3. 開啟WebSocket
     * 4. 關閉WebSocket
     * 5. 重啟WebSocket
     * 6. 追蹤加密貨幣歷史價格
     * 7. 追蹤加密貨幣每日價格
     * 8. 取得所有任務進度
     * 9. 檢查並重新連接WebSocket

     * 更新加密貨幣清單
     *
     * @param response HttpServletResponse
     *
     * @return ResponseEntity
     */
    @PostMapping("/crypto/updateCryptoList")
    public ResponseEntity<?> updateCryptoList(HttpServletResponse response) {
        try {
            cryptoService.tooManyRequest(response);
            cryptoService.updateSymbolList();
            return ResponseEntity.ok().body("更新加密貨幣清單成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("更新加密貨幣清單失敗: " + e.getMessage());
        }
    }

    /**
     * 取得伺服器加密貨幣清單
     *
     * @return ResponseEntity
     */
    @GetMapping("/crypto/getTradingPairsDetail")
    public ResponseEntity<?> getServerTradingPairs() {
        try {
            String subscriptions = cryptoService.getServerTradingPairs();
            return ResponseEntity.ok().body(subscriptions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取得伺服器加密貨幣清單失敗: " + e.getMessage());
        }
    }

    /**
     * 開啟WebSocket, 並訂閱加密貨幣
     *
     * @return ResponseEntity
     */
    @PostMapping("/crypto/ws/start")
    public ResponseEntity<?> startWebSocket() {
        try {
            cryptoService.openConnection();
            return ResponseEntity.ok().body("WebSocket已啟動");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("啟動WebSocket失敗: " + e.getMessage());
        }
    }

    /**
     * 關閉WebSocket, 並清空訂閱
     *
     * @return ResponseEntity
     */
    @PostMapping("/crypto/ws/stop")
    public ResponseEntity<?> stopWebSocket() {
        try {
            cryptoService.closeConnection();
            return ResponseEntity.ok().body("WebSocket已停止");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("停止WebSocket失敗: " + e.getMessage());
        }
    }

    /**
     * 重啟WebSocket
     *
     * @return ResponseEntity
     */
    @PostMapping("/crypto/ws/restart")
    public ResponseEntity<?> restartWebSocket() {
        try {
            cryptoService.closeConnection();
            cryptoService.openConnection();
            return ResponseEntity.ok().body("WebSocket已重啟");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("重啟WebSocket失敗: " + e.getMessage());
        }
    }

    /**
     * 追蹤加密貨幣歷史價格, 並回傳任務id
     *
     * @param tradingPair String 交易對
     *
     * @return CompletableFuture 任務id
     */
    @PostMapping("/crypto/trackCryptoHistoryData")
    public CompletableFuture<?> trackCryptoHistoryData(
            @RequestParam String tradingPair) {
        CryptoTradingPair tradingPairs = cryptoService.getCryptoTradingPair(tradingPair.toUpperCase());
        return cryptoService.trackCryptoHistoryPrices(tradingPairs)
                            .thenApplyAsync(taskId -> ResponseEntity.ok().body("請求更新成功，任務id: " + taskId));
    }

    /**
     * 追蹤加密貨幣每日價格
     *
     * @return ResponseEntity
     */
    @PostMapping("/crypto/trackCryptoDailyData")
    public ResponseEntity<?> trackCryptoDailyData() {
        try {
            cryptoService.trackCryptoHistoryPricesWithUpdateDaily();
            return ResponseEntity.ok().body("請求更新成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    /**
     * 取得所有任務進度, 並回傳任務進度列表
     *
     * @return List<Progress.ProgressDto> 任務進度列表 (任務名稱, 進度, 總任務數, 進度百分比)
     */
    @GetMapping("/crypto/getAllTaskProgress")
    @ResponseBody
    public List<Progress.ProgressDto> getAllTaskProgress() {
        List<Progress.ProgressDto> progressList = new ArrayList<>();
        for (Progress progress : progressTracker.getAllProgressInfo()) {
            Progress.ProgressDto dto = new Progress.ProgressDto(progress.getTaskName(),
                                                                progress.getProgressCount(),
                                                                progress.getTotalTask(),
                                                                progress.getProgressPercentage() * 100);
            progressList.add(dto);
        }
        return progressList;
    }


    /**
     * 檢查並重新連接WebSocket
     *
     * @return ResponseEntity
     */
    @PostMapping("/crypto/checkAndReconnectWebSocket")
    public ResponseEntity<?> checkAndReconnectWebSocket() {
        try {
            crontabMethod.checkAndReconnectWebSocket();
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }
    }

    /**
     * 管理員-股票類
     * 1. 更新股票清單
     * 2. 更新股票詳細資料
     * 3. 追蹤股票歷史價格
     * 4. 追蹤股票每日價格
     * 5. 開啟即時股價追蹤
     * 6. 關閉即時股價追蹤

     * 更新股票清單
     *
     * @return ResponseEntity
     */
    @PostMapping("/stock/tw/updateStockList")
    public ResponseEntity<?> updateStockList() {
        try {
            stockTwService.updateStockList();
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }

    }

    /**
     * 更新股票詳細資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/stock/tw/updateStockDetail")
    public ResponseEntity<?> getSubscriptionStocksCurrentPrice() {
        try {
            Map<String, List<String>> result = stockTwService.checkSubscriptionValidity();
            stockTwService.trackStockNowPrices(result.get("inquiry"));
            result.remove("inquiry");
            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }

    }

    /**
     * 追蹤股票歷史價格
     *
     * @param stockCode String 股票代碼
     *
     * @return ResponseEntity
     */
    @PostMapping("/stock/tw/trackStockHistoryData")
    public ResponseEntity<?> getSpecificStocksHistoryPriceByStockCode(
            @RequestParam String stockCode) {
        try {
            StockTw stockTw = stockTwService.getStockTwByStockCode(stockCode);
            stockTwService.trackStockTwHistoryPrices(stockTw);
            return ResponseEntity.ok().body("ok");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 追蹤股票每日價格
     *
     * @return ResponseEntity
     */
    @PostMapping("/stock/tw/trackStockDailyData")
    public ResponseEntity<?> getSpecificStocksDailyPriceByStockCode() {
        try {
            stockTwService.trackStockHistoryPricesWithUpdateDaily();
            return ResponseEntity.ok().body("請求更新成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 開啟即時股價追蹤
     *
     * @return ResponseEntity
     */
    @PostMapping("/stock/tw/trackImmediatePrice")
    public ResponseEntity<?> trackImmediatePrice() {
        try {
            crontabMethod.operateStockTwTrack(true);
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }
    }

    /**
     * 關閉即時股價追蹤
     *
     * @return ResponseEntity
     */
    @PostMapping("/stock/tw/unTrackImmediatePrice")
    public ResponseEntity<?> unTrackImmediatePrice() {
        try {
            crontabMethod.operateStockTwTrack(false);
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }
    }

    /**
     * 管理員-貨幣類
     * 1. 更新貨幣清單

     * 更新貨幣清單
     *
     * @return ResponseEntity
     */
    @PostMapping("/currency/updateCurrencyData")
    public ResponseEntity<?> updateCurrencyData() {
        try {
            currencyService.updateCurrencyData();
            return ResponseEntity.ok().body("操作成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("操作成功" + e.getMessage());
        }
    }

    /**
     * 管理員-一般類
     * 1. 更新ROI資料
     * 2. 更新資產總覽資料
     * 3. 更新現金流資料
     * 4. 更新頭條新聞資料
     * 5. 更新資產新聞資料
     * 6. 更新新聞資料
     * 7. 清除過期Token
     * 8. 檢查歷史資料
     * 9. 移除過期新聞
     * 10. 更新資產列表快取
     * 11. 更新所有資產列表快取

     * 更新ROI資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updateRoiData")
    public ResponseEntity<?> updateRoiData() {
        try {
            crontabMethod.updateUserRoiData();
            return ResponseEntity.ok().body("ROI更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 更新資產總覽資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updatePropertySummaryData")
    public ResponseEntity<?> updatePropertySummaryData() {
        try {
            crontabMethod.recordUserPropertySummary();
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 更新現金流資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updateCashFlowData")
    public ResponseEntity<?> updateCashFlowData() {
        try {
            crontabMethod.updateUserCashFlow();
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 更新頭條新聞資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updateHeadlineNewsData")
    public ResponseEntity<?> updateHeadlineNewsData() {
        try {
            newsService.sendNewsRequest(true, 1, null, null);
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 更新資產新聞資料
     *
     * @param assetId Long 資產ID
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updateAssetNewsData")
    public ResponseEntity<?> updateNewsData(
            @RequestParam(value = "keyword",
                          required = false,
                          defaultValue = "") String keyword, @RequestParam(value = "asset",
                                                                           required = false) Long assetId) {
        try {
            Asset asset = null;
            if (assetId != null) {
                asset = assetService.getAssetById(assetId);
            }
            newsService.sendNewsRequest(false, 1, keyword, asset);
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 更新新聞資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updateNewsData")
    public ResponseEntity<?> updateNewsData() {
        try {
            crontabMethod.updateNewsData();
            return ResponseEntity.ok().body("更新成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 清除過期Token
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/cleanExpiredTokens")
    public ResponseEntity<?> cleanExpiredTokens() {
        try {
            crontabMethod.cleanExpiredTokens();
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 檢查歷史資料
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/checkHistoryData")
    public ResponseEntity<?> checkHistoryData() {
        try {
            crontabMethod.checkHistoryData();
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }
    }

    /**
     * 移除過期新聞
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/removeExpiredNews")
    public ResponseEntity<?> removeExpiredNews() {
        try {
            crontabMethod.removeExpiredNews();
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }
    }

    /**
     * 更新資產列表快取
     *
     * @return ResponseEntity
     */
    @PostMapping("/common/updateAssetListCache")
    public ResponseEntity<?> updateAssetListCache() {
        try {
            crontabMethod.updateAssetListCache();
            return ResponseEntity.ok().body("操作成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("操作失敗: " + e.getMessage());
        }
    }
}

