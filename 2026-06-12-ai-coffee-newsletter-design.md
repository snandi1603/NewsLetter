# AI Coffee Newsletter — Design Spec

## Overview

A cloud-hosted daily AI newsletter system that fetches blog posts, tweets, and articles from major AI companies and influencers, uses Claude Sonnet to curate and summarize them, and serves a static web page with a "Top 5" digest plus a "more" section. Hosted on AWS behind a custom domain.

## Goals

- Automated daily fetch at 6 AM from configurable sources
- AI-curated ranking with 2-3 sentence summaries and original links
- Static site with date-based pagination (back/next navigation)
- REST API for future mobile app
- Like/dislike feedback loop to improve curation over time
- Zero ongoing maintenance — add sources by editing a config file

## Sources

### Company Blogs (RSS)
- Anthropic (anthropic.com/blog)
- OpenAI (openai.com/blog)
- Google AI / DeepMind (blog.google/technology/ai/, deepmind.google/blog)
- XAI (x.ai/blog)
- Cursor (cursor.com/blog)
- Kiro (kiro.dev/blog)

### Platforms (RSS / Scraping)
- Substack — configurable list of AI newsletters
- Medium — AI-focused publications/tags

### Individuals (Public Twitter Scraping)
- Andrej Karpathy (@karpathy)
- Additional handles configurable

### Configuration

All sources managed via `sources.yaml` stored in S3:

```yaml
rss:
  - name: Anthropic
    url: https://anthropic.com/blog/rss
    type: company_blog
  - name: OpenAI
    url: https://openai.com/blog/rss
    type: company_blog
  - name: Google AI
    url: https://blog.google/technology/ai/rss
    type: company_blog
  - name: DeepMind
    url: https://deepmind.google/blog/rss
    type: company_blog
  - name: XAI
    url: https://x.ai/blog/rss
    type: company_blog
  - name: Cursor
    url: https://cursor.com/blog/rss
    type: company_blog
  - name: Kiro
    url: https://kiro.dev/blog/rss
    type: company_blog

substack:
  - name: AI Newsletter Example
    url: https://example.substack.com/feed

medium:
  - name: Towards AI
    url: https://pub.towardsai.net/feed

twitter:
  - handle: karpathy
  - handle: ylecun
  - handle: jimfan
```

Adding a new source = edit this file. No code changes needed.

## Architecture

### AWS Services
- **EventBridge** — Cron trigger at 6 AM daily
- **Lambda (Java 21)** — Three functions: Fetcher, Curator, API
- **DynamoDB** — Articles, digests, and feedback storage
- **S3** — Static site hosting + sources config
- **CloudFront** — CDN for static site
- **Route53** — Custom domain DNS
- **API Gateway** — REST API for mobile/feedback
- **CloudWatch** — Logging and alarms
- **SNS** — Failure notifications

### Component Overview

```
EventBridge (6AM) → FetcherLambda → DynamoDB (articles)
                                          ↓
                                   CuratorLambda → Claude Sonnet API
                                          ↓
                              DynamoDB (digests) + S3 (static HTML)
                                          ↓
                              CloudFront + Route53 (your domain)

API Gateway → ApiLambda → DynamoDB (read digests, write feedback)
```

### Lambda Functions

#### 1. FetcherLambda
- Triggered by EventBridge at 6 AM
- Reads `sources.yaml` from S3
- Runs fetcher modules in parallel:
  - **RssFetcher** — parses RSS/Atom feeds (Rome library)
  - **TwitterFetcher** — scrapes public tweets (JSoup)
  - **SubstackFetcher** — parses Substack RSS feeds
  - **MediumFetcher** — scrapes Medium articles (JSoup)
- Deduplicates by URL hash against DynamoDB
- Stores raw articles in DynamoDB `articles` table
- Triggers CuratorLambda on completion (via EventBridge event)

#### 2. CuratorLambda
- Pulls today's newly fetched articles from DynamoDB
- Truncates long articles to ~4000 tokens
- Sends batch to Claude Sonnet with ranking prompt
- Claude returns JSON: score (1-10), summary, tags per article
- Writes digest to DynamoDB `digests` table
- Generates static HTML and uploads to S3
- Updates yesterday's page navigation (adds "Tomorrow" link)

#### 3. ApiLambda
- `GET /digest?date=YYYY-MM-DD` — returns curated digest for a date
- `GET /articles?source=X&limit=N` — browse articles by source
- `GET /sources` — list configured sources
- `POST /feedback` — record like/dislike on an article

## Data Model

### DynamoDB Tables

#### `articles` table
| Field | Type | Description |
|-------|------|-------------|
| `article_id` | String (PK) | SHA-256 hash of original URL |
| `fetch_date` | String (SK) | ISO date (2026-06-12) |
| `source_name` | String | e.g., "Anthropic", "@karpathy" |
| `source_type` | String | rss, twitter, substack, medium |
| `original_url` | String | Link to original content |
| `title` | String | Article title |
| `raw_content` | String | Full text (truncated to 4000 tokens) |
| `summary` | String | Claude-generated 2-3 sentence summary |
| `relevance_score` | Number | Claude-assigned 1-10 score |
| `tags` | List<String> | Topics extracted by Claude |
| `published_at` | String | Original publish timestamp |
| `created_at` | String | When we fetched it |

