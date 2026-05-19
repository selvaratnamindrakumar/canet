# Architecture — Employee Skills Tracking App on AWS

## Full Architecture Diagram

```
                        ┌──────────────────────────────────────────────────────────────────┐
                        │                          AWS Cloud (eu-west-1)                    │
                        │                                                                    │
  ┌──────────┐  HTTPS   │  ┌───────────────────┐    ┌──────────────────────────────────┐  │
  │          │──────────┼─▶│   Amazon          │    │   Amazon S3 Bucket               │  │
  │ Browser  │          │  │   CloudFront      │───▶│   (frontend)                     │  │
  │          │          │  │   Distribution    │    │   index.html, *.js, *.css        │  │
  │          │          │  │   (CDN + HTTPS)   │    │   Private (OAC access only)      │  │
  │          │          │  └───────────────────┘    └──────────────────────────────────┘  │
  │          │  HTTPS   │                                                                    │
  │          │──────────┼─▶┌───────────────────┐                                           │
  └──────────┘  REST    │  │   API Gateway     │                                           │
                        │  │   HTTP API        │                                           │
                        │  │   (pay-per-req)   │                                           │
                        │  └────────┬──────────┘                                           │
                        │           │ invoke                                                 │
                        │  ┌────────▼──────────────────────────────────────────────────┐  │
                        │  │                Public Subnets (AZ-a, AZ-b)                │  │
                        │  │   ┌────────────────────────────────────────────────────┐  │  │
                        │  │   │  NAT Gateway (AZ-a)   Internet Gateway             │  │  │
                        │  │   └────────────────────────────────────────────────────┘  │  │
                        │  └───────────────────────────────────────────────────────────┘  │
                        │           │ ENI / VPC                                             │
                        │  ┌────────▼──────────────────────────────────────────────────┐  │
                        │  │                Private Subnets (AZ-a, AZ-b)               │  │
                        │  │                                                             │  │
                        │  │  ┌──────────────────────────────┐                          │  │
                        │  │  │  AWS Lambda Function          │                          │  │
                        │  │  │  FastAPI + Mangum (py3.11)    │                          │  │
                        │  │  │  512 MB / 30 s timeout        │                          │  │
                        │  │  │  handler.handler              │                          │  │
                        │  │  └──────────────┬───────────────┘                          │  │
                        │  │                 │ port 5432                                 │  │
                        │  │  ┌──────────────▼───────────────┐                          │  │
                        │  │  │  Amazon RDS PostgreSQL 16     │                          │  │
                        │  │  │  db.t3.micro  /  gp3 20 GB   │                          │  │
                        │  │  │  Encrypted at rest & transit │                          │  │
                        │  │  │  Automated backups (7 days)  │                          │  │
                        │  │  └──────────────────────────────┘                          │  │
                        │  └───────────────────────────────────────────────────────────┘  │
                        │                                                                    │
                        │  ┌───────────────────────────────────────────────────────────┐  │
                        │  │  AWS Secrets Manager                                       │  │
                        │  │  Secret: skills-app-dev/db-credentials                    │  │
                        │  │  Fields: host, port, dbname, username, password            │  │
                        │  └───────────────────────────────────────────────────────────┘  │
                        │                                                                    │
                        │  ┌───────────────────────────────────────────────────────────┐  │
                        │  │  AWS CloudWatch                                             │  │
                        │  │  Log groups: /aws/lambda/... | /aws/apigateway/...          │  │
                        │  └───────────────────────────────────────────────────────────┘  │
                        └──────────────────────────────────────────────────────────────────┘
```

---

## Component Descriptions

### Amazon CloudFront + S3

The frontend consists of three HTML files, JS modules, and a CSS file — entirely static. These are stored in a private S3 bucket and served through a CloudFront distribution:

- **Origin Access Control (OAC)** ensures only CloudFront can read from S3; the bucket has no public access.
- **Default root object** is `index.html`.
- **Custom error responses** map 404 and 403 back to `index.html` (SPA-friendly routing).
- **HTTPS enforced** — CloudFront redirects HTTP to HTTPS.
- Static assets (JS/CSS) are cached for 1–7 days; HTML files for 5 minutes.

### API Gateway HTTP API

A fully managed HTTP API (v2) that routes all methods and paths (`$default` route) to the Lambda function via an `AWS_PROXY` integration using payload format version 2.0. API Gateway handles:

