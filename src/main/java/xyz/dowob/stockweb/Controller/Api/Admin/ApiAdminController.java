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
import xyz.dowob.stockweb.Service.Common.ProgressTracker;
import xyz.dowob.stockweb.Service.Common.NewsService;
import xyz.dowob.stockweb.Service.Crypto.CryptoService;
import xyz.dowob.stockweb.Service.Currency.CurrencyService;
import xyz.dowob.stockweb.Service.Stock.StockTwService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
     *
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

    @GetMapping("/crypto/getTradingPairsDetail")
    public ResponseEntity<?> getServerTradingPairs() {
        try {
            String subscriptions = cryptoService.getServerTradingPairs();
            return ResponseEntity.ok().body(subscriptions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("取得伺服器加密貨幣清單失敗: " + e.getMessage());
        }
    }

    @PostMapping("/crypto/ws/start")
    public ResponseEntity<?> startWebSocket() {
        try {
            cryptoService.openConnection();
            return ResponseEntity.ok().body("WebSocket已啟動");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("啟動WebSocket失敗: " + e.getMessage());
        }
    }


    @PostMapping("/crypto/ws/stop")
    public ResponseEntity<?> stopWebSocket() {
        try {
            cryptoService.closeConnection();
            return ResponseEntity.ok().body("WebSocket已停止");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("停止WebSocket失敗: " + e.getMessage());
        }
    }

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

    @PostMapping("/crypto/trackCryptoHistoryData")
    public CompletableFuture<?> trackCryptoHistoryData(@RequestParam String tradingPair) {
        CryptoTradingPair tradingPairs = cryptoService.getCryptoTradingPair(tradingPair.toUpperCase());
        return cryptoService.trackCryptoHistoryPrices(tradingPairs).thenApplyAsync(taskId -> ResponseEntity.ok().body("請求更新成功，任務id: " + taskId));
    }

    @PostMapping("/crypto/trackCryptoDailyData")
    public ResponseEntity<?> trackCryptoDailyData() {
        try {
            cryptoService.trackCryptoHistoryPricesWithUpdateDaily();
            return ResponseEntity.ok().body("請求更新成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }



    @GetMapping("/crypto/getAllTaskProgress")
    @ResponseBody
    public List<Progress.ProgressDto> getAllTaskProgress() {
        List<Progress.ProgressDto> progressList = new ArrayList<>();
        for (Progress progress : progressTracker.getAllProgressInfo()) {
            Progress.ProgressDto dto = new Progress.ProgressDto(
                    progress.getTaskName(),
                    progress.getProgressCount(),
                    progress.getTotalTask(),
                    progress.getProgressPercentage() * 100
            );
            progressList.add(dto);
        }
        return progressList;
    }


    /**
     * 管理員-台灣股票類
     *
     */

    @PostMapping("/stock/tw/updateStockList")
    public ResponseEntity<?> updateStockList() {
        try {
            stockTwService.updateStockList();
            return ResponseEntity.ok().body("股票列表更新成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }

    }

    @PostMapping("/stock/tw/getStockDetail")
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
    @PostMapping("/stock/tw/getStockHistoryData")
    public ResponseEntity<?> getSpecificStocksHistoryPriceByStockCode(@RequestParam String stockCode) {
        try {
            StockTw stockTw = stockTwService.getStockTwByStockCode(stockCode);
            stockTwService.trackStockTwHistoryPrices(stockTw);
            return ResponseEntity.ok().body("ok");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/stock/tw/getStockDailyData")
    public ResponseEntity<?> getSpecificStocksDailyPriceByStockCode() {
        try {
            stockTwService.trackStockHistoryPricesWithUpdateDaily();
            return ResponseEntity.ok().body("請求更新成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }

    }

    /**
     * 管理員-貨幣類
     *
     */

    @PostMapping("/currency/updateCurrencyData")
    public ResponseEntity<?> updateCurrencyData() {
        try {
            currencyService.updateCurrencyData();
            return ResponseEntity.ok().body("匯率更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 管理員-一般類
     *
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

    @PostMapping("/common/updatePropertySummaryData")
    public ResponseEntity<?> updatePropertySummaryData() {
        try {
            crontabMethod.recordUserPropertySummary();
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/common/updateCashFlowData")
    public ResponseEntity<?> updateCashFlowData() {
        try {
            crontabMethod.updateUserCashFlow();
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/common/updateHeadlineNewsData")
    public ResponseEntity<?> updateHeadlineNewsData() {
        try {
            newsService.sendNewsRequest(true, 1, null, null);
            return ResponseEntity.ok().body("更新成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/common/updateAssetNewsData")
    public ResponseEntity<?> updateNewsData(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "asset", required = false) Long assetId) {
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

    @PostMapping("/common/updateNewsData")
    public ResponseEntity<?> updateNewsData() {
        try {
            crontabMethod.updateNewsData();
            return ResponseEntity.ok().body("更新成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

