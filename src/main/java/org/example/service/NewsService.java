// src/main/java/org/example/service/NewsService.java
package org.example.service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.example.NewsArticle;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NewsService {
    private static final String BASE = "https://api.spaceflightnewsapi.net/v4/articles/";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Gson gson = new Gson();

    // Простой кэш страниц: ключ = query|pageSize|page
    private final Map<String, PageResult> cache = new ConcurrentHashMap<>();

    public PageResult fetchPage(String query, int page, int pageSize) {
        String q = query == null ? "" : query.trim();
        String key = q + "|" + pageSize + "|" + page;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        int offset = page * pageSize;
        StringBuilder sb = new StringBuilder(BASE)
                .append("?limit=").append(pageSize)
                .append("&offset=").append(offset);
        if (!q.isBlank()) {
            sb.append("&search=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(sb.toString()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("HTTP " + res.statusCode());
            }
            SpaceflightResponse dto = gson.fromJson(res.body(), SpaceflightResponse.class);
            List<NewsArticle> articles = new ArrayList<>();
            if (dto.results != null) {
                for (ArticleDto a : dto.results) {
                    articles.add(new NewsArticle(
                            orEmpty(a.title),
                            orEmpty(a.summary),
                            orEmpty(a.url),
                            orEmpty(a.newsSite),
                            orEmpty(a.publishedAt),
                            orEmpty(a.imageUrl)
                    ));
                }
            }
            boolean hasNext = dto.next != null && !dto.next.isBlank();
            PageResult result = new PageResult(articles, hasNext);
            cache.put(key, result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Не удалось загрузить новости", e);
        }
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    public record PageResult(List<NewsArticle> articles, boolean hasNext) {}

    // DTO под структуру API
    static class SpaceflightResponse {
        int count;
        String next;
        String previous;
        List<ArticleDto> results;
    }

    static class ArticleDto {
        String title;
        String summary;
        String url;
        @SerializedName("image_url")
        String imageUrl;
        @SerializedName("news_site")
        String newsSite;
        @SerializedName("published_at")
        String publishedAt;
    }
}