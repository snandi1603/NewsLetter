# AI Coffee Newsletter — Architecture

## Overview

A serverless daily AI news digest that fetches articles from RSS feeds and Twitter, curates them using Claude, and serves a static site on a custom domain.

**Live URL:** https://ainewsletter.snandi1603.org

---

## High-Level Data Flow

```
┌────────────────┐     6:00 AM      ┌─────────────────┐       RSS/Twitter       ┌──────────────┐
│  EventBridge   │ ────────────────► │  FetcherLambda  │ ◄─────────────────────► │  Internet    │
│  (cron)        │                   │                 │                          └──────────────┘
└────────────────┘                   └────────┬────────┘
                                              │ saves articles
                                              ▼
┌────────────────┐     6:15 AM      ┌─────────────────┐       Claude API        ┌──────────────┐
│  EventBridge   │ ────────────────► │  CuratorLambda  │ ◄────────────────────► │  Anthropic   │
│  (cron)        │                   │                 │                          └──────────────┘
└────────────────┘                   └────────┬────────┘
                                              │ saves digest + uploads HTML
                                              ▼
┌────────────────┐                   ┌─────────────────┐
│  Browser       │ ◄───────────────► │  CloudFront     │ ◄──── S3 (static HTML)
│                │                   │  + Route 53     │
│                │   GET /digest     ├─────────────────┤
│                │ ──────────────────►  API Gateway    │ ──── ApiLambda ──── DynamoDB
│                │   POST /feedback  │                 │
└────────────────┘                   └─────────────────┘
```

---

## AWS Services Used

| Service | Purpose | Resource Name |
|---------|---------|---------------|
| **Lambda** | Compute (3 functions) | newsletter-fetcher, newsletter-curator, newsletter-api |
| **DynamoDB** | Database (3 tables) | newsletter-articles, newsletter-digests, newsletter-feedback |
| **S3** | Static site hosting | ai-coffee-newsletter-site |
| **CloudFront** | CDN + HTTPS | d3benhjqdxopcu.cloudfront.net |
| **Route 53** | Custom domain DNS | ainewsletter.snandi1603.org |
| **ACM** | SSL certificate | For HTTPS on custom domain |
| **EventBridge** | Cron scheduler | Daily triggers at 6:00 and 6:15 AM UTC |
| **API Gateway** | REST API | GET /digest, POST /feedback, GET /sources |
| **IAM** | Permissions | One role per Lambda (least privilege) |
| **CloudFormation** | Infrastructure-as-code | AiCoffeeNewsletterStack (via CDK) |

---

## Lambda Functions

### 1. FetcherLambda (newsletter-fetcher)

**Trigger:** EventBridge at 6:00 AM UTC daily
**Purpose:** Collect articles from all configured sources
**Flow:**
```
Read sources.yaml from S3
    → For each RSS feed: parse and extract articles
    → For each Twitter handle: fetch recent tweets
    → Deduplicate (skip if article_id already in DynamoDB)
    → Save new articles to newsletter-articles table
```
**Permissions:** Read/Write DynamoDB (articles), Read/Write S3

### 2. CuratorLambda (newsletter-curator)

**Trigger:** EventBridge at 6:15 AM UTC daily
**Purpose:** Rank articles with AI and generate the newsletter page
**Flow:**
```
Read today's articles from DynamoDB (max 20)
    → Send to Claude API with curation prompt
    → Claude returns scores (1-10), summaries, and tags
    → Sort by score, pick top 5
    → Save digest to newsletter-digests table
    → Generate HTML page with inline CSS
    → Upload digest HTML + index.html to S3
```
**Permissions:** Read DynamoDB (articles), Read/Write DynamoDB (digests), Read/Write S3
**Requires:** ANTHROPIC_API_KEY environment variable

### 3. ApiLambda (newsletter-api)

**Trigger:** API Gateway (HTTP requests from browser)
**Purpose:** Serve data and accept feedback
**Endpoints:**
```
GET  /digest?date=2026-06-12  → Returns digest JSON
POST /feedback                → Records thumbs-up/down
GET  /sources                 → Returns configured sources
```
**Permissions:** Read DynamoDB (articles, digests), Read/Write DynamoDB (feedback)

---