- TLS termination
- Throttling (default: 10,000 req/s, configurable)
- Access logging to CloudWatch
- No servers to manage; billing is per request

### AWS Lambda (FastAPI + Mangum)

The FastAPI application runs inside a Lambda function. The `Mangum` adapter translates API Gateway payload format 2.0 events into ASGI-compatible scope/receive/send objects that FastAPI understands.

Key settings:
- **Runtime**: Python 3.11
- **Memory**: 512 MB (tunable; more memory = more CPU proportionally)
- **Timeout**: 30 seconds
- **Handler**: `handler.handler`
- **VPC config**: private subnets + `lambda_sg` security group

On cold start, the handler loads `DATABASE_URL` from Secrets Manager before importing the app, so SQLAlchemy connects correctly.

### Amazon RDS for PostgreSQL 16

A managed PostgreSQL database in the private subnets:

- **Instance class**: `db.t3.micro` (dev); upgrade to `db.t3.small` or higher for production.
- **Storage**: 20 GB gp3, encrypted at rest (AES-256).
- **Multi-AZ**: disabled in dev; set `multi_az = true` in production for automatic failover.
- **Automated backups**: 7-day retention, daily backup window `02:00–03:00 UTC`.
- **Performance Insights**: enabled for query analysis.
- **Security**: accessible only from `lambda_sg`; no public access.

### AWS Secrets Manager

Stores RDS credentials (`host`, `port`, `dbname`, `username`, `password`) as a JSON secret. The Lambda execution role has `secretsmanager:GetSecretValue` on this secret only (least-privilege). Secrets Manager supports automatic rotation with a Lambda rotation function — see the runbook.

### VPC Networking

- **CIDR**: `10.0.0.0/16`
- **Public subnets** (`10.0.0.0/24`, `10.0.1.0/24`): NAT Gateway, Internet Gateway
- **Private subnets** (`10.0.10.0/24`, `10.0.11.0/24`): Lambda ENIs, RDS
- Lambda requires private subnets to access RDS; outbound internet traffic (for Secrets Manager API calls) is routed through the NAT Gateway.

---

## Data Flow Narrative

### Read path (e.g. GET /employees/)

1. User's browser fetches `GET https://api.skills.example.com/employees/`.
2. Request arrives at **API Gateway HTTP API**, which authenticates the source and applies throttling.
3. API Gateway invokes the **Lambda function** with a payload format 2.0 event.
4. If this is a cold start, Lambda:
   a. Calls **Secrets Manager** `GetSecretValue` over HTTPS (via NAT Gateway).
   b. Sets `DATABASE_URL` in the process environment.
   c. Imports the FastAPI app (SQLAlchemy engine is created).
5. **Mangum** converts the event to an ASGI scope and calls the FastAPI app.
6. FastAPI routes the request to the `/employees/` endpoint handler.
7. SQLAlchemy opens a connection to **RDS PostgreSQL** (port 5432, private subnet).
8. The query runs, rows are returned, SQLAlchemy closes the connection (connection pool is per-container, reused on warm invocations).
9. FastAPI serialises the response to JSON; Mangum converts to an API Gateway response.
10. API Gateway returns the HTTP 200 response to the browser.

### Write path (e.g. POST /employees/{id}/skills)

Same as the read path up to step 5, then:

6. FastAPI validates the request body against the Pydantic schema.
7. SQLAlchemy executes an `INSERT` or `UPDATE` within a transaction.
8. On commit, RDS writes the row (WAL written, replicated if Multi-AZ is enabled).
9. FastAPI returns the created/updated resource; Mangum/API Gateway relay it to the browser.

### Frontend asset load

1. Browser requests `https://d1234abcdef.cloudfront.net/index.html`.
2. CloudFront checks its edge cache; cache miss on first load.
3. CloudFront fetches the object from **S3** using the OAC signed request.
4. S3 returns the file; CloudFront caches it at the edge and serves it to the browser.
5. Subsequent requests within the cache TTL are served directly from the CloudFront edge (no S3 call).

---

## Security Considerations

