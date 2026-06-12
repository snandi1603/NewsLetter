package com.newsletter.model;

import java.util.List;

public record Feedback(
    String articleId,
    String digestDate,
    String feedback,
    String sourceName,
    List<String> tags,
    String createdAt
) {}
