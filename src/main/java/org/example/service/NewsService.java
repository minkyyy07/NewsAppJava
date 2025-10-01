// src/main/java/org/example/service/NewsService.java
package org.example.service;

import org.example.NewsArticle;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;

import org.w3c.dom.*;

public class NewsService {
    private static final List<String> POLITICS_FEEDS = List.of(
            "https://feeds.reuters.com/Reuters/PoliticsNews",
            "https://feeds.bbci.co.uk/news/politics/rss.xml",
            "http://rss.cnn.com/rss/cnn_allpolitics.rss"
    );

    private static final List<String> SPORTS_FEEDS = List.of(
            "https://feeds.reuters.com/reuters/sportsNews",
            "https://feeds.bbci.co.uk/sport/rss.xml",
            "http://rss.cnn.com/rss/edition_sport.rss",
            "https://www.espn.com/espn/rss/news"
    );

    private static final List<String> TECHNOLOGY_FEEDS = List.of(
            "https://feeds.reuters.com/reuters/technologyNews",
            "https://feeds.bbci.co.uk/news/technology/rss.xml",
            "http://rss.cnn.com/rss/edition_technology.rss",
            "https://techcrunch.com/feed/",
            "https://www.wired.com/feed/"
    );

    private static final List<String> SCIENCE_FEEDS = List.of(
            "https://feeds.reuters.com/reuters/scienceNews",
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://www.sciencedaily.com/rss/all.xml",
            "https://www.nature.com/nature.rss",
            "https://feeds.feedburner.com/oreilly/radar"
    );

    private static final List<String> BUSINESS_FEEDS = List.of(
            "https://feeds.reuters.com/reuters/businessNews",
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "http://rss.cnn.com/rss/money_latest.rss",
            "https://feeds.bloomberg.com/markets/news.rss",
            "https://www.ft.com/rss/home"
    );

    private static final List<String> HEALTH_FEEDS = List.of(
            "https://feeds.reuters.com/reuters/health",
            "https://feeds.bbci.co.uk/news/health/rss.xml",
            "http://rss.cnn.com/rss/cnn_health.rss",
            "https://www.medicalnewstoday.com/rss",
            "https://www.healthline.com/rss"
    );

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Кэш страниц: ключ = category|query|pageSize|page
    private final Map<String, PageResult> cache = new ConcurrentHashMap<>();

    public PageResult fetchPage(String category, String query, int page, int pageSize) {
        String cat = normalizeCategory(category);
        String q = query == null ? "" : query.trim();
        String key = cat + "|" + q.toLowerCase(Locale.ROOT) + "|" + pageSize + "|" + page;
        PageResult cached = cache.get(key);
        if (cached != null) return cached;

        List<String> feeds = switch (cat) {
            case "politics" -> POLITICS_FEEDS;
            case "sports" -> SPORTS_FEEDS;
            case "technology" -> TECHNOLOGY_FEEDS;
            case "science" -> SCIENCE_FEEDS;
            case "business" -> BUSINESS_FEEDS;
            case "health" -> HEALTH_FEEDS;
            default -> {
                List<String> all = new ArrayList<>();
                all.addAll(POLITICS_FEEDS);
                all.addAll(SPORTS_FEEDS);
                all.addAll(TECHNOLOGY_FEEDS);
                all.addAll(SCIENCE_FEEDS);
                all.addAll(BUSINESS_FEEDS);
                all.addAll(HEALTH_FEEDS);
                yield all;
            }
        };

        // Загружаем и парсим все ленты
        List<NewsRecord> aggregated = new ArrayList<>();
        for (String url : feeds) {
            try {
                aggregated.addAll(fetchFeed(url));
            } catch (Exception e) {
                // Игнорируем частичные сбои, продолжаем с остальными лентами
            }
        }

        // Фильтрация по запросу
        if (!q.isBlank()) {
            String ql = q.toLowerCase(Locale.ROOT);
            aggregated = aggregated.stream()
                    .filter(n -> (n.title != null && n.title.toLowerCase(Locale.ROOT).contains(ql))
                            || (n.summary != null && n.summary.toLowerCase(Locale.ROOT).contains(ql)))
                    .collect(Collectors.toList());
        }

        // Сортировка по дате (новые первыми)
        aggregated.sort((a, b) -> {
            Instant ia = a.instant == null ? Instant.EPOCH : a.instant;
            Instant ib = b.instant == null ? Instant.EPOCH : b.instant;
            return ib.compareTo(ia);
        });

        // Пагинация
        int offset = page * pageSize;
        List<NewsRecord> pageSlice;
        boolean hasNext;
        if (offset >= aggregated.size()) {
            pageSlice = List.of();
            hasNext = false;
        } else {
            int to = Math.min(offset + pageSize, aggregated.size());
            pageSlice = aggregated.subList(offset, to);
            hasNext = to < aggregated.size();
        }

        List<NewsArticle> articles = pageSlice.stream()
                .map(n -> new NewsArticle(
                        orEmpty(n.title),
                        stripHtml(orEmpty(n.summary)),
                        orEmpty(n.link),
                        orEmpty(n.source),
                        n.publishedAt == null ? "" : n.publishedAt,
                        ""
                ))
                .collect(Collectors.toList());

        PageResult result = new PageResult(articles, hasNext);
        cache.put(key, result);
        return result;
    }