| Concern | Mitigation |
|---------|-----------|
| Database credentials in plaintext | Stored in AWS Secrets Manager; never in environment variables directly |
| RDS publicly accessible | `publicly_accessible = false`; no public IP; security group only allows Lambda SG |
| Lambda → RDS network path | Private subnets only; `lambda_sg` restricts egress to port 5432 within VPC |
| Data at rest | RDS storage encrypted (AES-256); S3 SSE-S3 enabled |
| Data in transit | API Gateway enforces HTTPS; RDS SSL available (add `sslmode=require` to DATABASE_URL) |
| S3 direct access | Public access blocked; CloudFront OAC is the only reader |
| IAM permissions | Lambda role has only VPC execution, CloudWatch Logs, and `GetSecretValue` on one secret |
| CloudFront | Redirects HTTP → HTTPS; supports WAF attachment for OWASP rule sets |
| Secrets rotation | Secrets Manager supports automated rotation — see runbook |

---

## Scaling Considerations

### Lambda concurrency

Lambda scales horizontally: each concurrent request gets its own execution environment. For this HR tool (5–20 employees, light use) the default concurrency limit (1,000 per region) is more than sufficient. If traffic spikes are expected:

- Set **reserved concurrency** to cap DB connections (each Lambda container holds one connection pool).
- Enable **provisioned concurrency** to eliminate cold starts for latency-sensitive endpoints.

### RDS connection limits

`db.t3.micro` supports approximately 85 connections. With Lambda, each execution environment maintains its own connection pool. At high concurrency this can exhaust connections. Mitigations:

1. **RDS Proxy**: add an `aws_db_proxy` resource in Terraform; Lambda connects to the proxy, which multiplexes connections.
2. Reduce `pool_size` in SQLAlchemy's `create_engine` call (e.g., `pool_size=2, max_overflow=1`).
3. Upgrade to a larger RDS instance class for more connections.

### CloudFront caching

Increase `default_ttl` for immutable assets (JS/CSS with content-hashed filenames) to reduce S3 origin requests. Use cache policies and origin request policies in Terraform for fine-grained control.

### Read replicas

For read-heavy workloads, add a `aws_db_instance` read replica and route `SELECT` queries to it via a separate SQLAlchemy engine bound to the replica endpoint.

---

## Alternative: Aurora PostgreSQL Serverless v2

**When to choose Aurora Serverless v2 instead of RDS:**

- Load is highly bursty (e.g., all queries happen during a 2-hour review window, then zero activity for 12 hours).
- You want near-zero cost when idle (Aurora scales to ~0.5 ACU minimum).
- You need instant failover with Aurora's shared storage layer (RPO near-zero).

**How to switch (Terraform):**

Replace `aws_db_instance` with:

```hcl
resource "aws_rds_cluster" "postgres" {
  cluster_identifier      = "${local.name_prefix}-cluster"
  engine                  = "aurora-postgresql"
  engine_version          = "16.2"
  database_name           = var.db_name
  master_username         = var.db_username
  master_password         = var.db_password
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.rds.id]
  storage_encrypted       = true
  skip_final_snapshot     = false
  final_snapshot_identifier = "${local.name_prefix}-final-snapshot"

  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 4.0
  }
}

resource "aws_rds_cluster_instance" "postgres" {
  cluster_identifier = aws_rds_cluster.postgres.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.postgres.engine
  engine_version     = aws_rds_cluster.postgres.engine_version
}
```

Use `aws_rds_cluster.postgres.endpoint` as the `host` in Secrets Manager.

**Trade-offs vs RDS t3.micro:**
- Aurora Serverless v2 minimum ~$0.50/ACU-hour × 0.5 ACU ≈ $18/month at idle, similar to `db.t3.micro` at $15–18/month.
- Aurora adds ~$0.10/GB-month for storage vs gp3 fixed cost.
- Aurora is a better fit if you anticipate significant traffic growth or need Multi-AZ without extra cost.

---

## Alternative: ECS Fargate

**When to choose ECS Fargate instead of Lambda:**

- The API has sustained high traffic (> ~50 req/s continuously) — Lambda idle savings disappear.
- You need WebSocket connections (not supported by API Gateway HTTP API → Lambda).
- Background tasks run longer than 15 minutes.
- You want to eliminate cold-start latency entirely (always-on tasks).
- The existing `Dockerfile` in `backend/` can be used directly — no code changes needed.

**How to switch (Terraform sketch):**

