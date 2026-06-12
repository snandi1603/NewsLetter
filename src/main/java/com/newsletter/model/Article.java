package com.newsletter.model;

import java.util.List;

public record Article(
    String articleId,
    String fetchDate,
    String sourceName,
    String sourceType,
    String originalUrl,
    String title,
    String rawContent,
    String summary,
    int relevanceScore,
    List<String> tags,
    String publishedAt,
    String createdAt
) {
    public static String generateId(String url) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
