package xyz.dowob.stockweb.Controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author yuan
 */
@Controller
public class CustomErrorController implements ErrorController {

    /**
     * 處理所有錯誤並導向錯誤頁面(不包含404)
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
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "redirect:/index";
            }
            model.addAttribute("statusCode", statusCode);
        }

        return "error";
    }
}
