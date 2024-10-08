package xyz.dowob.stockweb.Controller.Api.User;

import jakarta.servlet.http.HttpSession;
import org.springframework.boot.json.JsonParseException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.dowob.stockweb.Dto.Property.PropertyListDto;
import xyz.dowob.stockweb.Enum.AssetType;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Service.Common.Property.PropertyService;
import xyz.dowob.stockweb.Service.User.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 這是一個用於處理用戶與財產相關的控制器
 *
 * @author yuan
 */
@Controller
@RequestMapping("/api/user/property")
public class ApiPropertyController {
    private final UserService userService;

    private final PropertyService propertyService;

    /**
     * 這是一個構造函數，用於注入UserService和PropertyService
     *
     * @param userService     用戶服務
     * @param propertyService 財產服務
     */
    public ApiPropertyController(UserService userService, PropertyService propertyService) {
        this.userService = userService;
        this.propertyService = propertyService;
    }

    /**
     * 修改股票資產
     *
     * @param propertyListDto 股票資產清單
     * @param session         用戶session
     *
     * @return ResponseEntity
     * 如果修改成功, 回傳"修改成功"
     * 如果修改失敗, 回傳Map<String, String> key: 股票代碼, value: 失敗原因
     */
    @PostMapping("/modify/stock_tw")
    public ResponseEntity<?> modifyStock(
            @RequestBody PropertyListDto propertyListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            Map<String, String> failureModify = new HashMap<>();
            for (PropertyListDto.PropertyDto stockTw : propertyListDto.getPropertyList()) {
                try {
                    propertyService.modifyStock(user, stockTw);
                } catch (RuntimeException e) {
                    if (stockTw.getSymbol() != null) {
                        failureModify.put(stockTw.getSymbol(), e.getMessage());
                    } else {
                        failureModify.put(stockTw.getId().toString(), e.getMessage());
                    }
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

    /**
     * 修改貨幣資產
     *
     * @param propertyListDto 貨幣資產清單
     * @param session         用戶session
     *
     * @return ResponseEntity
     * 如果修改成功, 回傳"修改成功"
     * 如果修改失敗, 回傳Map<String, String> key: 貨幣代碼, value: 失敗原因
     */
    @PostMapping("/modify/currency")
    public ResponseEntity<?> modifyCurrency(
            @RequestBody PropertyListDto propertyListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            Map<String, String> failureModify = new HashMap<>();
            for (PropertyListDto.PropertyDto currency : propertyListDto.getPropertyList()) {
                try {
                    propertyService.modifyCurrency(user, currency);
                } catch (RuntimeException e) {
                    if (currency.getSymbol() != null) {
                        failureModify.put(currency.getSymbol(), e.getMessage());
                    } else {
                        failureModify.put(currency.getId().toString(), e.getMessage());
                    }
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

    /**
     * 修改加密貨幣資產
     *
     * @param propertyListDto 加密貨幣資產清單
     * @param session         用戶session
     *
     * @return ResponseEntity
     * 如果修改成功, 回傳"修改成功"
     * 如果修改失敗, 回傳Map<String, String> key: 加密貨幣代碼, value: 失敗原因
     */
    @PostMapping("/modify/crypto")
    public ResponseEntity<?> modifyCrypto(
            @RequestBody PropertyListDto propertyListDto, HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            Map<String, String> failureModify = new HashMap<>();
            for (PropertyListDto.PropertyDto crypto : propertyListDto.getPropertyList()) {
                try {
                    propertyService.modifyCrypto(user, crypto);
                } catch (RuntimeException e) {
                    if (crypto.getSymbol() != null) {
                        failureModify.put(crypto.getSymbol(), e.getMessage());
                    } else {
                        failureModify.put(crypto.getId().toString(), e.getMessage());
                    }
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

    /**
     * 取得使用者所有資產
     *
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @GetMapping("/getUserAllProperty")
    public ResponseEntity<?> getAllProperties(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            List<PropertyListDto.getAllPropertiesDto> allProperties = propertyService.getUserAllProperties(user, true);
            if (allProperties == null) {
                return ResponseEntity.status(445).body("沒有資產");
            }
            String json = propertyService.writeAllPropertiesToJson(allProperties);
            return ResponseEntity.ok().body(json);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    /**
     * 取得資產類型
     *
     * @return ResponseEntity
     */
    @GetMapping("/getPropertyType")
    public ResponseEntity<?> getPropertyType() {
        try {
            return ResponseEntity.ok().body(AssetType.values());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    /**
     * 根據資產類型取得所有資產名稱
     *
     * @param type 資產類型
     *
     * @return ResponseEntity
     */
    @GetMapping("/getAllNameByPropertyType")
    public ResponseEntity<?> getAllNameByPropertyType(
            @RequestParam String type) {
        try {
            CacheControl cacheControl = CacheControl.maxAge(1, TimeUnit.HOURS);
            return ResponseEntity.ok().cacheControl(cacheControl).body(propertyService.getAllNameByPropertyType(type.trim().toUpperCase()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    /**
     * 取得用戶資產歷史紀錄
     *
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @GetMapping("/getPropertySummary/history")
    public ResponseEntity<?> getPropertyHistorySummary(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            String json = propertyService.getPropertyOverview(user);
            return ResponseEntity.ok().body(json);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    /**
     * 取得用戶資產狀況總覽
     *
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @GetMapping("/getPropertyOverview")
    public ResponseEntity<?> getPropertyOverview(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            String json = propertyService.getUserPropertyOverview(user);
            return ResponseEntity.ok().body(json);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    /**
     * 取得用戶回報率相關狀況總覽
     *
     * @param session 用戶session
     *
     * @return ResponseEntity
     */
    @GetMapping("/getRoiStatistics")
    public ResponseEntity<?> getRoiStatistics(HttpSession session) {
        try {
            User user = userService.getUserFromJwtTokenOrSession(session);
            if (user == null) {
                return ResponseEntity.status(401).body("請先登入");
            }
            Map<String, Object> result = propertyService.getRoiStatistic(user);
            Map<String, Map<String, String>> sharpRatio = Map.of("sharp_ratio", propertyService.getSharpRatio(user));
            Map<String, Map<String, Map<String, Map<String, Object>>>> drawDown = Map.of("draw_down", propertyService.getDrawDown(user));
            result.putAll(sharpRatio);
            result.putAll(drawDown);
            return ResponseEntity.ok().body(result);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }
}
