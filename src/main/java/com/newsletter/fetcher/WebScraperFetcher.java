package com.newsletter.fetcher;

import com.newsletter.model.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WebScraperFetcher {

    public List<Article> fetchFromPage(String pageUrl, String sourceName, String linkPattern) {
        try {
            Document doc = Jsoup.connect(pageUrl)
                .userAgent("AI-Coffee-Newsletter/1.0")
                .followRedirects(true)
                .get();

            Elements links = doc.select("a[href*=" + linkPattern + "]");
            List<Article> articles = new ArrayList<>();
            String today = LocalDate.now().toString();

            for (Element link : links) {
                String href = link.absUrl("href");
                String title = link.text().trim();

                if (title.isEmpty() || title.length() < 10 || href.equals(pageUrl)) continue;

                articles.add(new Article(
                    Article.generateId(href),
                    today,
                    sourceName,
                    "web_scrape",
                    href,
                    title,
                    "",
                    null,
                    0,
                    List.of(),
                    Instant.now().toString(),
                    Instant.now().toString()
                ));
            }
            return articles;
        } catch (Exception e) {
            System.err.println("Failed to scrape " + pageUrl + ": " + e.getMessage());
            return List.of();
        }
    }
}
