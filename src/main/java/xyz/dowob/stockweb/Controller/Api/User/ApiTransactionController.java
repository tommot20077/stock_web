package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.dowob.stockweb.Dto.Property.TransactionListDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.User.TransactionService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/user/transaction")
public class ApiTransactionController {
    private final TransactionService transactionService;
    private final UserService userService;
    @Autowired
    public ApiTransactionController(TransactionService transactionService, UserService userService) {
        this.transactionService = transactionService;

        this.userService = userService;
    }

    @PostMapping("/operation")
    public ResponseEntity<?> operation(@RequestBody TransactionListDto transactionListDto, HttpSession session) {
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
                    failureModify.put(transaction.getSymbol(), e.getMessage());}
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

}