## DynamoDB Tables

### newsletter-articles
| Key | Type | Description |
|-----|------|-------------|
| article_id (PK) | String | Hash of source + title |
| fetch_date (SK) | String | Date fetched (YYYY-MM-DD) |
| GSI: fetch_date-index | | Query articles by date |

### newsletter-digests
| Key | Type | Description |
|-----|------|-------------|
| digest_date (PK) | String | Date of digest (YYYY-MM-DD) |

### newsletter-feedback
| Key | Type | Description |
|-----|------|-------------|
| article_id (PK) | String | Which article |
| digest_date (SK) | String | Which day's digest |

---

## IAM Security Model

Each Lambda gets its own IAM Role with least-privilege permissions:

```
fetcherLambda Role:
    ├── ReadWrite: newsletter-articles
    └── ReadWrite: ai-coffee-newsletter-site (S3)

curatorLambda Role:
    ├── Read: newsletter-articles
    ├── ReadWrite: newsletter-digests
    └── ReadWrite: ai-coffee-newsletter-site (S3)

apiLambda Role:
    ├── Read: newsletter-articles
    ├── Read: newsletter-digests
    └── ReadWrite: newsletter-feedback
```

Your IAM User (sudipto / dynamodblearning-java profile) is only used at deploy time. It is never involved at runtime.

---

## Networking & DNS

```
User types: https://ainewsletter.snandi1603.org
    │
    ▼
Route 53: "ainewsletter.snandi1603.org" → A record (alias) → CloudFront
    │
    ▼
CloudFront: Serves cached content from S3
    │         Uses ACM certificate for HTTPS
    ▼
S3: Returns index.html (redirects to /digest/YYYY-MM-DD.html)
```

---

## Project Structure

```
NewsLetter/
├── src/main/java/com/newsletter/
│   ├── model/              ← Data shapes (Article, Digest, Feedback, SourceConfig)
│   ├── common/             ← Shared (AwsClientFactory, DynamoDbRepository)
│   ├── fetcher/            ← FetcherLambda + RssFetcher + TwitterFetcher
│   ├── curator/            ← CuratorLambda + ClaudeClient + HtmlGenerator
│   └── api/                ← ApiLambda + ApiRouter
├── cdk/src/main/java/      ← CDK infrastructure (NewsletterStack, CdkApp)
├── sources.yaml            ← Configurable news sources
├── build.gradle            ← Main build (shadow JAR for Lambda)
├── settings.gradle         ← Gradle settings + toolchain resolver
└── docs/                   ← Documentation
```

---

## Deployment

**Prerequisites:** AWS CDK CLI, Java 21, Gradle

```bash
# Build the Lambda JAR
./gradlew clean shadowJar

# Deploy infrastructure (set API key first)
export ANTHROPIC_API_KEY=sk-ant-...
cd cdk
cdk deploy --profile dynamodblearning-java

# Manual first-run (after that, EventBridge handles daily triggers)
aws lambda invoke --function-name newsletter-fetcher --profile dynamodblearning-java /tmp/out.json
aws lambda invoke --function-name newsletter-curator --profile dynamodblearning-java --cli-read-timeout 300 /tmp/out.json
```

**Tear down:**
```bash
cdk destroy --profile dynamodblearning-java
# Manually delete RETAIN resources (tables + bucket)
```

---

## Cost Estimate (monthly, low traffic)

| Service | Estimated Cost |
|---------|---------------|
| Lambda (3 functions, ~60 invocations/month) | ~$0 (free tier) |
| DynamoDB (on-demand, low volume) | ~$0 (free tier) |
| S3 (< 1 GB storage) | ~$0.02 |
| CloudFront (low traffic) | ~$0 (free tier) |
| Route 53 (hosted zone) | $0.50 |
| ACM certificate | Free |
| Claude API (~20 articles/day) | ~$0.50-2.00 |
| **Total** | **~$1-3/month** |

---

## Future Improvements

- Store ANTHROPIC_API_KEY in AWS Secrets Manager instead of env var
- Add email delivery (SES) for subscribers
- Add more sources (Reddit, HackerNews, arXiv)
- Analytics dashboard for feedback data
- CI/CD pipeline (GitHub Actions → auto-deploy on push)
