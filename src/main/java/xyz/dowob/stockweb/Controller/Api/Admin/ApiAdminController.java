package xyz.dowob.stockweb.Controller.Api.Admin;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.Common.Progress;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Service.Common.ProgressTracker;
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
    private final ProgressTracker progressTracker;
    Logger logger = LoggerFactory.getLogger(ApiAdminController.class);
    @Autowired
    public ApiAdminController(CurrencyService currencyService, CryptoService cryptoService, StockTwService stockTwService, ProgressTracker progressTracker) {
        this.currencyService = currencyService;
        this.cryptoService = cryptoService;
        this.stockTwService = stockTwService;
        this.progressTracker = progressTracker;
    }


    /**
     * 管理員-加密貨幣類
     *
     */

    @PostMapping("/crypto/updateCryptoData")
    public ResponseEntity<?> updateCryptoData(HttpServletResponse response) {
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
            try {
                cryptoService.closeConnection();
            } catch (Exception e) {
                logger.debug("目前沒有開啟的連線，啟動新的連線");
            }
            cryptoService.openConnection();
            return ResponseEntity.ok().body("WebSocket已重啟");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("重啟WebSocket失敗: " + e.getMessage());
        }
    }

    @GetMapping("/crypto/getCryptoHistoryData")
    public CompletableFuture<?> getCryptoHistoryData(@RequestParam String tradingPair) {
        CryptoTradingPair tradingPairs = cryptoService.getCryptoTradingPair(tradingPair.toUpperCase());
        return cryptoService.trackCryptoHistoryPrices(tradingPairs)
                            .thenApplyAsync(taskId -> ResponseEntity.ok().body("任務id: " + taskId));
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

    @PostMapping("/stock/tw/updateStockData")
    public ResponseEntity<?> updateStockData() {
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




}
