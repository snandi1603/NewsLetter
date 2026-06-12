package com.newsletter.model;

import java.util.List;

public record Digest(
    String digestDate,
    List<DigestEntry> top5,
    List<DigestEntry> more,
    String generatedAt
) {
    public record DigestEntry(
        String articleId,
        String title,
        String summary,
        String sourceName,
        String originalUrl,
        int score
    ) {}
}
