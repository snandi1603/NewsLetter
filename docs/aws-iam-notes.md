# AWS IAM - Mental Model

## The Three Concepts

| Concept | What it is | Analogy |
|---------|-----------|---------|
| IAM User | A permanent identity for a person or CI/CD | Your employee ID card |
| IAM Role | A temporary identity a service can "put on" | A visitor badge you hand back |
| IAM Policy | Rules defining what actions are allowed | Access rules printed on the badge |

## IAM User

- Permanent identity (lives until you delete it)
- Has long-lived credentials (Access Key + Secret Key)
- Stored in `~/.aws/credentials` under a profile name
- Used by humans or automation to deploy/manage infrastructure

Example: `sudipto` user with profile `dynamodblearning-java`

## IAM Role

- No permanent credentials (temporary tokens generated each time it's assumed)
- Has two parts:
  1. **Trust Policy** — WHO can assume this role (e.g., "only Lambda service")
  2. **Permission Policy** — WHAT they can do once assumed (e.g., "ReadWrite on DynamoDB table X")
- Tokens auto-expire (typically ~1 hour)

## How They Relate

```
IAM User (sudipto)
    │
    │ creates (via CDK / Console / CLI)
    │
    ├── Role A: "Fetcher-ServiceRole"
    │       Trust: Only Lambda can assume
    │       Perms: ReadWrite articles table, ReadWrite S3
    │
    ├── Role B: "Curator-ServiceRole"
    │       Trust: Only Lambda can assume
    │       Perms: Read articles, ReadWrite digests, ReadWrite S3
    │
    └── Role C: "Api-ServiceRole"
            Trust: Only Lambda can assume
            Perms: Read articles, Read digests, ReadWrite feedback
```

## Two Separate Worlds

### Deploy Time (you, once)

```
Your laptop
    → authenticates as IAM User (permanent access keys)
    → runs `cdk deploy`
    → creates tables, roles, lambdas, everything
```

### Runtime (AWS, every day)

```
EventBridge fires at 6 AM
    → Lambda wakes up
    → Lambda assumes its assigned Role (your user is NOT involved)
    → Gets temporary credentials
    → Does its work (DynamoDB, S3, etc.)
    → Credentials expire
```

Your IAM user is NEVER involved at runtime. The services self-govern using the roles you assigned.

## Why Roles Instead of Hardcoding User Keys?

| | Hardcoded Keys | IAM Role |
|---|---|---|
| Security | Keys can leak, never expire | No keys to leak, auto-expire |
| Rotation | You manually rotate | AWS handles it |
| Blast radius | Keys can do everything the user can | Role only allows specific actions |
| Best practice | Never do this | Always do this |

## CDK Shorthand

Instead of writing IAM JSON by hand:

```java
// One line creates the role + policy + attachment
articlesTable.grantReadWriteData(fetcherLambda);
```

CDK generates:
1. IAM Role (identity for the Lambda)
2. IAM Policy (ReadWrite permissions on the table)
3. Attaches policy to role
4. Assigns role to Lambda

## Current Account State (as of 2026-06-12)

**Users:**
- `sudipto` — main user (DynamoDB Full, IAM Full, CloudWatch, X-Ray, Billing ReadOnly)
- `kiro-tx-only` — limited user (TransactionsOnly)

**Key Insight:** `sudipto` needs additional policies (Lambda, S3, API Gateway, CloudFront, EventBridge, CloudFormation) before running `cdk deploy` for the Newsletter project.
