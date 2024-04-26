package xyz.dowob.stockweb.Service.Common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.*;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import xyz.dowob.stockweb.Enum.NewsType;
import xyz.dowob.stockweb.Model.Common.Asset;
import xyz.dowob.stockweb.Model.Common.News;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Currency.Currency;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Repository.Common.AssetRepository;
import xyz.dowob.stockweb.Repository.Common.NewsRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;


@Service
public class NewsService {

    private final NewsRepository newsRepository;

    @Value("${news.api.token}")
    private String newsApiToken;

    @Value("${news.api.url}")
    private String newsApiUrl;

    @Value("${news.index.category}")
    private String indexCategory;

    @Value("${news.prefer.country}")
    private String preferCountry;

    @Value("${news.prefer.language}")
    private String preferLanguage;

    @Value("${news.api.pageSize}")
    private int pageSize;

    Logger logger = LoggerFactory.getLogger(NewsService.class);

    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public String getInquiryUrl(boolean isHeadline, int page, String keyword, Asset asset) {
        String inquiryUrl = newsApiUrl;
        if (asset != null && StringUtils.isBlank(keyword) && !isHeadline) {
            switch (asset) {
                case Currency currency -> keyword = currency.getCurrency();

                case StockTw stockTw -> keyword = stockTw.getStockName();

                case CryptoTradingPair cryptoTradingPair -> keyword = cryptoTradingPair.getBaseAsset();

                default -> {
                    logger.info("找不到關鍵字，設定尋找頭條");
                    isHeadline = true;
                }
            }
        } else if ((keyword == null || StringUtils.isBlank(keyword)) && !isHeadline) {
            logger.info("找不到關鍵字，設定尋找頭條");
            isHeadline = true;
        }

        if (isHeadline) {
            inquiryUrl = inquiryUrl + "top-headlines" + "?";
            inquiryUrl = inquiryUrl + "country=" + preferCountry + "&";
            inquiryUrl = inquiryUrl + "category=" + indexCategory + "&";
        } else {
            inquiryUrl = inquiryUrl + "everything" + "?";
            inquiryUrl = inquiryUrl + "language=" + preferLanguage + "&";
            inquiryUrl = inquiryUrl + "q=" + keyword + "&";
        }
        inquiryUrl = inquiryUrl + "pageSize=" + pageSize + "&";
        inquiryUrl = inquiryUrl + "page=" + page;
        logger.debug("查詢Url: " + inquiryUrl);

        return inquiryUrl;
    }
    @Async
    public void sendNewsRequest(boolean isHeadline, int page, String keyword, Asset asset) {

        String inquiryUrl = getInquiryUrl(isHeadline, page, keyword, asset);
        RestTemplate restTemplate = new RestTemplate();
        URI uri = URI.create(inquiryUrl);

        RequestEntity<?> requestEntity = RequestEntity
                .get(uri)
                .header("Authorization", "Bearer " + newsApiToken)
                .accept(MediaType.APPLICATION_JSON)
                .build();
        try {
            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("請求成功: " + response.getBody());

                handleResponse(response.getBody(), isHeadline, page, keyword, asset);

            } else {
                logger.error("請求失敗: " + response.getBody());
                throw new RuntimeException("請求失敗: " + response.getBody());
            }
        } catch (HttpStatusCodeException e) {
            logger.error("請求失敗: " + e.getStatusCode());
            logger.error("錯誤內容: " + e.getResponseBodyAsString());
            throw new RuntimeException("請求失敗: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("請求時發生錯誤: " + e.getMessage());
            throw new RuntimeException("請求時發生錯誤: " + e.getMessage());
        }
    }


    public void handleResponse(String responseBody, boolean isHeadline, int page, String keyword, Asset asset) {
        JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
        int totalResults = responseJson.get("totalResults").getAsInt();
        JsonArray articles = responseJson.get("articles").getAsJsonArray();

        List<String> titleList = newsRepository.getAllNewsWithPublishedAtAndTitle();

        logger.info("總筆數: " + totalResults);
        if (totalResults == 0) {
            logger.info("無資料");
            return;
        }

        for (JsonElement article : articles) {

            News news = new News();
            JsonObject articleJson = article.getAsJsonObject();

            JsonElement titleElement = articleJson.get("title");
            String title = titleElement.isJsonNull() ? null : titleElement.getAsString();



            if (titleList.contains(title)) {
                logger.info("重複新聞: " + title);
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
                    news.setNewsType(NewsType.HEADLINE);
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
            logger.debug("有後續資料，發送新的請求");
            sendNewsRequest(isHeadline, page + 1, keyword, asset);
        } else {
            logger.debug("無後續資料");
        }
    }


    public void deleteNewsAfterDate(LocalDateTime date) {
        newsRepository.deleteAllByPublishedAtBefore(date);
    }



    public Page<News> getAllNewsByType(String typeString, int page) {
        NewsType type = NewsType.valueOf(typeString.toUpperCase());
        PageRequest pageRequest = PageRequest.of(page - 1, 50);
        return newsRepository.findAllByNewsType(type, pageRequest);
    }

    public Page<News> getAllNewsByAsset(Asset asset, int page){
        PageRequest pageRequest = PageRequest.of(page - 1, 50);
        return newsRepository.findAllByAsset(asset, pageRequest);
    }


    public String formatNewsListToJson(Page<News> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        try {
            return objectMapper.writeValueAsString(newsList);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }





}
