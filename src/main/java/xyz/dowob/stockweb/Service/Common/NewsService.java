package xyz.dowob.stockweb.Service.Common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import xyz.dowob.stockweb.Component.Annotation.MeaninglessData;
import xyz.dowob.stockweb.Enum.NewsType;
import xyz.dowob.stockweb.Exception.ServiceExceptions;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.News;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Repository.Common.NewsRepository;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author yuan
 * 新聞相關業務邏輯
 */
@Service
public class NewsService {
    private final NewsRepository newsRepository;

    @Value("${news.api.token}")
    private String newsApiToken;

    @Value("${news.api.url:https://newsapi.org/v2/}")
    private String newsApiUrl;

    @Value("${news.index.category:business}")
    private String indexCategory;

    @Value("${news.prefer.country:tw}")
    private String preferCountry;

    @Value("${news.prefer.language:zh}")
    private String preferLanguage;

    @Value("${common.global_page_size:100}")
    private int pageSize;

    /**
     * 建構子
     *
     * @param newsRepository 新聞數據庫
     */
    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    /**
     * 根據參數獲取查詢Url
     *
     * @param isHeadline 是否為頭條
     * @param page       頁數
     * @param keyword    關鍵字
     * @param asset      資產
     *
     * @return 查詢Url
     */
    public String getInquiryUrl(boolean isHeadline, int page, String keyword, Asset asset) {
        StringBuilder inquiryUrl = new StringBuilder(newsApiUrl);
        if (asset != null && StringUtils.isBlank(keyword) && !isHeadline) {
            switch (asset) {
                case Currency currency -> keyword = currency.getCurrency();
                case StockTw stockTw -> keyword = stockTw.getStockName();
                case CryptoTradingPair cryptoTradingPair -> keyword = cryptoTradingPair.getBaseAsset();
                default -> isHeadline = true;
            }
        } else if ((keyword == null || StringUtils.isBlank(keyword)) && !isHeadline) {
            isHeadline = true;
        }
        if (isHeadline) {
            inquiryUrl.append("top-headlines?");
            inquiryUrl.append("country=").append(preferCountry).append("&");
            inquiryUrl.append("category=").append(indexCategory).append("&");
        } else {
            inquiryUrl.append("everything?");
            inquiryUrl.append("language=").append(preferLanguage).append("&");
            if ("DEBT".equalsIgnoreCase(keyword)) {
                String query = "公債 OR 國債";
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                inquiryUrl = new StringBuilder("https://newsapi.org/v2/everything?language=zh&q=" + encodedQuery + "&pageSize=100&page=1");
            } else {
                inquiryUrl = new StringBuilder(inquiryUrl + "q=" + keyword + "&");
            }
        }
        inquiryUrl.append("pageSize=").append(pageSize).append("&");
        inquiryUrl.append("page=").append(page);
        return inquiryUrl.toString();
    }

    /**
     * 發送取得新聞資料得請求,並處理回應資料
     *
     * @param isHeadline 是否為頭條
     * @param page       頁數
     * @param keyword    關鍵字
     * @param asset      資產
     */
    @Async
    public void sendNewsRequest(boolean isHeadline, int page, String keyword, Asset asset) throws ServiceExceptions {
        String inquiryUrl = getInquiryUrl(isHeadline, page, keyword, asset);
        RestTemplate restTemplate = new RestTemplate();
        URI uri = URI.create(inquiryUrl);
        RequestEntity<?> requestEntity = RequestEntity.get(uri)
                                                      .header("Authorization", "Bearer " + newsApiToken)
                                                      .accept(MediaType.APPLICATION_JSON)
                                                      .build();
        try {
            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                handleResponse(response.getBody(), isHeadline, page, keyword, asset);
            } else {
                throw new ServiceExceptions(ServiceExceptions.ErrorEnum.NEWS_REQUEST_ERROR, response.getBody());
            }
        } catch (HttpStatusCodeException e) {
            throw new ServiceExceptions(ServiceExceptions.ErrorEnum.NEWS_REQUEST_ERROR, e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new ServiceExceptions(ServiceExceptions.ErrorEnum.NEWS_REQUEST_ERROR, e.getMessage());
        }
    }

