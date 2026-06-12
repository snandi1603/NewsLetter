# AI Coffee Newsletter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a serverless daily AI newsletter that fetches, curates, and serves AI news via a static site with API backend.

**Architecture:** Three Java 21 Lambda functions (Fetcher, Curator, API) orchestrated by EventBridge, storing data in DynamoDB, serving static HTML from S3 behind CloudFront. Claude Sonnet ranks and summarizes articles.

**Tech Stack:** Java 21, Gradle, AWS CDK (Java), AWS SDK v2, Rome (RSS), JSoup (scraping), Anthropic Java SDK, Jackson, JUnit 5, LocalStack.

---

## File Structure

```
NewsLetter/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── sources.yaml                          # Source config (also deployed to S3)
├── cdk/
│   ├── build.gradle
│   └── src/main/java/com/newsletter/cdk/
│       └── NewsletterStack.java          # All AWS infra
├── src/
│   ├── main/java/com/newsletter/
│   │   ├── model/
│   │   │   ├── Article.java              # Article data class
│   │   │   ├── Digest.java              # Digest data class
│   │   │   ├── Feedback.java            # Feedback data class
│   │   │   └── SourceConfig.java        # Parsed sources.yaml
│   │   ├── fetcher/
│   │   │   ├── FetcherLambda.java       # Lambda handler entry point
│   │   │   ├── FetcherService.java      # Orchestrates all fetchers
│   │   │   ├── RssFetcher.java          # RSS/Atom feed parser
│   │   │   ├── TwitterFetcher.java      # Public tweet scraper
│   │   │   └── SourceConfigLoader.java  # Reads sources.yaml from S3
│   │   ├── curator/
│   │   │   ├── CuratorLambda.java       # Lambda handler entry point
│   │   │   ├── CuratorService.java      # Orchestrates curation
│   │   │   ├── ClaudeClient.java        # Anthropic SDK wrapper
│   │   │   └── HtmlGenerator.java       # Generates static HTML pages
│   │   ├── api/
│   │   │   ├── ApiLambda.java           # Lambda handler entry point
│   │   │   └── ApiRouter.java           # Routes GET/POST requests
│   │   └── common/
│   │       ├── DynamoDbClient.java      # Shared DynamoDB operations
│   │       └── S3Client.java            # Shared S3 operations
│   └── test/java/com/newsletter/
│       ├── fetcher/
│       │   ├── RssFetcherTest.java
│       │   ├── TwitterFetcherTest.java
│       │   └── FetcherServiceTest.java
│       ├── curator/
│       │   ├── ClaudeClientTest.java
│       │   ├── CuratorServiceTest.java
│       │   └── HtmlGeneratorTest.java
│       └── api/
│           └── ApiRouterTest.java
└── frontend/
    ├── template.html                     # HTML template for digest pages
    ├── style.css
    └── feedback.js                       # Like/dislike button logic
```

---

### Task 1: Project Scaffolding & Gradle Setup

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `gradle.properties`

- [ ] **Step 1: Initialize Gradle project**

```bash
cd "/Users/snandi/Google Drive/My Drive/Cloud-Learning/NewsLetter"
gradle init --type java-library --dsl groovy --test-framework junit-jupiter --java-version 21
```

- [ ] **Step 2: Replace build.gradle with project dependencies**

```groovy
plugins {
    id 'java'
}

group = 'com.newsletter'
version = '1.0.0'
sourceCompatibility = JavaVersion.VERSION_21
targetCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencies {
    // AWS SDK v2
    implementation platform('software.amazon.awssdk:bom:2.25.60')
    implementation 'software.amazon.awssdk:dynamodb'
    implementation 'software.amazon.awssdk:s3'
    implementation 'software.amazon.awssdk:dynamodb-enhanced'

    // AWS Lambda
    implementation 'com.amazonaws:aws-lambda-java-core:1.2.3'
    implementation 'com.amazonaws:aws-lambda-java-events:3.11.4'

    // RSS parsing
    implementation 'com.rometools:rome:2.1.0'

    // HTML scraping
    implementation 'org.jsoup:jsoup:1.17.2'

    // Anthropic SDK
    implementation 'com.anthropic:anthropic-java:1.2.0'

    // JSON
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0'

    // Logging
    implementation 'org.slf4j:slf4j-api:2.0.12'
    implementation 'org.slf4j:slf4j-simple:2.0.12'

    // Testing
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation 'org.mockito:mockito-core:5.11.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.11.0'
}

test {
    useJUnitPlatform()
}

task packageFetcher(type: Zip) {
    from compileJava
    from processResources
    into('lib') { from configurations.runtimeClasspath }
    archiveFileName = 'fetcher-lambda.zip'
}

task packageCurator(type: Zip) {
    from compileJava
    from processResources
    into('lib') { from configurations.runtimeClasspath }
    archiveFileName = 'curator-lambda.zip'
}

task packageApi(type: Zip) {
    from compileJava
    from processResources
    into('lib') { from configurations.runtimeClasspath }
    archiveFileName = 'api-lambda.zip'
}
```

- [ ] **Step 3: Create settings.gradle**

```groovy
rootProject.name = 'ai-coffee-newsletter'
```

- [ ] **Step 4: Create directory structure**