    private String normalizeCategory(String category) {
        if (category == null) return "all";
        String c = category.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "политика", "politics" -> "politics";
            case "спорт", "sports" -> "sports";
            case "технологии", "technology" -> "technology";
            case "наука", "science" -> "science";
            case "бизнес", "business" -> "business";
            case "здоровье", "health" -> "health";
            default -> "all";
        };
    }

    private List<NewsRecord> fetchFeed(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "NewsApp/1.0 (+https://localhost)")
                .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
                .GET()
                .build();
        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode());

        byte[] body = res.body();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(body));

        Element root = doc.getDocumentElement();
        String rootName = localName(root);
        List<NewsRecord> list = new ArrayList<>();
        String feedSource = hostFromUrl(url);

        if ("rss".equalsIgnoreCase(rootName) || "rdf".equalsIgnoreCase(rootName)) {
            // RSS 2.0 / RDF
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String title = textOfFirst(item, List.of("title"));
                String link = textOfFirst(item, List.of("link"));
                String desc = textOfFirst(item, List.of("description", "content:encoded"));
                String pub = textOfFirst(item, List.of("pubDate", "dc:date"));
                Instant ins = parseDate(pub);
                String src = sourceFromLinkOrDefault(link, feedSource);
                list.add(new NewsRecord(title, desc, link, src, pub, ins));
            }
        } else if ("feed".equalsIgnoreCase(rootName)) {
            // Atom
            NodeList entries = doc.getElementsByTagName("entry");
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String title = textOfFirst(entry, List.of("title"));
                String link = linkFromAtom(entry);
                String desc = textOfFirst(entry, List.of("summary", "content"));
                String pub = textOfFirst(entry, List.of("updated", "published"));
                Instant ins = parseDate(pub);
                String src = sourceFromLinkOrDefault(link, feedSource);
                list.add(new NewsRecord(title, desc, link, src, pub, ins));
            }
        }
        return list;
    }

    private static String localName(Node n) {
        String ln = n.getLocalName();
        if (ln != null) return ln;
        String tn = n.getNodeName();
        int idx = tn.indexOf(':');
        return idx >= 0 ? tn.substring(idx + 1) : tn;
    }

    private String linkFromAtom(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element l = (Element) links.item(i);
            String rel = l.getAttribute("rel");
            String href = l.getAttribute("href");
            if (href != null && !href.isBlank() && (rel == null || rel.isBlank() || "alternate".equalsIgnoreCase(rel))) {
                return href;
            }
        }
        return textOfFirst(entry, List.of("link"));
    }

    private String textOfFirst(Element parent, List<String> names) {
        for (String name : names) {
            NodeList nl = parent.getElementsByTagName(name);
            if (nl.getLength() > 0) {
                String t = text(nl.item(0));
                if (t != null && !t.isBlank()) return t.trim();
            }
            // также пробуем без префикса
            int idx = name.indexOf(':');
            if (idx > 0) {
                String bare = name.substring(idx + 1);
                NodeList nl2 = parent.getElementsByTagName(bare);
                if (nl2.getLength() > 0) {
                    String t = text(nl2.item(0));
                    if (t != null && !t.isBlank()) return t.trim();
                }
            }
        }
        return "";
    }

    private String text(Node node) {
        if (node == null) return null;
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.TEXT_NODE || c.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(c.getNodeValue());
            }
        }
        return sb.toString();
    }

    private String sourceFromLinkOrDefault(String link, String dflt) {
        try {
            if (link != null && !link.isBlank()) {
                URL u = new URL(link);
                String host = u.getHost();
                if (host != null && !host.isBlank()) return host.replaceFirst("^www\\.", "");
            }
        } catch (Exception ignored) {}
        return dflt;
    }

    private String hostFromUrl(String url) {
        try {
            URL u = new URL(url);
            String host = u.getHost();
            if (host != null && !host.isBlank()) return host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {}
        return "";
    }

    private Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        // Попробуем несколько форматов
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ISO_INSTANT,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME
        );
        for (DateTimeFormatter f : fmts) {
            try {
                if (f == DateTimeFormatter.RFC_1123_DATE_TIME) {
                    ZonedDateTime z = ZonedDateTime.parse(t, f);
                    return z.toInstant();
                } else if (f == DateTimeFormatter.ISO_INSTANT) {
                    return Instant.parse(t);
                } else {
                    OffsetDateTime odt = OffsetDateTime.parse(t, f);
                    return odt.toInstant();
                }
            } catch (DateTimeParseException ignored) {}
        }
        // Последняя попытка: убрать запятые/UTC обозначения
        try { return Instant.parse(t.replace(" ", "T").replace("Z", "Z")); } catch (Exception ignored) {}
        return null;
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static String stripHtml(String s) {
        if (s == null || s.isBlank()) return "";
        String noTags = s.replaceAll("<[^>]+>", " ");
        // Простейшая декодировка часто встречающихся сущностей
        noTags = noTags.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return noTags.replaceAll("\n{3,}", "\n\n").trim();
    }

    public record PageResult(List<NewsArticle> articles, boolean hasNext) {}

    private static class NewsRecord {
        final String title;
        final String summary;
        final String link;
        final String source;
        final String publishedAt;
        final Instant instant;
        NewsRecord(String title, String summary, String link, String source, String publishedAt, Instant instant) {
            this.title = title;
            this.summary = summary;
            this.link = link;
            this.source = source;
            this.publishedAt = publishedAt;
            this.instant = instant;
        }
    }
}
