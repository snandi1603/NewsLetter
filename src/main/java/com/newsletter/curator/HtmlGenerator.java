package com.newsletter.curator;

import com.newsletter.model.Digest;

public class HtmlGenerator {

    private final String apiUrl;

    public HtmlGenerator(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String generateDigestPage(Digest digest, String previousDate, String nextDate) {
        StringBuilder top5Html = new StringBuilder();
        for (Digest.DigestEntry entry : digest.top5()) {
            top5Html.append(renderArticleCard(entry, digest.digestDate()));
        }

        StringBuilder moreHtml = new StringBuilder();
        for (Digest.DigestEntry entry : digest.more()) {
            moreHtml.append(renderMoreItem(entry, digest.digestDate()));
        }

        String prevLink = previousDate != null
            ? "<a href=\"/digest/" + previousDate + ".html\">&laquo; Yesterday</a>"
            : "<span class=\"disabled\">&laquo; Yesterday</span>";
        String nextLink = nextDate != null
            ? "<a href=\"/digest/" + nextDate + ".html\">Tomorrow &raquo;</a>"
            : "<span class=\"disabled\">Tomorrow &raquo;</span>";

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AI Coffee Newsletter - %s</title>
                <link rel="stylesheet" href="/assets/style.css">
            </head>
            <body>
                <header>
                    <h1>AI Coffee Newsletter</h1>
                    <p class="date">%s</p>
                </header>
                <nav class="pagination">%s<span class="current">%s</span>%s</nav>
                <section class="top-5">
                    <h2>Top 5 Today</h2>
                    %s
                </section>
                <section class="more">
                    <h2>More From Today</h2>
                    %s
                </section>
                <nav class="pagination">%s<span class="current">%s</span>%s</nav>
                <script>const API_BASE = '%s';</script>
                <script src="/assets/feedback.js"></script>
            </body>
            </html>
            """.formatted(
                digest.digestDate(), digest.digestDate(),
                prevLink, digest.digestDate(), nextLink,
                top5Html.toString(),
                moreHtml.toString(),
                prevLink, digest.digestDate(), nextLink,
                apiUrl
            );
    }

    public String generateIndexRedirect(String latestDate) {
        return """
            <!DOCTYPE html>
            <html><head>
            <meta http-equiv="refresh" content="0; url=/digest/%s.html">
            </head><body></body></html>
            """.formatted(latestDate);
    }

    private String renderArticleCard(Digest.DigestEntry entry, String digestDate) {
        return """
            <div class="article-card">
                <h3>%s</h3>
                <p class="source">%s</p>
                <p class="summary">%s</p>
                <a class="link" href="%s" target="_blank">Read original &rarr;</a>
                <div class="feedback-btns">
                    <button data-article-id="%s" data-digest-date="%s" data-feedback="like">&#128077;</button>
                    <button data-article-id="%s" data-digest-date="%s" data-feedback="dislike">&#128078;</button>
                </div>
            </div>
            """.formatted(
                entry.title(), entry.sourceName(), entry.summary(),
                entry.originalUrl(),
                entry.articleId(), digestDate,
                entry.articleId(), digestDate
            );
    }

    private String renderMoreItem(Digest.DigestEntry entry, String digestDate) {
        return """
            <div class="more-item">
                <span><span class="title">%s</span> <span class="meta">- %s - %s</span></span>
                <span>
                    <a class="link" href="%s" target="_blank">&#128279;</a>
                    <button data-article-id="%s" data-digest-date="%s" data-feedback="like">&#128077;</button>
                    <button data-article-id="%s" data-digest-date="%s" data-feedback="dislike">&#128078;</button>
                </span>
            </div>
            """.formatted(
                entry.title(), entry.summary(), entry.sourceName(),
                entry.originalUrl(),
                entry.articleId(), digestDate,
                entry.articleId(), digestDate
            );
    }
}
