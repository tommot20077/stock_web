package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.User.PropertyService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/user/property")
public class ApiPropertyController {

    private final UserService userService;
    private final PropertyService propertyService;
    Logger logger = LoggerFactory.getLogger(ApiPropertyController.class);

    @Autowired
    public ApiPropertyController(UserService userService, PropertyService propertyService) {
        this.userService = userService;
        this.propertyService = propertyService;
    }

    @PostMapping("/stock/tw/modify")
    public ResponseEntity<?> modifyStock(@RequestBody PropertyListDto propertyListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            logger.debug("獲取: " + user.getUsername() + " 的使用者");


            Map<String, String> failureModify = new HashMap<>();
            for (PropertyListDto.PropertyDto stockTw : propertyListDto.getPropertyList()) {
                try {
                    logger.debug("修改: " + stockTw.getId() + " 的股票");
                    logger.debug("來源資料: " + stockTw);
                    propertyService.modifyStock(user, stockTw);
                } catch (RuntimeException e) {
                    failureModify.put(stockTw.getSymbol(), e.getMessage());
                }
            }
            if (failureModify.isEmpty()) {
                return ResponseEntity.ok().body("修改成功");
            } else {
                return ResponseEntity.status(400).body(failureModify);
            }
        } catch (JsonParseException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @PostMapping("/currency/modify")
    public ResponseEntity<?> modifyCurrency(@RequestBody PropertyListDto propertyListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            logger.debug("獲取: " + user.getUsername() + " 的使用者");

            Map<String, String> failureModify = new HashMap<>();
            for (PropertyListDto.PropertyDto currency : propertyListDto.getPropertyList()) {
                try {
                    logger.debug("修改: " + currency.getId() + " 的貨幣");
                    logger.debug("來源資料: " + currency);
                    propertyService.modifyCurrency(user, currency);
                } catch (RuntimeException e) {
                    failureModify.put(currency.getSymbol(), e.getMessage());
                }
            }
            if (failureModify.isEmpty()) {
                return ResponseEntity.ok().body("修改成功");
            } else {
                return ResponseEntity.status(400).body(failureModify);
            }

        } catch (JsonParseException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @PostMapping("/crypto/modify")
    public ResponseEntity<?> modifyCrypto(@RequestBody PropertyListDto propertyListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            logger.debug("獲取: " + user.getUsername() + " 的使用者");

            Map<String, String> failureModify = new HashMap<>();
            for (PropertyListDto.PropertyDto crypto : propertyListDto.getPropertyList()) {
                try {
                    logger.debug("修改: " + crypto.getId() + " 的加密貨幣");
                    logger.debug("來源資料: " + crypto);
                    propertyService.modifyCrypto(user, crypto);
                } catch (RuntimeException e) {
                    failureModify.put(crypto.getSymbol(), e.getMessage());
                }
            }
            if (failureModify.isEmpty()) {
                return ResponseEntity.ok().body("修改成功");
            } else {
                return ResponseEntity.status(400).body(failureModify);
            }
        } catch (JsonParseException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

}