```hcl
resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  container_definitions    = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.api.repository_url}:latest"
    portMappings = [{ containerPort = 8000 }]
    environment = [{ name = "DB_SECRET_ARN", value = aws_secretsmanager_secret.db_credentials.arn }]
  }])
}
```

Replace the Lambda permission and API Gateway integration with an ALB (Application Load Balancer) pointing to the ECS service.

**Trade-offs vs Lambda:**
- Fargate minimum cost: ~$14/month for 0.25 vCPU / 0.5 GB (always running).
- No cold starts; predictable latency.
- Better for containerised apps that benefit from the full server model.

---

## Monitoring

### CloudWatch Dashboards

Recommended metrics to add to a CloudWatch Dashboard:

| Widget | Metric | Statistic | Period |
|--------|--------|-----------|--------|
| Lambda invocations | `AWS/Lambda > Invocations` | Sum | 1 min |
| Lambda errors | `AWS/Lambda > Errors` | Sum | 1 min |
| Lambda duration (p99) | `AWS/Lambda > Duration` | p99 | 5 min |
| Lambda cold starts | `AWS/Lambda > InitDuration` | Sum | 5 min |
| API Gateway 4xx | `AWS/ApiGateway > 4XXError` | Sum | 1 min |
| API Gateway 5xx | `AWS/ApiGateway > 5XXError` | Sum | 1 min |
| RDS CPU | `AWS/RDS > CPUUtilization` | Average | 5 min |
| RDS connections | `AWS/RDS > DatabaseConnections` | Maximum | 5 min |
| RDS free storage | `AWS/RDS > FreeStorageSpace` | Minimum | 1 h |

### RDS Performance Insights

Performance Insights is enabled in the Terraform config (`performance_insights_enabled = true`). Use it to:
- Identify top SQL queries by wait time.
- Spot N+1 query patterns from SQLAlchemy.
- Monitor `db load` (average active sessions).

### Lambda Insights

Enable Lambda Insights for OS-level metrics (memory utilisation, CPU steal, network):

```hcl
resource "aws_lambda_function" "api" {
  # ...existing config...
  layers = ["arn:aws:lambda:eu-west-1:580247275435:layer:LambdaInsightsExtension:38"]

  environment {
    variables = {
      # ...existing vars...
    }
  }
}

resource "aws_iam_role_policy_attachment" "lambda_insights" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchLambdaInsightsExecutionRolePolicy"
}
```

### CloudWatch Alarms

Recommended alarms for production:

```hcl
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${local.name_prefix}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  alarm_actions       = [aws_sns_topic.alerts.arn]
  dimensions = {
    FunctionName = aws_lambda_function.api.function_name
  }
}
```

---

## Estimated AWS Cost Breakdown

Costs based on eu-west-1 (Ireland) pricing (May 2026, on-demand). Light use: 5–20 employees, ~10,000 API requests/month.

| Service | Configuration | Unit Price | Monthly Est. |
|---------|--------------|------------|-------------|
| RDS PostgreSQL | db.t3.micro, 20 GB gp3, single-AZ, 730 h/month | $0.022/h + $0.115/GB-month | ~$16.30 |
| Lambda | 512 MB, 10k req, 1 s avg duration | $0.20/1M req + $0.0000166667/GB-s | < $0.01 |
| API Gateway HTTP API | 10k requests | $1.00/1M req | < $0.02 |
| S3 (frontend) | 10 MB storage, 1k GET | $0.023/GB-month + $0.004/10k GET | < $0.01 |
| CloudFront | 1 GB transfer, 10k req | $0.085/GB + $0.01/10k HTTPS | ~$0.19 |
| Secrets Manager | 1 secret | $0.40/secret/month | $0.40 |
| NAT Gateway | 1 AZ, minimal data processing | $0.045/h + $0.045/GB | ~$32.90 |
| CloudWatch Logs | ~500 MB/month | $0.57/GB ingestion | ~$0.29 |
| **Total** | | | **~$50/month** |

**Dominant cost**: NAT Gateway at ~$33/month. To reduce this:
- Remove NAT Gateway and use VPC endpoints for Secrets Manager (`aws_vpc_endpoint` type `Interface`). This avoids internet-bound traffic from Lambda while keeping RDS in private subnets.
- Alternatively, keep Lambda outside the VPC and use an RDS Proxy or RDS Proxy endpoint with TLS to connect securely from Lambda without a VPC requirement.
