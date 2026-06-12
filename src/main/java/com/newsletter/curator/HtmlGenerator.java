package com.newsletter.curator;

import com.newsletter.model.Digest;

public class HtmlGenerator {

    private final String apiUrl;
    private static final String[] ACCENT_COLORS = {"#FF6B6B", "#4ECDC4", "#FFE66D", "#95E1D3", "#A8E6CF"};

    public HtmlGenerator(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String generateDigestPage(Digest digest, String previousDate, String nextDate) {
        StringBuilder top5Html = new StringBuilder();
        for (int i = 0; i < digest.top5().size(); i++) {
            top5Html.append(renderArticleCard(digest.top5().get(i), digest.digestDate(), i));
        }

        StringBuilder moreHtml = new StringBuilder();
        for (Digest.DigestEntry entry : digest.more()) {
            moreHtml.append(renderMoreItem(entry, digest.digestDate()));
        }

        String prevLink = previousDate != null
            ? "<a class=\"nav-link\" href=\"/digest/" + previousDate + ".html\">&laquo; Yesterday</a>"
            : "<span class=\"nav-link disabled\">&laquo; Yesterday</span>";
        String nextLink = nextDate != null
            ? "<a class=\"nav-link\" href=\"/digest/" + nextDate + ".html\">Tomorrow &raquo;</a>"
            : "<span class=\"nav-link disabled\">Tomorrow &raquo;</span>";

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AI Coffee Newsletter - %s</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
                <style>
                    :root {
                        --bg: #FFF8F0;
                        --card-bg: #FFFFFF;
                        --accent-1: #FF6B6B;
                        --accent-2: #4ECDC4;
                        --accent-3: #FFE66D;
                        --accent-4: #95E1D3;
                        --accent-5: #A8E6CF;
                        --text-primary: #2D3436;
                        --text-secondary: #636E72;
                        --shadow: 0 2px 12px rgba(0,0,0,0.08);
                        --shadow-hover: 0 8px 24px rgba(0,0,0,0.12);
                    }
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
                        background: var(--bg);
                        color: var(--text-primary);
                        line-height: 1.6;
                        padding: 40px 20px;
                    }
                    .container {
                        max-width: 720px;
                        margin: 0 auto;
                    }
                    header {
                        text-align: center;
                        margin-bottom: 48px;
                    }
                    header h1 {
                        font-size: 2.5rem;
                        font-weight: 700;
                        margin-bottom: 4px;
                    }
                    header .tagline {
                        font-size: 1.1rem;
                        color: var(--text-secondary);
                    }
                    header .date {
                        display: inline-block;
                        background: var(--accent-2);
                        color: #fff;
                        padding: 4px 14px;
                        border-radius: 20px;
                        font-size: 0.85rem;
                        font-weight: 600;
                        margin-top: 12px;
                    }
                    .pagination {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 32px;
                    }
                    .nav-link {
                        color: var(--accent-1);
                        text-decoration: none;
                        font-weight: 600;
                        font-size: 0.9rem;
                        padding: 8px 16px;
                        border-radius: 8px;
                        transition: background 0.2s;
                    }
                    .nav-link:hover { background: rgba(255,107,107,0.1); }
                    .nav-link.disabled { color: #ccc; pointer-events: none; }
                    .section-title {
                        font-size: 1.4rem;
                        font-weight: 700;
                        margin-bottom: 20px;
                        padding-left: 4px;
                    }
                    .article-card {
                        background: var(--card-bg);
                        border-radius: 14px;
                        padding: 24px;
                        margin-bottom: 18px;
                        box-shadow: var(--shadow);
                        border-left: 5px solid var(--accent-1);
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    .article-card:hover {
                        transform: translateY(-3px);
                        box-shadow: var(--shadow-hover);
                    }
                    .article-card h3 {
                        font-size: 1.15rem;
                        font-weight: 600;
                        margin-bottom: 8px;
                        color: var(--text-primary);
                    }
                    .article-card .meta {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        margin-bottom: 10px;
                    }
                    .source-badge {
                        background: #F0F0F0;
                        color: var(--text-secondary);
                        padding: 3px 10px;
                        border-radius: 12px;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }
                    .score-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 3px;
                        background: var(--accent-2);
                        color: #fff;
                        padding: 3px 10px;
                        border-radius: 12px;
                        font-size: 0.75rem;
                        font-weight: 700;
                    }
                    .article-card .summary {
                        color: var(--text-secondary);
                        font-size: 0.95rem;
                        margin-bottom: 14px;
                        line-height: 1.7;
                    }
                    .article-card .actions {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                    }
                    .read-link {
                        color: var(--accent-1);
                        text-decoration: none;
                        font-weight: 600;
                        font-size: 0.9rem;
                        transition: opacity 0.2s;
                    }
                    .read-link:hover { opacity: 0.7; }
                    .feedback-btns {
                        display: flex;
                        gap: 8px;
                    }
                    .feedback-btns button {
                        background: none;
                        border: 2px solid #eee;
                        border-radius: 50%%;
                        width: 38px;
                        height: 38px;
                        font-size: 1.1rem;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .feedback-btns button:hover {
                        border-color: var(--accent-2);
                        transform: scale(1.15);
                    }
                    .more-section { margin-top: 40px; }
                    .more-item {
                        background: var(--card-bg);
                        border-radius: 10px;
                        padding: 16px 20px;
                        margin-bottom: 10px;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.05);
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        gap: 12px;
                        transition: transform 0.15s;
                    }
                    .more-item:hover { transform: translateX(4px); }
                    .more-item .title {
                        font-weight: 600;
                        font-size: 0.95rem;
                    }
                    .more-item .meta-text {
                        color: var(--text-secondary);
                        font-size: 0.8rem;
                    }
                    .more-item .actions {
                        display: flex;
                        align-items: center;
                        gap: 6px;
                        flex-shrink: 0;
                    }
                    .more-item .actions a {
                        font-size: 1.1rem;
                        text-decoration: none;
                    }
                    .more-item .actions button {
                        background: none;
                        border: none;
                        font-size: 1rem;
                        cursor: pointer;
                        opacity: 0.6;
                        transition: opacity 0.2s, transform 0.2s;
                    }
                    .more-item .actions button:hover {
                        opacity: 1;
                        transform: scale(1.2);
                    }
                    footer {
                        text-align: center;
                        margin-top: 60px;
                        color: var(--text-secondary);
                        font-size: 0.85rem;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>AI Coffee Newsletter</h1>
                        <p class="tagline">Your daily dose of AI news, curated by Claude</p>
                        <span class="date">%s</span>
                    </header>
                    <nav class="pagination">%s<span></span>%s</nav>
                    <section>
                        <h2 class="section-title">Top 5 Today</h2>
                        %s
                    </section>
                    <section class="more-section">
                        <h2 class="section-title">More From Today</h2>
                        %s
                    </section>
                    <nav class="pagination">%s<span></span>%s</nav>
                    <footer>
                        <p>Curated with Claude &middot; Built with Java + AWS Lambda</p>
                    </footer>
                </div>
                <script>const API_BASE = '%s';</script>
                <script>
                    document.querySelectorAll('[data-feedback]').forEach(btn => {
                        btn.addEventListener('click', async () => {
                            const {articleId, digestDate, feedback} = btn.dataset;
                            try {
                                await fetch(API_BASE + '/feedback', {
                                    method: 'POST',
                                    headers: {'Content-Type': 'application/json'},
                                    body: JSON.stringify({article_id: articleId, digest_date: digestDate, feedback})
                                });
                                btn.style.transform = 'scale(1.3)';
                                setTimeout(() => btn.style.transform = '', 300);
                            } catch(e) {}
                        });
                    });
                </script>
            </body>
            </html>
            """.formatted(
                digest.digestDate(), digest.digestDate(),
                prevLink, nextLink,
                top5Html.toString(),
                moreHtml.toString(),
                prevLink, nextLink,
                apiUrl != null ? apiUrl : ""
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

    private String renderArticleCard(Digest.DigestEntry entry, String digestDate, int index) {
        String accent = ACCENT_COLORS[index % ACCENT_COLORS.length];
        return """
            <div class="article-card" style="border-left-color: %s">
                <h3>%s</h3>
                <div class="meta">
                    <span class="source-badge">%s</span>
                    <span class="score-badge">%d/10</span>
                </div>
                <p class="summary">%s</p>
                <div class="actions">
                    <a class="read-link" href="%s" target="_blank">Read original &rarr;</a>
                    <div class="feedback-btns">
                        <button data-article-id="%s" data-digest-date="%s" data-feedback="like">&#128077;</button>
                        <button data-article-id="%s" data-digest-date="%s" data-feedback="dislike">&#128078;</button>
                    </div>
                </div>
            </div>
            """.formatted(
                accent,
                escapeHtml(entry.title()), escapeHtml(entry.sourceName()),
                entry.score(),
                escapeHtml(entry.summary()),
                entry.originalUrl(),
                entry.articleId(), digestDate,
                entry.articleId(), digestDate
            );
    }

    private String renderMoreItem(Digest.DigestEntry entry, String digestDate) {
        return """
            <div class="more-item">
                <div>
                    <span class="title">%s</span>
                    <div class="meta-text">%s &middot; Score: %d/10</div>
                </div>
                <div class="actions">
                    <a href="%s" target="_blank">&#128279;</a>
                    <button data-article-id="%s" data-digest-date="%s" data-feedback="like">&#128077;</button>
                    <button data-article-id="%s" data-digest-date="%s" data-feedback="dislike">&#128078;</button>
                </div>
            </div>
            """.formatted(
                escapeHtml(entry.title()), escapeHtml(entry.sourceName()), entry.score(),
                entry.originalUrl(),
                entry.articleId(), digestDate,
                entry.articleId(), digestDate
            );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