```bash
mkdir -p src/main/java/com/newsletter/{model,fetcher,curator,api,common}
mkdir -p src/test/java/com/newsletter/{fetcher,curator,api}
mkdir -p frontend
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Initialize git and commit**

```bash
git init
echo ".gradle/\nbuild/\n.idea/\n*.iml" > .gitignore
git add .
git commit -m "feat: scaffold Java 21 Gradle project with dependencies"
```

---

### Task 2: Data Models

**Files:**
- Create: `src/main/java/com/newsletter/model/Article.java`
- Create: `src/main/java/com/newsletter/model/Digest.java`
- Create: `src/main/java/com/newsletter/model/Feedback.java`
- Create: `src/main/java/com/newsletter/model/SourceConfig.java`
- Test: `src/test/java/com/newsletter/model/SourceConfigTest.java`

- [ ] **Step 1: Write SourceConfig test**

```java
package com.newsletter.model;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceConfigTest {

    @Test
    void parsesYamlConfig() throws Exception {
        String yaml = """
            rss:
              - name: Anthropic
                url: https://anthropic.com/blog/rss
                type: company_blog
            twitter:
              - handle: karpathy
            substack:
              - name: AI News
                url: https://ainews.substack.com/feed
            medium:
              - name: Towards AI
                url: https://pub.towardsai.net/feed
            """;

        YAMLMapper mapper = new YAMLMapper();
        SourceConfig config = mapper.readValue(yaml, SourceConfig.class);

        assertEquals(1, config.rss().size());
        assertEquals("Anthropic", config.rss().get(0).name());
        assertEquals(1, config.twitter().size());
        assertEquals("karpathy", config.twitter().get(0).handle());
        assertEquals(1, config.substack().size());
        assertEquals(1, config.medium().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.model.SourceConfigTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement model classes**

```java
// Article.java
package com.newsletter.model;

import java.time.Instant;
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
```

```java
// Digest.java
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
```

```java
// Feedback.java
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
```

```java
// SourceConfig.java
package com.newsletter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceConfig(
    List<RssSource> rss,
    List<TwitterSource> twitter,
    List<FeedSource> substack,
    List<FeedSource> medium
) {
    public record RssSource(String name, String url, String type) {}
    public record TwitterSource(String handle) {}
    public record FeedSource(String name, String url) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.model.SourceConfigTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add data model records (Article, Digest, Feedback, SourceConfig)"
```

---

### Task 3: Source Config Loader

**Files:**
- Create: `src/main/java/com/newsletter/fetcher/SourceConfigLoader.java`
- Create: `sources.yaml`
- Test: `src/test/java/com/newsletter/fetcher/SourceConfigLoaderTest.java`

- [ ] **Step 1: Write test for loading config from file**

```java
package com.newsletter.fetcher;

import com.newsletter.model.SourceConfig;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SourceConfigLoaderTest {

    @Test
    void loadsConfigFromYamlString() throws Exception {
        String yaml = """
            rss:
              - name: Anthropic
                url: https://anthropic.com/blog/rss
                type: company_blog
              - name: OpenAI
                url: https://openai.com/blog/rss
                type: company_blog
            twitter:
              - handle: karpathy
              - handle: ylecun
            substack:
              - name: AI News
                url: https://ainews.substack.com/feed
            medium:
              - name: Towards AI
                url: https://pub.towardsai.net/feed
            """;

        SourceConfigLoader loader = new SourceConfigLoader();
        SourceConfig config = loader.parseYaml(yaml);

        assertEquals(2, config.rss().size());
        assertEquals("OpenAI", config.rss().get(1).name());
        assertEquals(2, config.twitter().size());
        assertEquals("ylecun", config.twitter().get(1).handle());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.fetcher.SourceConfigLoaderTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement SourceConfigLoader**

```java
package com.newsletter.fetcher;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.newsletter.model.SourceConfig;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SourceConfigLoader {

    private final YAMLMapper yamlMapper = new YAMLMapper();

    public SourceConfig parseYaml(String yaml) throws Exception {
        return yamlMapper.readValue(yaml, SourceConfig.class);
    }

    public SourceConfig loadFromS3(S3Client s3, String bucket, String key) throws Exception {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        try (InputStream is = s3.getObject(request)) {
            String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parseYaml(yaml);
        }
    }
}
```

- [ ] **Step 4: Create sources.yaml**

```yaml
rss:
  - name: Anthropic
    url: https://www.anthropic.com/rss.xml
    type: company_blog
  - name: OpenAI
    url: https://openai.com/blog/rss.xml
    type: company_blog
  - name: Google AI
    url: https://blog.google/technology/ai/rss
    type: company_blog
  - name: DeepMind
    url: https://deepmind.google/blog/rss.xml
    type: company_blog
  - name: XAI
    url: https://x.ai/blog/rss
    type: company_blog
  - name: Cursor
    url: https://www.cursor.com/blog/rss.xml
    type: company_blog
  - name: Kiro
    url: https://kiro.dev/blog/rss
    type: company_blog

substack:
  - name: The Batch (Andrew Ng)
    url: https://www.deeplearning.ai/the-batch/feed

medium:
  - name: Towards AI
    url: https://pub.towardsai.net/feed

twitter:
  - handle: karpathy
  - handle: ylecun
  - handle: jimfan
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.fetcher.SourceConfigLoaderTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/ sources.yaml
git commit -m "feat: add SourceConfigLoader with S3 and local YAML parsing"
```

---

### Task 4: RSS Fetcher

**Files:**
- Create: `src/main/java/com/newsletter/fetcher/RssFetcher.java`
- Test: `src/test/java/com/newsletter/fetcher/RssFetcherTest.java`
- Create: `src/test/resources/sample-rss.xml`

- [ ] **Step 1: Create sample RSS feed for testing**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Anthropic Blog</title>
    <link>https://anthropic.com/blog</link>
    <item>
      <title>Introducing Claude 4</title>
      <link>https://anthropic.com/blog/claude-4</link>
      <description>We are excited to announce Claude 4, our most capable model yet.</description>
      <pubDate>Thu, 12 Jun 2026 10:00:00 GMT</pubDate>
    </item>
    <item>
      <title>Safety Research Update</title>
      <link>https://anthropic.com/blog/safety-update</link>
      <description>Our latest research on constitutional AI methods.</description>
      <pubDate>Wed, 11 Jun 2026 09:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>
```

- [ ] **Step 2: Write RssFetcher test**

```java
package com.newsletter.fetcher;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RssFetcherTest {

    @Test
    void parsesRssFeedIntoArticles() throws Exception {
        InputStream rssStream = getClass().getResourceAsStream("/sample-rss.xml");
        RssFetcher fetcher = new RssFetcher();

        List<Article> articles = fetcher.parseFromStream(rssStream, "Anthropic", "rss");

        assertEquals(2, articles.size());
        assertEquals("Introducing Claude 4", articles.get(0).title());
        assertEquals("https://anthropic.com/blog/claude-4", articles.get(0).originalUrl());
        assertEquals("Anthropic", articles.get(0).sourceName());
        assertEquals("rss", articles.get(0).sourceType());
        assertNotNull(articles.get(0).articleId());
    }

    @Test
    void generatesConsistentArticleId() throws Exception {
        InputStream rssStream = getClass().getResourceAsStream("/sample-rss.xml");
        RssFetcher fetcher = new RssFetcher();

        List<Article> first = fetcher.parseFromStream(rssStream, "Anthropic", "rss");
        rssStream = getClass().getResourceAsStream("/sample-rss.xml");
        List<Article> second = fetcher.parseFromStream(rssStream, "Anthropic", "rss");

        assertEquals(first.get(0).articleId(), second.get(0).articleId());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.fetcher.RssFetcherTest"`
Expected: FAIL — class not found

- [ ] **Step 4: Implement RssFetcher**

```java
package com.newsletter.fetcher;

import com.newsletter.model.Article;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RssFetcher {

    public List<Article> fetchFromUrl(String url, String sourceName, String sourceType) {
        try (InputStream stream = URI.create(url).toURL().openStream()) {
            return parseFromStream(stream, sourceName, sourceType);
        } catch (Exception e) {
            System.err.println("Failed to fetch RSS from " + url + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<Article> parseFromStream(InputStream stream, String sourceName, String sourceType) throws Exception {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(stream));

        List<Article> articles = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (SyndEntry entry : feed.getEntries()) {
            String url = entry.getLink();
            String title = entry.getTitle();
            String content = entry.getDescription() != null ? entry.getDescription().getValue() : "";
            String publishedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant().toString()
                : Instant.now().toString();

            articles.add(new Article(
                Article.generateId(url),
                today,
                sourceName,
                sourceType,
                url,
                title,
                content,
                null,
                0,
                List.of(),
                publishedAt,
                Instant.now().toString()
            ));
        }
        return articles;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.fetcher.RssFetcherTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: add RssFetcher with Rome RSS parsing"
```

---

### Task 5: Twitter Fetcher

**Files:**
- Create: `src/main/java/com/newsletter/fetcher/TwitterFetcher.java`
- Test: `src/test/java/com/newsletter/fetcher/TwitterFetcherTest.java`
- Create: `src/test/resources/sample-twitter.html`

- [ ] **Step 1: Create sample Twitter HTML for testing**

```html
<html>
<body>
<article data-testid="tweet">
  <div data-testid="tweetText">Just published a new blog post on scaling laws for neural networks. Key insight: compute-optimal training requires scaling data proportionally with model size. https://t.co/abc123</div>
  <time datetime="2026-06-12T08:30:00.000Z">Jun 12</time>
  <a href="/karpathy/status/123456789">link</a>
</article>
</body>
</html>
```

- [ ] **Step 2: Write TwitterFetcher test**

```java
package com.newsletter.fetcher;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TwitterFetcherTest {

    @Test
    void parsesHtmlIntoArticles() {
        String html = """
            <html><body>
            <article data-testid="tweet">
              <div data-testid="tweetText">New blog post on scaling laws https://t.co/abc123</div>
              <time datetime="2026-06-12T08:30:00.000Z">Jun 12</time>
              <a href="/karpathy/status/123456789"></a>
            </article>
            </body></html>
            """;

        TwitterFetcher fetcher = new TwitterFetcher();
        List<Article> articles = fetcher.parseHtml(html, "karpathy");

        assertEquals(1, articles.size());
        assertTrue(articles.get(0).rawContent().contains("scaling laws"));
        assertEquals("@karpathy", articles.get(0).sourceName());
        assertEquals("twitter", articles.get(0).sourceType());
    }

    @Test
    void returnsEmptyOnParseFailure() {
        TwitterFetcher fetcher = new TwitterFetcher();
        List<Article> articles = fetcher.parseHtml("<html></html>", "karpathy");

        assertTrue(articles.isEmpty());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.fetcher.TwitterFetcherTest"`
Expected: FAIL — class not found

- [ ] **Step 4: Implement TwitterFetcher**

```java
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

public class TwitterFetcher {

    private static final String NITTER_BASE = "https://nitter.net";

    public List<Article> fetchTweets(String handle) {
        try {
            String url = NITTER_BASE + "/" + handle;
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
            return parseNitterHtml(doc.html(), handle);
        } catch (Exception e) {
            System.err.println("Failed to fetch tweets for @" + handle + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<Article> parseHtml(String html, String handle) {
        Document doc = Jsoup.parse(html);
        Elements tweets = doc.select("article[data-testid=tweet]");

        if (tweets.isEmpty()) {
            return parseNitterHtml(html, handle);
        }

        List<Article> articles = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (Element tweet : tweets) {
            Element textEl = tweet.selectFirst("[data-testid=tweetText]");
            Element timeEl = tweet.selectFirst("time");
            Element linkEl = tweet.selectFirst("a[href*=status]");

            if (textEl == null) continue;

            String content = textEl.text();
            String tweetUrl = linkEl != null
                ? "https://x.com" + linkEl.attr("href")
                : "https://x.com/" + handle;
            String publishedAt = timeEl != null
                ? timeEl.attr("datetime")
                : Instant.now().toString();

            articles.add(new Article(
                Article.generateId(tweetUrl),
                today,
                "@" + handle,
                "twitter",
                tweetUrl,
                content.length() > 80 ? content.substring(0, 80) + "..." : content,
                content,
                null,
                0,
                List.of(),
                publishedAt,
                Instant.now().toString()
            ));
        }
        return articles;
    }

    private List<Article> parseNitterHtml(String html, String handle) {
        Document doc = Jsoup.parse(html);
        Elements tweets = doc.select(".timeline-item .tweet-content");

        List<Article> articles = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (Element tweet : tweets) {
            String content = tweet.text();
            if (content.isBlank()) continue;

            String tweetUrl = "https://x.com/" + handle + "/status/" + System.nanoTime();

            articles.add(new Article(
                Article.generateId(content),
                today,
                "@" + handle,
                "twitter",
                tweetUrl,
                content.length() > 80 ? content.substring(0, 80) + "..." : content,
                content,
                null,
                0,
                List.of(),
                publishedAt(doc, tweet),
                Instant.now().toString()
            ));
        }
        return articles;
    }

    private String publishedAt(Document doc, Element tweet) {
        Element time = tweet.parent() != null ? tweet.parent().selectFirst(".tweet-date a") : null;
        return time != null ? time.attr("title") : Instant.now().toString();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.fetcher.TwitterFetcherTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: add TwitterFetcher with Nitter fallback scraping"
```

---

### Task 6: DynamoDB Client

**Files:**
- Create: `src/main/java/com/newsletter/common/DynamoDbClient.java`
- Test: `src/test/java/com/newsletter/common/DynamoDbClientTest.java`

- [ ] **Step 1: Write DynamoDbClient test**

```java
package com.newsletter.common;

import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient as AwsDynamoClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamoDbClientTest {

    @Mock
    private AwsDynamoClient mockDynamo;

    private DynamoDbRepository repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new DynamoDbRepository(mockDynamo, "articles", "digests", "feedback");
    }

    @Test
    void convertsArticleToItem() {
        Article article = new Article(
            "abc123", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/test", "Test Article",
            "Content here", null, 0, List.of(),
            "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"
        );

        Map<String, AttributeValue> item = repository.articleToItem(article);

        assertEquals("abc123", item.get("article_id").s());
        assertEquals("2026-06-12", item.get("fetch_date").s());
        assertEquals("Anthropic", item.get("source_name").s());
    }

    @Test
    void checksDuplicateByArticleId() {
        when(mockDynamo.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(Map.of()).build());

        assertTrue(repository.exists("abc123", "2026-06-12"));
    }

    @Test
    void returnsNotExistsWhenEmpty() {
        when(mockDynamo.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().build());

        assertFalse(repository.exists("abc123", "2026-06-12"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.common.DynamoDbClientTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement DynamoDbRepository**

```java
package com.newsletter.common;

import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;

public class DynamoDbRepository {

    private final DynamoDbClient dynamo;
    private final String articlesTable;
    private final String digestsTable;
    private final String feedbackTable;
    private final ObjectMapper mapper = new ObjectMapper();

    public DynamoDbRepository(DynamoDbClient dynamo, String articlesTable, String digestsTable, String feedbackTable) {
        this.dynamo = dynamo;
        this.articlesTable = articlesTable;
        this.digestsTable = digestsTable;
        this.feedbackTable = feedbackTable;
    }

    public boolean exists(String articleId, String fetchDate) {
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder()
            .tableName(articlesTable)
            .key(Map.of(
                "article_id", AttributeValue.builder().s(articleId).build(),
                "fetch_date", AttributeValue.builder().s(fetchDate).build()
            ))
            .build());
        return response.hasItem() && !response.item().isEmpty();
    }

    public void saveArticle(Article article) {
        dynamo.putItem(PutItemRequest.builder()
            .tableName(articlesTable)
            .item(articleToItem(article))
            .build());
    }

    public void saveDigest(Digest digest) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("digest_date", AttributeValue.builder().s(digest.digestDate()).build());
            item.put("top_5", AttributeValue.builder().s(mapper.writeValueAsString(digest.top5())).build());
            item.put("more", AttributeValue.builder().s(mapper.writeValueAsString(digest.more())).build());
            item.put("generated_at", AttributeValue.builder().s(digest.generatedAt()).build());
            dynamo.putItem(PutItemRequest.builder().tableName(digestsTable).item(item).build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save digest", e);
        }
    }

    public void saveFeedback(Feedback feedback) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("article_id", AttributeValue.builder().s(feedback.articleId()).build());
        item.put("digest_date", AttributeValue.builder().s(feedback.digestDate()).build());
        item.put("feedback", AttributeValue.builder().s(feedback.feedback()).build());
        item.put("source_name", AttributeValue.builder().s(feedback.sourceName()).build());
        item.put("tags", AttributeValue.builder().ss(feedback.tags()).build());
        item.put("created_at", AttributeValue.builder().s(feedback.createdAt()).build());
        dynamo.putItem(PutItemRequest.builder().tableName(feedbackTable).item(item).build());
    }

    public List<Article> getArticlesByDate(String fetchDate) {
        QueryResponse response = dynamo.query(QueryRequest.builder()
            .tableName(articlesTable)
            .indexName("fetch_date-index")
            .keyConditionExpression("fetch_date = :date")
            .expressionAttributeValues(Map.of(
                ":date", AttributeValue.builder().s(fetchDate).build()
            ))
            .build());
        return response.items().stream().map(this::itemToArticle).collect(Collectors.toList());
    }

    public Optional<Digest> getDigest(String date) {
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder()
            .tableName(digestsTable)
            .key(Map.of("digest_date", AttributeValue.builder().s(date).build()))
            .build());
        if (!response.hasItem() || response.item().isEmpty()) return Optional.empty();
        return Optional.of(itemToDigest(response.item()));
    }

    public Map<String, AttributeValue> articleToItem(Article article) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("article_id", AttributeValue.builder().s(article.articleId()).build());
        item.put("fetch_date", AttributeValue.builder().s(article.fetchDate()).build());
        item.put("source_name", AttributeValue.builder().s(article.sourceName()).build());
        item.put("source_type", AttributeValue.builder().s(article.sourceType()).build());
        item.put("original_url", AttributeValue.builder().s(article.originalUrl()).build());
        item.put("title", AttributeValue.builder().s(article.title()).build());
        item.put("raw_content", AttributeValue.builder().s(article.rawContent() != null ? article.rawContent() : "").build());
        if (article.summary() != null) item.put("summary", AttributeValue.builder().s(article.summary()).build());
        item.put("relevance_score", AttributeValue.builder().n(String.valueOf(article.relevanceScore())).build());
        if (article.tags() != null && !article.tags().isEmpty()) item.put("tags", AttributeValue.builder().ss(article.tags()).build());
        item.put("published_at", AttributeValue.builder().s(article.publishedAt()).build());
        item.put("created_at", AttributeValue.builder().s(article.createdAt()).build());
        return item;
    }

    private Article itemToArticle(Map<String, AttributeValue> item) {
        return new Article(
            item.get("article_id").s(),
            item.get("fetch_date").s(),
            item.get("source_name").s(),
            item.get("source_type").s(),
            item.get("original_url").s(),
            item.get("title").s(),
            item.containsKey("raw_content") ? item.get("raw_content").s() : "",
            item.containsKey("summary") ? item.get("summary").s() : null,
            item.containsKey("relevance_score") ? Integer.parseInt(item.get("relevance_score").n()) : 0,
            item.containsKey("tags") ? item.get("tags").ss() : List.of(),
            item.get("published_at").s(),
            item.get("created_at").s()
        );
    }

    private Digest itemToDigest(Map<String, AttributeValue> item) {
        try {
            String digestDate = item.get("digest_date").s();
            List<Digest.DigestEntry> top5 = mapper.readValue(item.get("top_5").s(),
                mapper.getTypeFactory().constructCollectionType(List.class, Digest.DigestEntry.class));
            List<Digest.DigestEntry> more = mapper.readValue(item.get("more").s(),
                mapper.getTypeFactory().constructCollectionType(List.class, Digest.DigestEntry.class));
            String generatedAt = item.get("generated_at").s();
            return new Digest(digestDate, top5, more, generatedAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse digest", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.common.DynamoDbClientTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add DynamoDbRepository for articles, digests, and feedback"
```

---

### Task 7: Fetcher Service (Orchestrator)

**Files:**
- Create: `src/main/java/com/newsletter/fetcher/FetcherService.java`
- Test: `src/test/java/com/newsletter/fetcher/FetcherServiceTest.java`

- [ ] **Step 1: Write FetcherService test**

```java
package com.newsletter.fetcher;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.SourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FetcherServiceTest {

    @Mock private DynamoDbRepository repository;
    @Mock private RssFetcher rssFetcher;
    @Mock private TwitterFetcher twitterFetcher;

    private FetcherService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FetcherService(repository, rssFetcher, twitterFetcher);
    }

    @Test
    void fetchesFromAllSourcesAndSavesNew() {
        SourceConfig config = new SourceConfig(
            List.of(new SourceConfig.RssSource("Anthropic", "https://anthropic.com/rss", "company_blog")),
            List.of(new SourceConfig.TwitterSource("karpathy")),
            List.of(),
            List.of()
        );

        Article rssArticle = new Article("id1", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/post", "Post Title", "Content",
            null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z");
        Article twitterArticle = new Article("id2", "2026-06-12", "@karpathy", "twitter",
            "https://x.com/karpathy/status/1", "Tweet...", "Tweet content",
            null, 0, List.of(), "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z");

        when(rssFetcher.fetchFromUrl(anyString(), eq("Anthropic"), eq("rss")))
            .thenReturn(List.of(rssArticle));
        when(twitterFetcher.fetchTweets("karpathy"))
            .thenReturn(List.of(twitterArticle));
        when(repository.exists(anyString(), anyString())).thenReturn(false);

        int saved = service.fetchAll(config);

        assertEquals(2, saved);
        verify(repository, times(2)).saveArticle(any());
    }

    @Test
    void skipsExistingArticles() {
        SourceConfig config = new SourceConfig(
            List.of(new SourceConfig.RssSource("Anthropic", "https://anthropic.com/rss", "company_blog")),
            List.of(), List.of(), List.of()
        );

        Article article = new Article("id1", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/post", "Post", "Content",
            null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z");

        when(rssFetcher.fetchFromUrl(anyString(), anyString(), anyString()))
            .thenReturn(List.of(article));
        when(repository.exists("id1", "2026-06-12")).thenReturn(true);

        int saved = service.fetchAll(config);

        assertEquals(0, saved);
        verify(repository, never()).saveArticle(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.fetcher.FetcherServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement FetcherService**

```java
package com.newsletter.fetcher;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.SourceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FetcherService {

    private final DynamoDbRepository repository;
    private final RssFetcher rssFetcher;
    private final TwitterFetcher twitterFetcher;

    public FetcherService(DynamoDbRepository repository, RssFetcher rssFetcher, TwitterFetcher twitterFetcher) {
        this.repository = repository;
        this.rssFetcher = rssFetcher;
        this.twitterFetcher = twitterFetcher;
    }

    public int fetchAll(SourceConfig config) {
        List<Article> allArticles = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<List<Article>>> futures = new ArrayList<>();

        // RSS sources
        for (var rss : config.rss()) {
            futures.add(executor.submit(() -> rssFetcher.fetchFromUrl(rss.url(), rss.name(), "rss")));
        }

        // Substack (also RSS)
        for (var sub : config.substack()) {
            futures.add(executor.submit(() -> rssFetcher.fetchFromUrl(sub.url(), sub.name(), "substack")));
        }

        // Medium (also RSS)
        for (var med : config.medium()) {
            futures.add(executor.submit(() -> rssFetcher.fetchFromUrl(med.url(), med.name(), "medium")));
        }

        // Twitter
        for (var tw : config.twitter()) {
            futures.add(executor.submit(() -> twitterFetcher.fetchTweets(tw.handle())));
        }

        for (Future<List<Article>> future : futures) {
            try {
                allArticles.addAll(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                System.err.println("Source fetch failed: " + e.getMessage());
            }
        }
        executor.shutdown();

        int savedCount = 0;
        for (Article article : allArticles) {
            if (!repository.exists(article.articleId(), article.fetchDate())) {
                repository.saveArticle(article);
                savedCount++;
            }
        }
        return savedCount;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.fetcher.FetcherServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add FetcherService orchestrating parallel source fetching"
```

---

### Task 8: Fetcher Lambda Handler

**Files:**
- Create: `src/main/java/com/newsletter/fetcher/FetcherLambda.java`

- [ ] **Step 1: Implement FetcherLambda**

```java
package com.newsletter.fetcher;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.SourceConfig;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import java.util.Map;

public class FetcherLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbRepository repository;
    private final SourceConfigLoader configLoader;
    private final FetcherService fetcherService;
    private final S3Client s3;
    private final String bucketName;

    public FetcherLambda() {
        DynamoDbClient dynamo = DynamoDbClient.create();
        this.s3 = S3Client.create();
        this.bucketName = System.getenv("BUCKET_NAME");
        this.repository = new DynamoDbRepository(dynamo,
            System.getenv("ARTICLES_TABLE"),
            System.getenv("DIGESTS_TABLE"),
            System.getenv("FEEDBACK_TABLE"));
        this.configLoader = new SourceConfigLoader();
        this.fetcherService = new FetcherService(repository, new RssFetcher(), new TwitterFetcher());
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            SourceConfig config = configLoader.loadFromS3(s3, bucketName, "sources.yaml");
            int saved = fetcherService.fetchAll(config);
            context.getLogger().log("Fetched and saved " + saved + " new articles");
            return Map.of("statusCode", 200, "saved", saved);
        } catch (Exception e) {
            context.getLogger().log("Fetcher failed: " + e.getMessage());
            return Map.of("statusCode", 500, "error", e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "feat: add FetcherLambda handler wiring up config, fetchers, and DynamoDB"
```

---

### Task 9: Claude Client (Anthropic SDK)

**Files:**
- Create: `src/main/java/com/newsletter/curator/ClaudeClient.java`
- Test: `src/test/java/com/newsletter/curator/ClaudeClientTest.java`

- [ ] **Step 1: Write ClaudeClient test**

```java
package com.newsletter.curator;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClaudeClientTest {

    @Test
    void buildsCurationPromptCorrectly() {
        List<Article> articles = List.of(
            new Article("id1", "2026-06-12", "Anthropic", "rss",
                "https://anthropic.com/blog/test", "Claude 4 Released",
                "We are excited to announce Claude 4.", null, 0, List.of(),
                "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"),
            new Article("id2", "2026-06-12", "@karpathy", "twitter",
                "https://x.com/karpathy/1", "Scaling laws insight...",
                "Key insight on compute-optimal training.", null, 0, List.of(),
                "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z")
        );

        ClaudeClient client = new ClaudeClient("fake-key");
        String prompt = client.buildCurationPrompt(articles);

        assertTrue(prompt.contains("Claude 4 Released"));
        assertTrue(prompt.contains("Scaling laws insight"));
        assertTrue(prompt.contains("article_id"));
        assertTrue(prompt.contains("Score each 1-10"));
    }

    @Test
    void parsesCurationResponseJson() throws Exception {
        String json = """
            [
              {"article_id": "id1", "score": 9, "summary": "Major release.", "tags": ["LLM", "release"]},
              {"article_id": "id2", "score": 7, "summary": "Training insight.", "tags": ["research"]}
            ]
            """;

        ClaudeClient client = new ClaudeClient("fake-key");
        var results = client.parseCurationResponse(json);

        assertEquals(2, results.size());
        assertEquals(9, results.get(0).score());
        assertEquals("Major release.", results.get(0).summary());
        assertEquals(List.of("LLM", "release"), results.get(0).tags());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.curator.ClaudeClientTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ClaudeClient**

```java
package com.newsletter.curator;

import com.anthropic.AnthropicClient;
import com.anthropic.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsletter.model.Article;
import java.util.List;

public class ClaudeClient {

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public record CurationResult(String articleId, int score, String summary, List<String> tags) {}

    public List<CurationResult> curateArticles(List<Article> articles) throws Exception {
        AnthropicClient client = AnthropicClient.builder()
            .apiKey(apiKey)
            .build();

        String prompt = buildCurationPrompt(articles);

        Message response = client.messages().create(MessageCreateParams.builder()
            .model("claude-sonnet-4-6-20250514")
            .maxTokens(4096)
            .addUserMessage(prompt)
            .build());

        String content = response.content().get(0).text();
        return parseCurationResponse(extractJson(content));
    }

    public String buildCurationPrompt(List<Article> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are an AI research news curator. Given these articles, rank them by:
            1. Significance (breakthrough, major release, or important announcement vs. minor update)
            2. Novelty (new information vs. rehash of known topics)
            3. Breadth of impact (affects the whole AI field vs. niche use case)

            Score each 1-10 and provide a 2-3 sentence summary.
            Ensure diversity of sources in the top 5 when possible.

            Return ONLY a JSON array: [{ "article_id": "...", "score": N, "summary": "...", "tags": ["..."] }]

            Articles:
            """);

        for (Article article : articles) {
            sb.append("\n---\n");
            sb.append("article_id: ").append(article.articleId()).append("\n");
            sb.append("title: ").append(article.title()).append("\n");
            sb.append("source: ").append(article.sourceName()).append("\n");
            sb.append("content: ").append(truncate(article.rawContent(), 2000)).append("\n");
        }
        return sb.toString();
    }

    public List<CurationResult> parseCurationResponse(String json) throws Exception {
        return mapper.readValue(json,
            mapper.getTypeFactory().constructCollectionType(List.class, CurationResult.class));
    }

    private String extractJson(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.curator.ClaudeClientTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add ClaudeClient for article curation with Sonnet"
```

---

### Task 10: HTML Generator

**Files:**
- Create: `src/main/java/com/newsletter/curator/HtmlGenerator.java`
- Create: `frontend/template.html`
- Create: `frontend/style.css`
- Create: `frontend/feedback.js`
- Test: `src/test/java/com/newsletter/curator/HtmlGeneratorTest.java`

- [ ] **Step 1: Create HTML template**

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Coffee Newsletter - {{DATE}}</title>
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body>
    <header>
        <h1>AI Coffee Newsletter</h1>
        <p class="date">{{DATE}}</p>
    </header>
    <nav class="pagination">
        {{PREV_LINK}}
        <span class="current">{{DATE}}</span>
        {{NEXT_LINK}}
    </nav>
    <section class="top-5">
        <h2>Top 5 Today</h2>
        {{TOP_5_CONTENT}}
    </section>
    <section class="more">
        <h2>More From Today</h2>
        {{MORE_CONTENT}}
    </section>
    <nav class="pagination">
        {{PREV_LINK}}
        <span class="current">{{DATE}}</span>
        {{NEXT_LINK}}
    </nav>
    <script src="/assets/feedback.js"></script>
</body>
</html>
```

- [ ] **Step 2: Create CSS**

```css
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; background: #fafafa; color: #333; }
header { text-align: center; margin-bottom: 30px; padding: 20px; background: #1a1a2e; color: white; border-radius: 8px; }
header h1 { font-size: 1.8rem; }
header .date { opacity: 0.8; margin-top: 5px; }
.pagination { display: flex; justify-content: space-between; align-items: center; margin: 20px 0; padding: 10px; }
.pagination a { text-decoration: none; color: #4a90d9; font-weight: bold; }
.pagination a:hover { text-decoration: underline; }
.pagination .disabled { color: #ccc; cursor: default; }
.top-5 { margin-bottom: 40px; }
.top-5 h2 { margin-bottom: 20px; color: #1a1a2e; }
.article-card { background: white; border-radius: 8px; padding: 20px; margin-bottom: 15px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.article-card h3 { margin-bottom: 8px; }
.article-card .source { color: #666; font-size: 0.85rem; margin-bottom: 8px; }
.article-card .summary { line-height: 1.6; margin-bottom: 10px; }
.article-card .link { color: #4a90d9; text-decoration: none; font-weight: 500; }
.article-card .link:hover { text-decoration: underline; }
.feedback-btns { margin-top: 10px; }
.feedback-btns button { border: 1px solid #ddd; background: white; padding: 5px 12px; border-radius: 4px; cursor: pointer; margin-right: 5px; }
.feedback-btns button:hover { background: #f0f0f0; }
.feedback-btns button.active-like { background: #d4edda; border-color: #28a745; }
.feedback-btns button.active-dislike { background: #f8d7da; border-color: #dc3545; }
.more h2 { margin-bottom: 15px; color: #1a1a2e; }
.more-item { padding: 10px 15px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
.more-item:last-child { border-bottom: none; }
.more-item .title { font-weight: 500; }
.more-item .meta { color: #666; font-size: 0.85rem; }
```

- [ ] **Step 3: Create feedback.js**

```javascript
const API_BASE = '{{API_URL}}';

document.querySelectorAll('.feedback-btns button').forEach(btn => {
    btn.addEventListener('click', async function() {
        const articleId = this.dataset.articleId;
        const digestDate = this.dataset.digestDate;
        const feedback = this.dataset.feedback;

        try {
            const response = await fetch(`${API_BASE}/feedback`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ article_id: articleId, digest_date: digestDate, feedback: feedback })
            });

            if (response.ok) {
                const parent = this.parentElement;
                parent.querySelectorAll('button').forEach(b => {
                    b.classList.remove('active-like', 'active-dislike');
                });
                this.classList.add(feedback === 'like' ? 'active-like' : 'active-dislike');
            }
        } catch (e) {
            console.error('Feedback failed:', e);
        }
    });
});
```

- [ ] **Step 4: Write HtmlGenerator test**

```java
package com.newsletter.curator;

import com.newsletter.model.Digest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HtmlGeneratorTest {

    @Test
    void generatesHtmlWithTop5AndMore() {
        List<Digest.DigestEntry> top5 = List.of(
            new Digest.DigestEntry("id1", "Claude 4 Released", "Major new model.", "Anthropic", "https://anthropic.com/blog/claude4", 9),
            new Digest.DigestEntry("id2", "GPT-5 Announced", "OpenAI's next gen.", "OpenAI", "https://openai.com/blog/gpt5", 8)
        );
        List<Digest.DigestEntry> more = List.of(
            new Digest.DigestEntry("id3", "Scaling Laws", "New paper on scaling.", "@karpathy", "https://x.com/karpathy/1", 6)
        );
        Digest digest = new Digest("2026-06-12", top5, more, "2026-06-12T06:30:00Z");

        HtmlGenerator generator = new HtmlGenerator("https://api.example.com");
        String html = generator.generateDigestPage(digest, "2026-06-11", null);

        assertTrue(html.contains("Claude 4 Released"));
        assertTrue(html.contains("GPT-5 Announced"));
        assertTrue(html.contains("Scaling Laws"));
        assertTrue(html.contains("2026-06-12"));
        assertTrue(html.contains("2026-06-11"));
        assertTrue(html.contains("data-article-id=\"id1\""));
        assertFalse(html.contains("Tomorrow"));
    }

    @Test
    void showsTomorrowLinkWhenProvided() {
        Digest digest = new Digest("2026-06-11", List.of(), List.of(), "2026-06-11T06:30:00Z");

        HtmlGenerator generator = new HtmlGenerator("https://api.example.com");
        String html = generator.generateDigestPage(digest, "2026-06-10", "2026-06-12");

        assertTrue(html.contains("2026-06-12"));
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.curator.HtmlGeneratorTest"`
Expected: FAIL — class not found

- [ ] **Step 6: Implement HtmlGenerator**

```java
package com.newsletter.curator;

import com.newsletter.model.Digest;
import java.util.List;

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
                <span><span class="title">%s</span> <span class="meta">— %s — %s</span></span>
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
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.curator.HtmlGeneratorTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/ frontend/
git commit -m "feat: add HtmlGenerator with static page rendering and feedback buttons"
```

---

### Task 11: Curator Service & Lambda

**Files:**
- Create: `src/main/java/com/newsletter/curator/CuratorService.java`
- Create: `src/main/java/com/newsletter/curator/CuratorLambda.java`
- Test: `src/test/java/com/newsletter/curator/CuratorServiceTest.java`

- [ ] **Step 1: Write CuratorService test**

```java
package com.newsletter.curator;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuratorServiceTest {

    @Mock private DynamoDbRepository repository;
    @Mock private ClaudeClient claudeClient;
    @Mock private HtmlGenerator htmlGenerator;

    private CuratorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CuratorService(repository, claudeClient, htmlGenerator);
    }

    @Test
    void curatesArticlesIntoDigest() throws Exception {
        List<Article> articles = List.of(
            new Article("id1", "2026-06-12", "Anthropic", "rss",
                "https://anthropic.com/post", "Post 1", "Content 1",
                null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"),
            new Article("id2", "2026-06-12", "@karpathy", "twitter",
                "https://x.com/karpathy/1", "Post 2", "Content 2",
                null, 0, List.of(), "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z")
        );

        when(repository.getArticlesByDate("2026-06-12")).thenReturn(articles);
        when(claudeClient.curateArticles(articles)).thenReturn(List.of(
            new ClaudeClient.CurationResult("id1", 9, "Great post.", List.of("LLM")),
            new ClaudeClient.CurationResult("id2", 7, "Good insight.", List.of("research"))
        ));
        when(htmlGenerator.generateDigestPage(any(), any(), any())).thenReturn("<html>...</html>");

        Digest digest = service.curate("2026-06-12");

        assertNotNull(digest);
        assertEquals("2026-06-12", digest.digestDate());
        assertEquals(1, digest.top5().size());
        assertEquals("id1", digest.top5().get(0).articleId());
        assertEquals(1, digest.more().size());
        verify(repository).saveDigest(any());
    }

    @Test
    void handlesEmptyArticleDay() throws Exception {
        when(repository.getArticlesByDate("2026-06-12")).thenReturn(List.of());

        Digest digest = service.curate("2026-06-12");

        assertNotNull(digest);
        assertTrue(digest.top5().isEmpty());
        assertTrue(digest.more().isEmpty());
        verify(claudeClient, never()).curateArticles(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.curator.CuratorServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement CuratorService**

```java
package com.newsletter.curator;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CuratorService {

    private static final int TOP_N = 5;
    private static final int MAX_ARTICLES = 50;

    private final DynamoDbRepository repository;
    private final ClaudeClient claudeClient;
    private final HtmlGenerator htmlGenerator;

    public CuratorService(DynamoDbRepository repository, ClaudeClient claudeClient, HtmlGenerator htmlGenerator) {
        this.repository = repository;
        this.claudeClient = claudeClient;
        this.htmlGenerator = htmlGenerator;
    }

    public Digest curate(String date) throws Exception {
        List<Article> articles = repository.getArticlesByDate(date);

        if (articles.isEmpty()) {
            Digest empty = new Digest(date, List.of(), List.of(), Instant.now().toString());
            repository.saveDigest(empty);
            return empty;
        }

        if (articles.size() > MAX_ARTICLES) {
            articles = articles.subList(0, MAX_ARTICLES);
        }

        List<ClaudeClient.CurationResult> results = claudeClient.curateArticles(articles);
        results.sort((a, b) -> Integer.compare(b.score(), a.score()));

        Map<String, Article> articleMap = articles.stream()
            .collect(Collectors.toMap(Article::articleId, a -> a));

        List<Digest.DigestEntry> top5 = new ArrayList<>();
        List<Digest.DigestEntry> more = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            ClaudeClient.CurationResult r = results.get(i);
            Article a = articleMap.get(r.articleId());
            if (a == null) continue;

            Digest.DigestEntry entry = new Digest.DigestEntry(
                r.articleId(), a.title(), r.summary(), a.sourceName(), a.originalUrl(), r.score()
            );

            if (i < TOP_N) {
                top5.add(entry);
            } else {
                more.add(entry);
            }
        }

        Digest digest = new Digest(date, top5, more, Instant.now().toString());
        repository.saveDigest(digest);
        return digest;
    }

    public String generateHtml(Digest digest) {
        String prevDate = LocalDate.parse(digest.digestDate()).minusDays(1).toString();
        return htmlGenerator.generateDigestPage(digest, prevDate, null);
    }
}
```

- [ ] **Step 4: Implement CuratorLambda**

```java
package com.newsletter.curator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Digest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.time.LocalDate;
import java.util.Map;

public class CuratorLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final CuratorService curatorService;
    private final HtmlGenerator htmlGenerator;
    private final S3Client s3;
    private final String bucketName;

    public CuratorLambda() {
        DynamoDbClient dynamo = DynamoDbClient.create();
        this.s3 = S3Client.create();
        this.bucketName = System.getenv("BUCKET_NAME");
        String apiUrl = System.getenv("API_URL");
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

        DynamoDbRepository repository = new DynamoDbRepository(dynamo,
            System.getenv("ARTICLES_TABLE"),
            System.getenv("DIGESTS_TABLE"),
            System.getenv("FEEDBACK_TABLE"));
        ClaudeClient claudeClient = new ClaudeClient(anthropicKey);
        this.htmlGenerator = new HtmlGenerator(apiUrl);
        this.curatorService = new CuratorService(repository, claudeClient, htmlGenerator);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            String today = LocalDate.now().toString();
            Digest digest = curatorService.curate(today);

            String html = curatorService.generateHtml(digest);
            uploadToS3("digest/" + today + ".html", html, "text/html");

            String indexHtml = htmlGenerator.generateIndexRedirect(today);
            uploadToS3("index.html", indexHtml, "text/html");

            context.getLogger().log("Curator generated digest for " + today +
                " with " + digest.top5().size() + " top articles");
            return Map.of("statusCode", 200, "date", today);
        } catch (Exception e) {
            context.getLogger().log("Curator failed: " + e.getMessage());
            return Map.of("statusCode", 500, "error", e.getMessage());
        }
    }

    private void uploadToS3(String key, String content, String contentType) {
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromString(content)
        );
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.curator.CuratorServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: add CuratorService and CuratorLambda for digest generation"
```

---

### Task 12: API Lambda

**Files:**
- Create: `src/main/java/com/newsletter/api/ApiLambda.java`
- Create: `src/main/java/com/newsletter/api/ApiRouter.java`
- Test: `src/test/java/com/newsletter/api/ApiRouterTest.java`

- [ ] **Step 1: Write ApiRouter test**

```java
package com.newsletter.api;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiRouterTest {

    @Mock private DynamoDbRepository repository;
    private ApiRouter router;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        router = new ApiRouter(repository);
    }

    @Test
    void getDigestReturnsDigestForDate() {
        Digest digest = new Digest("2026-06-12",
            List.of(new Digest.DigestEntry("id1", "Title", "Summary", "Anthropic", "https://url.com", 9)),
            List.of(),
            "2026-06-12T06:30:00Z");
        when(repository.getDigest("2026-06-12")).thenReturn(Optional.of(digest));

        ApiRouter.Response response = router.handleGet("/digest", Map.of("date", "2026-06-12"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Title"));
    }

    @Test
    void getDigestReturns404WhenMissing() {
        when(repository.getDigest("2026-06-12")).thenReturn(Optional.empty());

        ApiRouter.Response response = router.handleGet("/digest", Map.of("date", "2026-06-12"));

        assertEquals(404, response.statusCode());
    }

    @Test
    void postFeedbackSavesAndReturns200() {
        String body = """
            {"article_id": "id1", "digest_date": "2026-06-12", "feedback": "like"}
            """;

        ApiRouter.Response response = router.handlePost("/feedback", body);

        assertEquals(200, response.statusCode());
        verify(repository).saveFeedback(any(Feedback.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.newsletter.api.ApiRouterTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ApiRouter**

```java
package com.newsletter.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApiRouter {

    private final DynamoDbRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiRouter(DynamoDbRepository repository) {
        this.repository = repository;
    }

    public record Response(int statusCode, String body, Map<String, String> headers) {
        public Response(int statusCode, String body) {
            this(statusCode, body, Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Methods", "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers", "Content-Type"
            ));
        }
    }

    public Response handleGet(String path, Map<String, String> params) {
        try {
            return switch (path) {
                case "/digest" -> getDigest(params.getOrDefault("date", ""));
                case "/sources" -> getSources();
                default -> new Response(404, "{\"error\": \"Not found\"}");
            };
        } catch (Exception e) {
            return new Response(500, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    public Response handlePost(String path, String body) {
        try {
            return switch (path) {
                case "/feedback" -> postFeedback(body);
                default -> new Response(404, "{\"error\": \"Not found\"}");
            };
        } catch (Exception e) {
            return new Response(500, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private Response getDigest(String date) throws Exception {
        Optional<Digest> digest = repository.getDigest(date);
        if (digest.isEmpty()) {
            return new Response(404, "{\"error\": \"No digest for date: " + date + "\"}");
        }
        return new Response(200, mapper.writeValueAsString(digest.get()));
    }

    private Response getSources() {
        return new Response(200, "{\"message\": \"sources endpoint\"}");
    }

    private Response postFeedback(String body) throws Exception {
        Map<String, String> data = mapper.readValue(body, Map.class);
        Feedback feedback = new Feedback(
            data.get("article_id"),
            data.get("digest_date"),
            data.get("feedback"),
            "",
            List.of(),
            Instant.now().toString()
        );
        repository.saveFeedback(feedback);
        return new Response(200, "{\"status\": \"saved\"}");
    }
}
```

- [ ] **Step 4: Implement ApiLambda**

```java
package com.newsletter.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.newsletter.common.DynamoDbRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.util.Map;

public class ApiLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ApiRouter router;

    public ApiLambda() {
        DynamoDbClient dynamo = DynamoDbClient.create();
        DynamoDbRepository repository = new DynamoDbRepository(dynamo,
            System.getenv("ARTICLES_TABLE"),
            System.getenv("DIGESTS_TABLE"),
            System.getenv("FEEDBACK_TABLE"));
        this.router = new ApiRouter(repository);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String path = event.getPath();
        String method = event.getHttpMethod();

        ApiRouter.Response response;

        if ("OPTIONS".equals(method)) {
            response = new ApiRouter.Response(200, "");
        } else if ("GET".equals(method)) {
            Map<String, String> params = event.getQueryStringParameters() != null
                ? event.getQueryStringParameters() : Map.of();
            response = router.handleGet(path, params);
        } else if ("POST".equals(method)) {
            response = router.handlePost(path, event.getBody());
        } else {
            response = new ApiRouter.Response(405, "{\"error\": \"Method not allowed\"}");
        }

        APIGatewayProxyResponseEvent apiResponse = new APIGatewayProxyResponseEvent();
        apiResponse.setStatusCode(response.statusCode());
        apiResponse.setBody(response.body());
        apiResponse.setHeaders(response.headers());
        return apiResponse;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.newsletter.api.ApiRouterTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: add ApiLambda and ApiRouter with digest/feedback endpoints"
```

---

### Task 13: AWS CDK Infrastructure

**Files:**
- Create: `cdk/build.gradle`
- Create: `cdk/src/main/java/com/newsletter/cdk/NewsletterStack.java`
- Create: `cdk/src/main/java/com/newsletter/cdk/CdkApp.java`

- [ ] **Step 1: Create CDK build.gradle**

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'com.newsletter.cdk'
version = '1.0.0'
sourceCompatibility = JavaVersion.VERSION_21

mainClassName = 'com.newsletter.cdk.CdkApp'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'software.amazon.awscdk:aws-cdk-lib:2.140.0'
    implementation 'software.constructs:constructs:10.3.0'
}
```

- [ ] **Step 2: Implement CdkApp**

```java
package com.newsletter.cdk;

import software.amazon.awscdk.App;

public class CdkApp {
    public static void main(String[] args) {
        App app = new App();
        new NewsletterStack(app, "AiCoffeeNewsletterStack");
        app.synth();
    }
}
```

- [ ] **Step 3: Implement NewsletterStack**

```java
package com.newsletter.cdk;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.events.*;
import software.amazon.awscdk.services.events.targets.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.s3.deployment.*;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.origins.*;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.sns.*;
import software.amazon.awscdk.services.sns.subscriptions.*;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.*;
import software.constructs.Construct;
import java.util.List;
import java.util.Map;

public class NewsletterStack extends Stack {

    public NewsletterStack(final Construct scope, final String id) {
        super(scope, id);

        // DynamoDB Tables
        Table articlesTable = Table.Builder.create(this, "ArticlesTable")
            .tableName("newsletter-articles")
            .partitionKey(Attribute.builder().name("article_id").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("fetch_date").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        articlesTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
            .indexName("fetch_date-index")
            .partitionKey(Attribute.builder().name("fetch_date").type(AttributeType.STRING).build())
            .build());

        Table digestsTable = Table.Builder.create(this, "DigestsTable")
            .tableName("newsletter-digests")
            .partitionKey(Attribute.builder().name("digest_date").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        Table feedbackTable = Table.Builder.create(this, "FeedbackTable")
            .tableName("newsletter-feedback")
            .partitionKey(Attribute.builder().name("article_id").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("digest_date").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // S3 Bucket for static site
        Bucket siteBucket = Bucket.Builder.create(this, "SiteBucket")
            .bucketName("ai-coffee-newsletter-site")
            .websiteIndexDocument("index.html")
            .publicReadAccess(true)
            .blockPublicAccess(BlockPublicAccess.Builder.create()
                .blockPublicAcls(false)
                .ignorePublicAcls(false)
                .blockPublicPolicy(false)
                .restrictPublicBuckets(false)
                .build())
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // CloudFront Distribution
        Distribution distribution = Distribution.Builder.create(this, "Distribution")
            .defaultBehavior(BehaviorOptions.builder()
                .origin(S3BucketOrigin.withOriginAccessControl(siteBucket))
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .build())
            .build();

        // Environment variables for Lambdas
        Map<String, String> lambdaEnv = Map.of(
            "ARTICLES_TABLE", articlesTable.getTableName(),
            "DIGESTS_TABLE", digestsTable.getTableName(),
            "FEEDBACK_TABLE", feedbackTable.getTableName(),
            "BUCKET_NAME", siteBucket.getBucketName()
        );

        // Fetcher Lambda
        Function fetcherLambda = Function.Builder.create(this, "FetcherLambda")
            .functionName("newsletter-fetcher")
            .runtime(Runtime.JAVA_21)
            .handler("com.newsletter.fetcher.FetcherLambda::handleRequest")
            .code(Code.fromAsset("../build/libs/ai-coffee-newsletter-1.0.0-all.jar"))
            .memorySize(512)
            .timeout(Duration.minutes(5))
            .environment(lambdaEnv)
            .build();

        // Curator Lambda
        Map<String, String> curatorEnv = new java.util.HashMap<>(lambdaEnv);
        curatorEnv.put("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY"));
        curatorEnv.put("API_URL", ""); // Set after API Gateway creation

        Function curatorLambda = Function.Builder.create(this, "CuratorLambda")
            .functionName("newsletter-curator")
            .runtime(Runtime.JAVA_21)
            .handler("com.newsletter.curator.CuratorLambda::handleRequest")
            .code(Code.fromAsset("../build/libs/ai-coffee-newsletter-1.0.0-all.jar"))
            .memorySize(512)
            .timeout(Duration.minutes(5))
            .environment(curatorEnv)
            .build();

        // API Lambda
        Function apiLambda = Function.Builder.create(this, "ApiLambda")
            .functionName("newsletter-api")
            .runtime(Runtime.JAVA_21)
            .handler("com.newsletter.api.ApiLambda::handleRequest")
            .code(Code.fromAsset("../build/libs/ai-coffee-newsletter-1.0.0-all.jar"))
            .memorySize(256)
            .timeout(Duration.seconds(30))
            .environment(lambdaEnv)
            .build();

        // Grant permissions
        articlesTable.grantReadWriteData(fetcherLambda);
        articlesTable.grantReadData(curatorLambda);
        articlesTable.grantReadData(apiLambda);
        digestsTable.grantReadWriteData(curatorLambda);
        digestsTable.grantReadData(apiLambda);
        feedbackTable.grantReadWriteData(apiLambda);
        siteBucket.grantReadWrite(fetcherLambda);
        siteBucket.grantReadWrite(curatorLambda);

        // API Gateway
        RestApi api = RestApi.Builder.create(this, "NewsletterApi")
            .restApiName("newsletter-api")
            .defaultCorsPreflightOptions(CorsOptions.builder()
                .allowOrigins(Cors.ALL_ORIGINS)
                .allowMethods(Cors.ALL_METHODS)
                .build())
            .build();

        LambdaIntegration apiIntegration = new LambdaIntegration(apiLambda);
        api.getRoot().addResource("digest").addMethod("GET", apiIntegration);
        api.getRoot().addResource("feedback").addMethod("POST", apiIntegration);
        api.getRoot().addResource("sources").addMethod("GET", apiIntegration);

        // EventBridge - Fetcher runs at 6 AM UTC
        Rule fetcherRule = Rule.Builder.create(this, "FetcherSchedule")
            .schedule(Schedule.cron(CronOptions.builder().hour("6").minute("0").build()))
            .build();
        fetcherRule.addTarget(new LambdaFunction(fetcherLambda));

        // EventBridge - Curator runs at 6:15 AM UTC (after fetcher completes)
        Rule curatorRule = Rule.Builder.create(this, "CuratorSchedule")
            .schedule(Schedule.cron(CronOptions.builder().hour("6").minute("15").build()))
            .build();
        curatorRule.addTarget(new LambdaFunction(curatorLambda));

        // SNS for alerts
        Topic alertTopic = Topic.Builder.create(this, "AlertTopic")
            .topicName("newsletter-alerts")
            .build();

        // Outputs
        new CfnOutput(this, "SiteUrl", CfnOutputProps.builder()
            .value(distribution.getDistributionDomainName())
            .build());
        new CfnOutput(this, "ApiUrl", CfnOutputProps.builder()
            .value(api.getUrl())
            .build());
    }
}
```

- [ ] **Step 4: Update settings.gradle to include CDK subproject**

```groovy
rootProject.name = 'ai-coffee-newsletter'
include 'cdk'
```

- [ ] **Step 5: Verify CDK compiles**

Run: `cd cdk && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add cdk/ settings.gradle
git commit -m "feat: add AWS CDK infrastructure stack"
```

---

### Task 14: Deploy Static Assets

**Files:**
- Modify: `frontend/feedback.js` (remove template placeholder)

- [ ] **Step 1: Add Gradle task to upload static assets**

Add to root `build.gradle`:

```groovy
task uploadAssets(type: Exec) {
    commandLine 'aws', 's3', 'sync', 'frontend/', "s3://${project.findProperty('bucketName') ?: 'ai-coffee-newsletter-site'}/assets/",
        '--exclude', 'template.html',
        '--content-type', 'text/css',
        '--cache-control', 'max-age=86400'
}
```

- [ ] **Step 2: Add fat JAR task for Lambda deployment**

Add to root `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

shadowJar {
    archiveBaseName.set('ai-coffee-newsletter')
    archiveVersion.set('1.0.0')
    archiveClassifier.set('all')
}
```

- [ ] **Step 3: Build and verify fat JAR**

Run: `./gradlew shadowJar`
Expected: BUILD SUCCESSFUL, `build/libs/ai-coffee-newsletter-1.0.0-all.jar` created

- [ ] **Step 4: Commit**

```bash
git add build.gradle frontend/
git commit -m "feat: add shadow JAR packaging and asset upload tasks"
```

---

### Task 15: End-to-End Local Test

**Files:**
- Create: `src/test/java/com/newsletter/integration/EndToEndTest.java`

- [ ] **Step 1: Write integration test that exercises the full pipeline locally**

```java
package com.newsletter.integration;

import com.newsletter.curator.ClaudeClient;
import com.newsletter.curator.CuratorService;
import com.newsletter.curator.HtmlGenerator;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.fetcher.FetcherService;
import com.newsletter.fetcher.RssFetcher;
import com.newsletter.fetcher.TwitterFetcher;
import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import com.newsletter.model.SourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EndToEndTest {

    @Mock private DynamoDbRepository repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fullPipelineFromFetchToCuration() throws Exception {
        // Simulate fetcher finding articles
        RssFetcher rssFetcher = mock(RssFetcher.class);
        TwitterFetcher twitterFetcher = mock(TwitterFetcher.class);

        Article a1 = new Article("id1", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/post1", "Claude Gets Smarter",
            "Big improvements to Claude's reasoning capabilities.",
            null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z");
        Article a2 = new Article("id2", "2026-06-12", "@karpathy", "twitter",
            "https://x.com/karpathy/123", "New scaling paper...",
            "Published new findings on neural scaling laws.",
            null, 0, List.of(), "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z");

        SourceConfig config = new SourceConfig(
            List.of(new SourceConfig.RssSource("Anthropic", "https://anthropic.com/rss", "company_blog")),
            List.of(new SourceConfig.TwitterSource("karpathy")),
            List.of(), List.of()
        );

        when(rssFetcher.fetchFromUrl(anyString(), eq("Anthropic"), eq("rss"))).thenReturn(List.of(a1));
        when(twitterFetcher.fetchTweets("karpathy")).thenReturn(List.of(a2));
        when(repository.exists(anyString(), anyString())).thenReturn(false);

        // Run fetcher
        FetcherService fetcherService = new FetcherService(repository, rssFetcher, twitterFetcher);
        int saved = fetcherService.fetchAll(config);
        assertEquals(2, saved);

        // Simulate curator
        when(repository.getArticlesByDate("2026-06-12")).thenReturn(List.of(a1, a2));

        ClaudeClient claudeClient = mock(ClaudeClient.class);
        when(claudeClient.curateArticles(anyList())).thenReturn(List.of(
            new ClaudeClient.CurationResult("id1", 9, "Major Claude upgrade.", List.of("LLM")),
            new ClaudeClient.CurationResult("id2", 7, "Scaling law findings.", List.of("research"))
        ));

        HtmlGenerator htmlGenerator = new HtmlGenerator("https://api.example.com");
        CuratorService curatorService = new CuratorService(repository, claudeClient, htmlGenerator);
        Digest digest = curatorService.curate("2026-06-12");

        assertNotNull(digest);
        assertEquals(2, digest.top5().size() + digest.more().size());
        assertEquals("id1", digest.top5().get(0).articleId());

        // Generate HTML
        String html = curatorService.generateHtml(digest);
        assertTrue(html.contains("Claude Gets Smarter"));
        assertTrue(html.contains("https://anthropic.com/blog/post1"));
        assertTrue(html.contains("feedback"));
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `./gradlew test --tests "com.newsletter.integration.EndToEndTest"`
Expected: PASS

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "test: add end-to-end integration test for full pipeline"
```

---

### Task 16: Deployment Script & README

**Files:**
- Create: `deploy.sh`

- [ ] **Step 1: Create deployment script**

```bash
#!/bin/bash
set -e

echo "Building fat JAR..."
./gradlew shadowJar

echo "Synthesizing CDK..."
cd cdk
cdk synth
echo ""

echo "Deploying infrastructure..."
cdk deploy --require-approval never

echo "Uploading static assets..."
cd ..
aws s3 sync frontend/ s3://ai-coffee-newsletter-site/assets/ \
    --exclude "template.html" \
    --cache-control "max-age=86400"

echo "Uploading sources config..."
aws s3 cp sources.yaml s3://ai-coffee-newsletter-site/sources.yaml

echo "Done! Your newsletter is live."
```

- [ ] **Step 2: Make executable**

```bash
chmod +x deploy.sh
```

- [ ] **Step 3: Commit**

```bash
git add deploy.sh
git commit -m "feat: add deployment script"
```

---

## Deployment Checklist

After all tasks are complete:

1. Set environment variable: `export ANTHROPIC_API_KEY=<your-key>`
2. Run `./deploy.sh`
3. Note the CloudFront URL and API Gateway URL from CDK output
4. Update Route53 to point your domain to CloudFront
5. Verify by visiting your domain the next morning after 6 AM UTC

---

## Summary

| Task | Component | What it builds |
|------|-----------|----------------|
| 1 | Scaffolding | Gradle project with all dependencies |
| 2 | Models | Article, Digest, Feedback, SourceConfig records |
| 3 | Config | YAML config loader + sources.yaml |
| 4 | Fetcher | RSS feed parser |
| 5 | Fetcher | Twitter scraper |
| 6 | Storage | DynamoDB repository |
| 7 | Fetcher | Orchestrator service |
| 8 | Fetcher | Lambda handler |
| 9 | Curator | Claude API client |
| 10 | Frontend | HTML generator + CSS/JS |
| 11 | Curator | Curation service + Lambda |
| 12 | API | REST API Lambda |
| 13 | Infra | AWS CDK stack |
| 14 | Deploy | Fat JAR + asset upload |
| 15 | Testing | End-to-end integration test |
| 16 | Deploy | Deployment script |
