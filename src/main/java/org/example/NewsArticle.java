package org.example;

public class NewsArticle {
    private final String title;
    private final String summary;
    private final String url;
    private final String source;
    private final String publishedAt;
    private final String imageUrl;

    public NewsArticle(String title, String summary, String url, String source, String publishedAt, String imageUrl) {
        this.title = title;
        this.summary = summary;
        this.url = url;
        this.source = source;
        this.publishedAt = publishedAt;
        this.imageUrl = imageUrl;
    }

    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getUrl() { return url; }
    public String getSource() { return source; }
    public String getPublishedAt() { return publishedAt; }
    public String getImageUrl() { return imageUrl; }
}
