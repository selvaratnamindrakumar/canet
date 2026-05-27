# Cloud Readiness Migration Guide
## Spring Boot → Docker → AWS  |  Proof of Concept

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)  
2. [Architecture Overview](#2-architecture-overview)  
3. [Cloud Readiness Assessment — 12-Factor App](#3-cloud-readiness-assessment--12-factor-app)  
4. [Project Structure](#4-project-structure)  
5. [Running Locally](#5-running-locally)  
6. [Docker Build & Deployment](#6-docker-build--deployment)  
7. [AWS Deployment Walkthrough](#7-aws-deployment-walkthrough)  
8. [Health Checks & Observability](#8-health-checks--observability)  
9. [Configuration Management](#9-configuration-management)  
10. [Security Considerations](#10-security-considerations)  
11. [Migration Roadmap](#11-migration-roadmap)  
12. [Cost Estimate](#12-cost-estimate)  

---

## 1. Executive Summary

This Proof of Concept demonstrates the migration path for a traditional Spring Boot monolith into a **cloud-native, Docker-packaged** service running on **AWS ECS Fargate**.

| Dimension             | Before (on-prem)             | After (cloud-native Docker)           |
|-----------------------|------------------------------|---------------------------------------|
| Deployment            | Manual WAR/JAR deploy        | Docker image → ECR → ECS Fargate      |
| Configuration         | Hard-coded properties files  | Environment variables + Secrets Mgr   |
| Scaling               | Manual VM provisioning       | ECS auto-scaling (CPU/memory metrics) |
| Health monitoring     | Nagios / manual checks       | ALB health checks + CloudWatch Alarms |
| Observability         | File-based logs              | CloudWatch Logs Insights + Grafana    |
| Secrets management    | Config files in SCM          | AWS Secrets Manager + IAM             |
| Infrastructure        | Long-lived VMs               | Ephemeral containers (Fargate)        |
| Startup time          | 2–5 min (JVM + app server)   | 30–60 s (Spring Boot fat JAR)         |

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        AWS Cloud                            │
│                                                             │
│  ┌──────────┐     ┌───────────────────────────────────┐    │
│  │  Route53  │────▶│  Application Load Balancer (ALB)  │    │
│  └──────────┘     └─────────────┬─────────────────────┘    │
│                                 │                           │
│                   ┌─────────────▼─────────────────────┐    │
│                   │         ECS Cluster                │    │
│                   │  ┌──────────┐  ┌──────────┐        │    │
│                   │  │ Fargate  │  │ Fargate  │  ...   │    │
│                   │  │ Task     │  │ Task     │        │    │
│                   │  │          │  │          │        │    │
│                   │  │ [Docker] │  │ [Docker] │        │    │
│                   │  │ Spring   │  │ Spring   │        │    │
│                   │  │ Boot App │  │ Boot App │        │    │
│                   │  └────┬─────┘  └────┬─────┘        │    │
│                   └───────┼─────────────┼──────────────┘    │
│                           │             │                    │
│         ┌─────────────────┴──┐    ┌────┴──────────┐        │
│         │   Amazon ECR       │    │  CloudWatch    │        │
│         │  (Container Reg.)  │    │  Logs/Metrics  │        │
│         └────────────────────┘    └───────────────┘        │
│                                                             │
│         ┌──────────────────────────────────────┐           │
│         │  Amazon DynamoDB / RDS (future)       │           │
│         └──────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

### Local Development Stack (Docker Compose)

```
localhost
├── :8080  Spring Boot App
├── :4566  LocalStack  (S3, SQS, DynamoDB, SSM, Secrets Manager)
├── :9090  Prometheus
└── :3000  Grafana
```

---

## 3. Cloud Readiness Assessment — 12-Factor App

The [12-Factor App methodology](https://12factor.net/) is the industry standard checklist for cloud-native applications.

| Factor | Description | Status | Implementation |
|--------|-------------|--------|----------------|
| I. Codebase | One codebase, many deploys | ✅ | Git repository |
| II. Dependencies | Explicitly declared | ✅ | `pom.xml` with pinned versions |
| III. Config | Store config in environment | ✅ | `application.yml` + ENV vars |
| IV. Backing services | Treat as attached resources | ✅ | DB URL via ENV, swappable |
| V. Build/release/run | Strictly separated stages | ✅ | Multi-stage Dockerfile |
| VI. Processes | Execute as stateless processes | ✅ | No in-process session state |
| VII. Port binding | Export services via port binding | ✅ | `SERVER_PORT` env var |
| VIII. Concurrency | Scale out via process model | ✅ | ECS task count scaling |
| IX. Disposability | Fast startup, graceful shutdown | ✅ | `server.shutdown=graceful` |
| X. Dev/prod parity | Keep dev and prod similar | ✅ | Docker Compose mirrors ECS |
| XI. Logs | Treat logs as event streams | ✅ | stdout → CloudWatch Logs |
| XII. Admin processes | Run admin tasks as one-off processes | ✅ | ECS `run-task` for migrations |

**Score: 12/12 ✅**

---

## 4. Project Structure

```
cloud-readiness-demo/
├── aws/
│   ├── ecr-push.sh                   # Build & push image to ECR
│   ├── deploy-ecs.sh                 # Register task def & update service
│   └── ecs-task-definition.json      # ECS Fargate task definition template
│
├── config/
│   ├── prometheus.yml                # Prometheus scrape config
│   └── grafana/provisioning/         # Grafana auto-provisioning
│
├── docs/
│   └── CLOUD_MIGRATION_GUIDE.md      # This document
│
├── src/
│   └── main/
│       ├── java/com/company/demo/
│       │   ├── CloudReadinessApplication.java  # Entry point
│       │   ├── config/AppConfig.java           # Config & environment
│       │   ├── controller/
│       │   │   ├── TaskController.java          # REST CRUD API
│       │   │   └── InfoController.java          # Runtime environment info
│       │   ├── exception/
│       │   │   ├── TaskNotFoundException.java
│       │   │   └── GlobalExceptionHandler.java  # RFC 7807 error responses
│       │   ├── health/
│       │   │   └── TaskStoreHealthIndicator.java # Custom actuator probe
│       │   ├── model/
│       │   │   ├── Task.java
│       │   │   └── TaskStatus.java
│       │   └── service/TaskService.java          # Business logic
│       └── resources/
│           ├── application.yml        # Base config (all profiles)
│           ├── application-local.yml  # Developer workstation overrides
│           └── application-prod.yml   # Production (ECS) overrides
│
├── .dockerignore
├── Dockerfile                        # Multi-stage, layered JAR build
├── docker-compose.yml                # Full local dev stack
└── pom.xml
```

---

## 5. Running Locally

### Prerequisites

| Tool    | Minimum version | Check command        |
|---------|-----------------|----------------------|
| Java    | 17              | `java -version`      |
| Maven   | 3.8             | `mvn -version`       |
| Docker  | 24              | `docker version`     |
| AWS CLI | 2.x             | `aws --version`      |

### Option A — Maven (no Docker)

```bash
# Clone the repository
git clone https://github.com/selvaratnamindrakumar/canet.git
cd canet/cloud-readiness-demo

# Run with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Or with Maven wrapper not present:
mvn spring-boot:run -Dspring.profiles.active=local
```

Application starts at **http://localhost:8080**

### Option B — Full Docker Compose stack

```bash
cd canet/cloud-readiness-demo

# Build and start everything
docker compose up --build

# Start application only (skip monitoring stack)
docker compose up app localstack --build

# Stop and remove containers
docker compose down -v
```

### Verifying the application

```bash
# List tasks (pre-seeded demo data)
curl -s http://localhost:8080/api/v1/tasks | jq .

# Create a new task
curl -s -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "My first cloud task", "description": "Testing the PoC"}' | jq .

# Runtime environment info
curl -s http://localhost:8080/api/v1/info | jq .

# Health check
curl -s http://localhost:8080/actuator/health | jq .

# Liveness probe
curl -s http://localhost:8080/actuator/health/liveness | jq .

# Readiness probe
curl -s http://localhost:8080/actuator/health/readiness | jq .

# Prometheus metrics
curl -s http://localhost:8080/actuator/prometheus | grep http_server

# Open Grafana dashboard
open http://localhost:3000  # admin / admin
```

---

## 6. Docker Build & Deployment

### Multi-Stage Build Explained

```dockerfile
# Stage 1: Build (full JDK + Maven)
FROM eclipse-temurin:17-jdk-alpine AS builder
  → Downloads dependencies (cached layer)
  → Compiles and packages JAR
  → Extracts Spring Boot layers

# Stage 2: Runtime (minimal JRE only)
FROM eclipse-temurin:17-jre-alpine AS runtime
  → Copies only the application layers
  → Runs as non-root user
  → Final image ≈ 180 MB vs 400+ MB single-stage
```

### Spring Boot Layered JAR

The layered JAR separates the application into four layers, ordered from least to most frequently changed:

```
Layer 1: dependencies        (~120 MB) — only changes when pom.xml changes
Layer 2: spring-boot-loader  (~2 MB)   — only changes with Spring Boot upgrades  
Layer 3: snapshot-dependencies(~0 MB)  — SNAPSHOT libs only
Layer 4: application         (~1 MB)   — changes on every code change
```

**Result:** On a typical code change, only **Layer 4** (~1 MB) needs to be re-pulled by ECS tasks, not the full 180 MB image.

### Manual Docker Commands

```bash
# Build image
docker build -t cloud-readiness-demo:1.0.0 .

# Run with environment variables (mimics ECS)
docker run -d \
  --name cloud-readiness-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e APP_ENVIRONMENT=docker-local \
  -e APP_VERSION=1.0.0 \
  cloud-readiness-demo:1.0.0

# View logs
docker logs -f cloud-readiness-app

# Check health
docker inspect --format='{{json .State.Health}}' cloud-readiness-app | jq .
```

---

## 7. AWS Deployment Walkthrough

### Step 1 — Create ECR Repository

```bash
aws ecr create-repository \
  --repository-name cloud-readiness-demo \
  --image-scanning-configuration scanOnPush=true \
  --encryption-configuration encryptionType=AES256 \
  --region us-east-1
```

### Step 2 — Push Docker Image to ECR

```bash
chmod +x aws/ecr-push.sh
./aws/ecr-push.sh 1.0.0
```

The script:
1. Retrieves your AWS account ID automatically
2. Authenticates Docker to ECR
3. Builds the image with the multi-stage Dockerfile
4. Tags and pushes with both `1.0.0` and `latest` tags

### Step 3 — Create ECS Cluster

```bash
aws ecs create-cluster \
  --cluster-name cloud-readiness-cluster \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy \
    capacityProvider=FARGATE_SPOT,weight=1,base=1 \
  --region us-east-1
```

### Step 4 — Create CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/cloud-readiness-demo \
  --region us-east-1
```

### Step 5 — Deploy Task Definition and Service

```bash
# Edit aws/ecs-task-definition.json to replace ACCOUNT_ID and REGION
# then run:
chmod +x aws/deploy-ecs.sh
./aws/deploy-ecs.sh cloud-readiness-cluster cloud-readiness-svc
```

### Step 6 — Create Application Load Balancer

```bash
# Create target group pointing to port 8080
aws elbv2 create-target-group \
  --name cloud-readiness-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id <YOUR_VPC_ID> \
  --target-type ip \
  --health-check-path /actuator/health \
  --health-check-interval-seconds 30 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3
```

### Step 7 — Create ECS Service with ALB

```bash
aws ecs create-service \
  --cluster cloud-readiness-cluster \
  --service-name cloud-readiness-svc \
  --task-definition cloud-readiness-demo:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration \
    "awsvpcConfiguration={subnets=[<SUBNET_1>,<SUBNET_2>],securityGroups=[<SG_ID>],assignPublicIp=ENABLED}" \
  --load-balancers \
    "targetGroupArn=<TARGET_GROUP_ARN>,containerName=cloud-readiness-demo,containerPort=8080" \
  --enable-execute-command
```

### Step 8 — Configure Auto-Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/cloud-readiness-cluster/cloud-readiness-svc \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Scale on CPU utilisation > 70%
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/cloud-readiness-cluster/cloud-readiness-svc \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name scale-on-cpu \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration \
    'TargetValue=70.0,PredefinedMetricSpecification={PredefinedMetricType=ECSServiceAverageCPUUtilization}'
```

### Verify Deployment

```bash
# Wait for service to be stable
aws ecs wait services-stable \
  --cluster cloud-readiness-cluster \
  --services cloud-readiness-svc

# Describe running tasks
aws ecs describe-tasks \
  --cluster cloud-readiness-cluster \
  --tasks $(aws ecs list-tasks \
    --cluster cloud-readiness-cluster \
    --service-name cloud-readiness-svc \
    --query taskArns --output text)
```

---

## 8. Health Checks & Observability

### Health Endpoints

| Endpoint                          | Purpose                          | Used by        |
|-----------------------------------|----------------------------------|----------------|
| `GET /actuator/health`            | Overall application health       | Monitoring     |
| `GET /actuator/health/liveness`   | Is the JVM alive?                | ECS container health check |
| `GET /actuator/health/readiness`  | Ready to receive traffic?        | ALB target group health check |
| `GET /actuator/health/taskStore`  | Custom backing store health      | Application ops |
| `GET /actuator/prometheus`        | Prometheus metrics scrape        | CloudWatch / Grafana |
| `GET /actuator/info`              | Build and environment info       | Deployment dashboards |

### Sample Health Response

```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "taskStore": {
      "status": "UP",
      "details": {
        "type": "in-memory",
        "taskCount": 3,
        "note": "Replace with DynamoDB/RDS probe in production"
      }
    }
  }
}
```

### CloudWatch Metrics

The app exposes Prometheus metrics at `/actuator/prometheus`. To ingest into CloudWatch:

1. **Option A**: Use the CloudWatch Agent with a Prometheus config in your ECS task.
2. **Option B**: Use Amazon Managed Service for Prometheus (AMP) + Managed Grafana.

Key metrics to alert on:

| Metric                                        | Alert threshold  |
|-----------------------------------------------|------------------|
| `http_server_requests_seconds_max`            | p99 > 2 s        |
| `jvm_memory_used_bytes{area="heap"}`          | > 80% of max     |
| `process_cpu_usage`                           | > 0.8            |
| `http_server_requests_total{status="5xx"}`    | > 1% error rate  |

### CloudWatch Logs Insights Queries

```sql
-- Top 10 slowest API requests in the last hour
fields @timestamp, @message
| filter @message like /http/
| parse @message '"message":"*"' as msg
| stats avg(duration) as avg_ms by endpoint
| sort avg_ms desc
| limit 10

-- Error rate by endpoint
fields @timestamp, @message
| filter @message like /ERROR/
| stats count() as errors by bin(5m)
| sort @timestamp desc
```

---

## 9. Configuration Management

### Environment Variables Reference

| Variable                   | Default         | Description                            |
|----------------------------|-----------------|----------------------------------------|
| `SPRING_PROFILES_ACTIVE`   | `local`         | Spring profile (`local`, `prod`)       |
| `SERVER_PORT`              | `8080`          | HTTP port                              |
| `APP_ENVIRONMENT`          | `local`         | Environment label (shown in /info)     |
| `APP_VERSION`              | `1.0.0`         | Application version                    |
| `AWS_DEFAULT_REGION`       | `us-east-1`     | AWS region                             |
| `JAVA_OPTS`                | (see Dockerfile)| JVM flags                              |

### Secrets Management (AWS)

For production secrets, **never** use environment variables in plain text. Use AWS Secrets Manager:

```bash
# Create a secret
aws secretsmanager create-secret \
  --name prod/cloud-readiness-demo/db-password \
  --secret-string "SuperSecretPassword123!"

# Reference in ECS task definition (see ecs-task-definition.json)
"secrets": [
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:prod/cloud-readiness-demo/db-password"
  }
]
```

### Parameter Store for Non-Sensitive Config

```bash
aws ssm put-parameter \
  --name /prod/cloud-readiness-demo/db-host \
  --value "mydb.cluster-xyz.us-east-1.rds.amazonaws.com" \
  --type String
```

---

## 10. Security Considerations

### Container Security

| Control                      | Implementation                          |
|------------------------------|-----------------------------------------|
| Non-root user                | `USER appuser` in Dockerfile            |
| Minimal base image           | `eclipse-temurin:17-jre-alpine`         |
| No sensitive env in image    | All secrets via ECS task definition     |
| ECR image scanning           | `--image-scanning-configuration scanOnPush=true` |
| Read-only root filesystem    | `readonlyRootFilesystem: true` in task def (future) |

### IAM Least Privilege

Create a dedicated task role with only the permissions the app needs:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "ssm:GetParameter",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:prod/cloud-readiness-demo/*",
        "arn:aws:ssm:us-east-1:ACCOUNT:parameter/prod/cloud-readiness-demo/*",
        "arn:aws:logs:us-east-1:ACCOUNT:log-group:/ecs/cloud-readiness-demo:*"
      ]
    }
  ]
}
```

### Network Security

- Place ECS tasks in **private subnets** — no public IPs on containers
- ALB in public subnets, security group allows only 80/443 inbound
- Container security group allows inbound **only from ALB security group** on port 8080
- Use AWS VPC endpoints for ECR, Secrets Manager, and SSM to avoid public internet traffic

---

## 11. Migration Roadmap

This PoC represents **Phase 1** of a typical migration journey:

```
Phase 1 — Containerise (this PoC)          [Weeks 1–2]
  ✅ Create Dockerfile (multi-stage)
  ✅ Externalise configuration
  ✅ Add health probes via Spring Actuator
  ✅ Add structured logging & Prometheus metrics
  ✅ Local Docker Compose environment
  ✅ ECS Fargate deployment scripts

Phase 2 — Data layer migration             [Weeks 3–4]
  ☐ Replace in-memory store with Amazon DynamoDB
     (swap TaskService → DynamoDbTaskRepository)
  ☐ Add Spring Cloud AWS for DynamoDB/S3 integration
  ☐ Database migration scripts via ECS one-off tasks

Phase 3 — CI/CD Pipeline                  [Weeks 5–6]
  ☐ GitHub Actions workflow:
     build → test → docker build → ECR push → ECS deploy
  ☐ Blue/green deployment via CodeDeploy + ECS
  ☐ Automated rollback on health check failure

Phase 4 — Advanced Observability           [Week 7]
  ☐ Distributed tracing with AWS X-Ray
  ☐ Structured log correlation IDs (MDC)
  ☐ CloudWatch Container Insights
  ☐ PagerDuty / SNS alerting

Phase 5 — Cost Optimisation               [Week 8]
  ☐ Fargate Spot for non-production workloads
  ☐ Auto-scaling policies tuned by load testing
  ☐ Reserved capacity for predictable baseline load
```

---

## 12. Cost Estimate

*Estimates based on `us-east-1` pricing, 2 tasks running 24×7.*

| Service              | Configuration              | Monthly est. (USD) |
|----------------------|----------------------------|--------------------|
| ECS Fargate          | 2 × (0.5 vCPU / 1 GB)     | ~$29               |
| Application LB       | 1 ALB + ~1 GB data         | ~$17               |
| ECR                  | ~500 MB storage            | ~$0.05             |
| CloudWatch Logs      | ~5 GB/month                | ~$2.50             |
| CloudWatch Metrics   | 10 custom metrics          | ~$3                |
| **Total**            |                            | **~$52 / month**   |

*Cost optimisation: Use **Fargate Spot** for dev/test to save 70%.*

---

## References

- [Spring Boot Actuator Docs](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [12-Factor App](https://12factor.net/)
- [AWS ECS Fargate Getting Started](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-fargate.html)
- [Amazon ECR User Guide](https://docs.aws.amazon.com/AmazonECR/latest/userguide/)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/)
- [Spring Cloud AWS](https://awspring.io/)
- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)

---

*Document version: 1.0.0 | PoC created for AWS Cloud Sandbox demonstration*