#### `digests` table
| Field | Type | Description |
|-------|------|-------------|
| `digest_date` | String (PK) | ISO date (2026-06-12) |
| `top_5` | List<Map> | Top 5 article_ids with summaries |
| `more` | List<Map> | Remaining articles with one-liners |
| `generated_at` | String | Timestamp of generation |

#### `feedback` table
| Field | Type | Description |
|-------|------|-------------|
| `article_id` | String (PK) | References articles table |
| `digest_date` | String (SK) | Date feedback was given |
| `feedback` | String | "like" or "dislike" |
| `source_name` | String | Denormalized for querying |
| `tags` | List<String> | Denormalized from article |
| `created_at` | String | Timestamp |

## Ranking & Curation

### Ranking Criteria

| Signal | Weight | Description |
|--------|--------|-------------|
| Significance | High | Breakthrough/major release > minor update |
| Novelty | High | First mention > rehash |
| Source authority | High | Official blogs > random posts |
| Breadth of impact | Medium | Field-wide > niche |
| Recency | Medium | Today > caught-late older posts |
| Engagement | Low | Likes/retweets as weak signal |

### Curation Prompt (Claude Sonnet)

```
You are an AI research news curator. Given these articles, rank them by:
1. Significance (breakthrough, major release, or important announcement vs. minor update)
2. Novelty (new information vs. rehash of known topics)
3. Breadth of impact (affects the whole AI field vs. niche use case)

Score each 1-10 and provide a 2-3 sentence summary.
Ensure diversity of sources in the top 5 when possible.

Return JSON array: [{ "article_id": "...", "score": N, "summary": "...", "tags": [...] }]
```

### Tie-breaking
- Prefer diversity of sources in top 5 (don't show 5 posts from the same source)
- Prompt instructs Claude to spread sources

### Future Enhancement (v2)
When ~50+ feedback signals are collected, generate a weekly preference summary:
```
User preferences based on past feedback:
- Likes: model releases, safety research, Anthropic, Karpathy
- Dislikes: opinion pieces, crypto/AI crossover
- Preferred sources: Anthropic (85% liked), DeepMind (80% liked)
```
This gets injected into the curation prompt to personalize rankings.

## Frontend

### Static Site Structure
```
s3://newsletter-bucket/
├── index.html              (redirects to today's digest)
├── digest/
│   ├── 2026-06-12.html
│   ├── 2026-06-11.html
│   └── ...
├── assets/
│   ├── style.css
│   └── feedback.js         (handles like/dislike API calls)
└── sources.yaml
```

### Page Layout
- Header: Title + date
- Navigation: ◀ Yesterday | [date] | Tomorrow ▶
- Top 5 section: Full summaries with source badge and original link
- More section: One-liner list with source and link
- Each article has 👍 / 👎 buttons
- Footer navigation: Same back/next buttons

### Navigation Rules
- "Tomorrow" hidden/disabled if viewing today
- Skips dates with no digest (links to next available day)
- `index.html` always redirects to latest available digest

## Error Handling

### Fetching
- Each source fetcher is independent — one failure doesn't block others
- Retry with exponential backoff (max 3 attempts per source)
- Failed fetches logged to CloudWatch
- Source flagged as "unavailable" on page if fails 3 consecutive days

### Claude API
- Retry up to 2 times with backoff
- Fallback: serve unsummarized articles (title + link only) with "summaries unavailable" notice

### Content Edge Cases
- Twitter scraping may break — system degrades gracefully
- Long articles truncated to ~4000 tokens before Claude call
- Empty days: page shows "No new posts today"
- Deduplication: URL hash primary, title similarity as fallback

### Cost Guardrails
- Claude Sonnet for ranking/summarization (~$3/1M input tokens)
- Haiku for simple tag extraction if needed
- Max 50 articles per Claude call per day
- Estimated daily cost: ~$0.01-0.05

### Monitoring
- CloudWatch alarms if Lambda fails entirely
- SNS email notification if digest not generated by 7 AM

## Tech Stack

- **Language:** Java 21
- **Build:** Gradle or Maven
- **AWS SDK:** v2 (DynamoDB, S3, Lambda, etc.)
- **RSS Parsing:** Rome/ROME library
- **HTML Scraping:** JSoup
- **AI:** Anthropic Java SDK (Claude Sonnet)
- **JSON:** Jackson
- **Infrastructure as Code:** AWS CDK (Java) or SAM template
- **Testing:** JUnit 5, Mockito, LocalStack for integration tests

## Cost Estimate

| Service | Monthly Cost |
|---------|-------------|
| Lambda | ~$0 (free tier: 1M requests) |
| DynamoDB | ~$0 (free tier: 25 GB, 25 RCU/WCU) |
| S3 | ~$0.01 |
| CloudFront | ~$0-1 |
| API Gateway | ~$0 (free tier: 1M calls) |
| Route53 | ~$0.50 (hosted zone) |
| Claude Sonnet API | ~$1-2/month |
| **Total** | **~$2-4/month** |

## Future Enhancements (out of scope for v1)
- Mobile app consuming the REST API
- Feedback-driven personalized rankings (after ~50 signals)
- Weekly summary email
- "Share" button to export an article to social media
- Source health dashboard
