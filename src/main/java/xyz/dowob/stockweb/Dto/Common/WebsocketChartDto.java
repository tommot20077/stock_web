package xyz.dowob.stockweb.Dto.Common;

import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName WebsocketChartDto
 * @description
 * @create 2024-09-10 21:52
 * @Version 1.0
 **/
@Data
public class WebsocketChartDto implements Serializable {
    private ArrayNode data;

    private String type;

    private BigDecimal preferCurrencyExrate;
}