    /**
     * 處理請求新聞的回應並存入數據庫
     *
     * @param responseBody 回應內容
     * @param isHeadline   是否為頭條
     * @param page         頁數
     * @param keyword      關鍵字
     * @param asset        資產
     */
    public void handleResponse(String responseBody, boolean isHeadline, int page, String keyword, Asset asset) throws ServiceExceptions {
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
        int totalResults = responseJson.get("totalResults").getAsInt();
        JsonArray articles = responseJson.get("articles").getAsJsonArray();
        List<String> titleList = newsRepository.getAllNewsWithPublishedAtAndTitle();
        if (totalResults == 0) {
            return;
        }
        for (JsonElement article : articles) {
            News news = new News();
            JsonObject articleJson = article.getAsJsonObject();
            JsonElement titleElement = articleJson.get("title");
            String title = titleElement.isJsonNull() ? null : titleElement.getAsString();
            if (titleList.contains(title)) {
                continue;
            }
            news.setTitle(title);
            JsonElement sourceNameElement = articleJson.get("source").getAsJsonObject().get("name");
            news.setSourceName(sourceNameElement.isJsonNull() ? null : sourceNameElement.getAsString());
            JsonElement urlElement = articleJson.get("url");
            news.setUrl(urlElement.isJsonNull() ? null : urlElement.getAsString());
            JsonElement urlToImageElement = articleJson.get("urlToImage");
            news.setUrlToImage(urlToImageElement.isJsonNull() ? null : urlToImageElement.getAsString());
            JsonElement authorElement = articleJson.get("author");
            news.setAuthor(authorElement.isJsonNull() ? null : authorElement.getAsString());
            JsonElement publishedAtElement = articleJson.get("publishedAt");
            if (publishedAtElement.isJsonNull()) {
                news.setPublishedAt(null);
            } else {
                String publishedAt = publishedAtElement.getAsString();
                DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                news.setPublishedAt(LocalDateTime.parse(publishedAt, formatter));
            }
            if (isHeadline) {
                news.setNewsType(NewsType.HEADLINE);
            } else {
                if (asset == null) {
                    if ("DEBT".equalsIgnoreCase(keyword)) {
                        news.setNewsType(NewsType.DEBT);
                    } else {
                        news.setNewsType(NewsType.HEADLINE);
                    }
                } else {
                    switch (asset.getAssetType()) {
                        case STOCK_TW:
                            news.setNewsType(NewsType.STOCK_TW);
                            break;
                        case CRYPTO:
                            news.setNewsType(NewsType.CRYPTO);
                            break;
                        case CURRENCY:
                            news.setNewsType(NewsType.CURRENCY);
                            break;
                        default:
                            news.setNewsType(NewsType.HEADLINE);
                            break;
                    }
                }
            }
            news.setAsset(asset);
            newsRepository.save(news);
        }
        if (totalResults > page * pageSize) {
            sendNewsRequest(isHeadline, page + 1, keyword, asset);
        }
    }

    /**
     * 刪除指定日期之前的新聞
     *
     * @param date 指定日期
     */
    public void deleteNewsBeforeDate(LocalDateTime date) {
        newsRepository.deleteAllByPublishedAtBefore(date);
    }

    /**
     * 根據新聞類型獲取所有新聞分頁內容
     *
     * @param categoryString 類型
     * @param page           頁數
     *
     * @return Page<News>
     */
    public Page<News> getAllNewsByType(String categoryString, int page) {
        try {
            NewsType type = NewsType.valueOf(categoryString.toUpperCase());
            PageRequest pageRequest = PageRequest.of(page - 1, 50);
            return newsRepository.findAllByNewsTypeOrderByPublishedAtDesc(type, pageRequest);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("錯誤的類型: " + categoryString);
        }
    }

    /**
     * 根據資產獲取所有新聞分頁內容
     *
     * @param asset 資產
     * @param page  頁數
     *
     * @return Page<News>
     */
    public Page<News> getAllNewsByAsset(Asset asset, int page) {
        PageRequest pageRequest = PageRequest.of(page - 1, 50);
        return newsRepository.findAllByAssetOrderByPublishedAtDesc(asset, pageRequest);
    }

    /**
     * 轉換新聞列表為Json格式
     *
     * @param newsList 新聞列表
     *
     * @return Json格式的新聞列表
     */
    @MeaninglessData
    public String formatNewsListToJson(Page<News> newsList) throws JsonProcessingException {
        if (newsList == null || newsList.isEmpty()) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper.writeValueAsString(newsList);
    }
}
