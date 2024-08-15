package xyz.dowob.stockweb.Controller.Error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 這是一個用於處理錯誤的控制器
 *
 * @author yuan
 */
@Controller
public class CustomErrorController implements ErrorController {
    Logger logger = LoggerFactory.getLogger(CustomErrorController.class);

    /**
     * 處理所有錯誤並導向錯誤頁面(不包含404)
     * 404錯誤將導向首頁
     *
     * @param request 錯誤請求
     * @param model   錯誤訊息
     *
     * @return 錯誤頁面
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            logger.error("Error status: " + status);
            int statusCode = Integer.parseInt(status.toString());
            if (isApiRequest(request)) {
                logger.debug("發生錯誤:api類型");
                return handleApiError(statusCode);
            }
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                logger.debug("發生網頁:404錯誤");
                return "redirect:/index";
            } else {
                model.addAttribute("statusCode", statusCode);
            }
        }
        return "error";
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        logger.error("acceptHeader: " + acceptHeader);
        return acceptHeader != null && (acceptHeader.contains("application/json"));
    }

    private String handleApiError(int statusCode) {
        return "{\"statusCode\": " + statusCode + ", \"message\": \"發生錯誤\"}";
    }
}
