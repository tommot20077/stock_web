package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.Property.TransactionListDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.User.TransactionService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.Map;

/**
 * 這是一個用於處理用戶交易相關的控制器
 * @author yuan
 */
@Controller
@RequestMapping("/api/user/transaction")
public class ApiTransactionController {
    private final TransactionService transactionService;

    private final UserService userService;

    /**
     * 這是一個構造函數，用於注入TransactionService和UserService
     * @param transactionService 交易服務
     * @param userService 用戶服務
     */
    @Autowired
    public ApiTransactionController(TransactionService transactionService, UserService userService) {
        this.transactionService = transactionService;
        this.userService = userService;
    }

    /**
     * 紀錄交易 分成買入、賣出、提款、存款
     *
     * @param transactionListDto 交易紀錄
     * @param session            用戶session
     *
     * @return ResponseEntity
     */
    @PostMapping("/operation")
    public ResponseEntity<?> operation(
            @RequestBody TransactionListDto transactionListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            Map<String, String> failureModify = new HashMap<>();
            for (TransactionListDto.TransactionDto transaction : transactionListDto.getTransactionList()) {
                try {
                    transactionService.operation(user, transaction);
                } catch (Exception e) {
                    failureModify.put(transaction.getSymbol(), e.getMessage());
                }
            }
            if (failureModify.isEmpty()) {
                return ResponseEntity.ok().body("紀錄交易成功");
            } else {
                return ResponseEntity.status(400).body(failureModify);
            }
        } catch (JsonParseException e) {
            return ResponseEntity.status(400).body("請傳送正確格式的資料: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("伺服器錯誤: " + e.getMessage());
        }
    }

    /**
     * 取得用戶所有交易紀錄
     *
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @GetMapping("/getUserAllTransaction")
    public ResponseEntity<?> getUserAllTransaction(HttpSession session, @RequestParam(required = false, name = "page", defaultValue = "1") int page) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            String jsonString = transactionService.getUserAllTransaction(user, page);
            return ResponseEntity.ok().body(jsonString);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("伺服器錯誤: " + e.getMessage());
        }
    }
}
