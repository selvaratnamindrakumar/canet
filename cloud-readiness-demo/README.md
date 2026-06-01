# Cloud Readiness Demo — Spring Boot + Docker + AWS
## NEO Cloud Readiness PoC

> Spring Boot 3.3 application demonstrating cloud-native migration patterns:
> containerisation via Docker multi-stage build **and Jib (no Docker daemon required)**,
> AWS ECS Fargate deployment, health probes, observability, and GraalVM Native Image generation.

---

## Achievement Summary

| Capability | Status |
|---|---|
| REST API service | ✅ Implemented & tested |
| Docker/containerisation (multi-stage) | ✅ Implemented |
| Jib container image (no Docker daemon) | ✅ Implemented |
| Health, liveness & readiness probes | ✅ Via Spring Actuator |
| AWS deployment assets (ECR/ECS/Fargate) | ✅ Scripts + task definition |
| Monitoring (Prometheus + Grafana) | ✅ Local Docker Compose stack |
| Cloud migration documentation | ✅ Full guide + deployment notes |
| GraalVM Native Image profile | ✅ Configured (`-Pnative`) |
| 12-Factor App compliance | ✅ All 12 factors |

---

## Quick Start

### Option 1 — Maven (no Docker, no container tools)

```bash
cd cloud-readiness-demo
mvn spring-boot:run -Dspring.profiles.active=local
```

### Option 2 — Build executable JAR

```bash
mvn clean package -DskipTests
java -jar target/cloud-readiness-demo-1.0.0.jar
```

### Option 3 — Jib container tar (no Docker daemon)

```bash
# Builds a tar file — no Docker installation required
mvn clean package jib:buildTar -DskipTests
# Output: target/jib-image.tar
```

### Option 4 — Jib direct push to ECR (no Docker daemon, recommended for AWS)

```bash
# Windows
set ECR_PASSWORD=<aws ecr get-login-password --region us-east-1>
mvn jib:build ^
  -Djib.to.image=<ACCOUNT>.dkr.ecr.us-east-1.amazonaws.com/cloud-readiness-demo:1.0.0 ^
  -Djib.to.auth.username=AWS ^
  -Djib.to.auth.password=%ECR_PASSWORD%

# Linux/Mac (uses included script)
./aws/jib-ecr-push.sh 1.0.0
```

### Option 5 — Full local stack (Docker Compose)

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Application | http://localhost:8080 |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |

---

## API Reference

| Method | URL | Description |
|---|---|---|
| GET | /api/v1/tasks | List all tasks |
| GET | /api/v1/tasks/{id} | Get task by id |
| POST | /api/v1/tasks | Create task |
| PUT | /api/v1/tasks/{id} | Update task |
| DELETE | /api/v1/tasks/{id} | Delete task |
| GET | /api/v1/info | Runtime environment info |

## Health & Observability

| Endpoint | Purpose | Used by |
|---|---|---|
| GET /actuator/health | Overall health | Monitoring |
| GET /actuator/health/liveness | JVM alive? | ECS health check |
| GET /actuator/health/readiness | Ready for traffic? | ALB target group |
| GET /actuator/prometheus | Prometheus scrape | CloudWatch/Grafana |
| GET /actuator/info | Build & env metadata | Dashboards |

---

## Container Image Build Options

### Jib (No Docker Daemon Required)

```bash
# Build tar file for transfer
mvn jib:buildTar

# Push directly to ECR (no Docker needed!)
mvn jib:build -Djib.to.image=<ECR-URI>

# Load into local Docker (if available)
mvn jib:dockerBuild
```

### Docker Multi-Stage Build (Docker required)

```bash
docker build -t cloud-readiness-demo:1.0.0 .
docker run -p 8080:8080 cloud-readiness-demo:1.0.0
```

---

## GraalVM Native Image

The project includes a `native` Maven profile for native compilation:

```bash
# Compile to native executable (requires GraalVM JDK 17+)
mvn -Pnative native:compile

# Build native OCI image via Paketo buildpacks (requires Docker)
mvn -Pnative spring-boot:build-image
```

Expected native image benefits:

| Metric | JVM Image | Native Image |
|---|---|---|
| Startup time | 4–8 seconds | < 0.5 seconds |
| Image size | ~280 MB | ~90 MB |
| Memory (idle) | ~220 MB | ~45 MB |

> See **[docs/NATIVE_IMAGE_GUIDE.md](docs/NATIVE_IMAGE_GUIDE.md)** for full setup steps,
> including how to build on EC2 without GraalVM on the Windows workstation.

---

## AWS Sandbox Deployment

### Recommended path (no Docker on Windows)

```bash
# 1. Push image to ECR via Jib (no Docker daemon needed)
./aws/jib-ecr-push.sh 1.0.0

# 2. Deploy to ECS Fargate
./aws/deploy-ecs.sh cloud-readiness-cluster cloud-readiness-svc
```

> See **[docs/DEPLOYMENT_NOTES.md](docs/DEPLOYMENT_NOTES.md)** for complete step-by-step
> AWS Sandbox testing guide including four deployment paths, SSL fix for enterprise environments,
> and a validation test plan.

---

## Project Structure

```
cloud-readiness-demo/
├── aws/
│   ├── ecr-push.sh               # Docker-based ECR push
│   ├── jib-ecr-push.sh           # Jib ECR push (no Docker needed)
│   ├── deploy-ecs.sh             # ECS deploy script
│   └── ecs-task-definition.json  # Fargate task definition
├── config/                        # Prometheus + Grafana config
├── docs/
│   ├── CLOUD_MIGRATION_GUIDE.md  # Full migration guide
│   ├── DEPLOYMENT_NOTES.md       # AWS Sandbox testing guide
│   └── NATIVE_IMAGE_GUIDE.md     # GraalVM native image guide
├── src/main/java/com/company/demo/
│   ├── CloudReadinessApplication.java
│   ├── config/AppConfig.java         # Factor III: externalised config
│   ├── controller/                   # REST API endpoints
│   ├── exception/                    # RFC 7807 error handling
│   ├── health/                       # Custom Actuator probe
│   ├── model/                        # Task domain model
│   └── service/TaskService.java
├── src/main/resources/
│   ├── application.yml               # Base config (all profiles)
│   ├── application-local.yml         # Developer workstation
│   └── application-prod.yml          # Production (ECS Fargate)
├── Dockerfile                        # Multi-stage Docker build
├── docker-compose.yml                # Local dev stack
└── pom.xml                           # Jib plugin + native profile
```

---

## Documentation

| Document | Description |
|---|---|
| [CLOUD_MIGRATION_GUIDE.md](docs/CLOUD_MIGRATION_GUIDE.md) | Architecture, AWS walkthrough, 12-Factor compliance, migration roadmap, cost estimate |
| [DEPLOYMENT_NOTES.md](docs/DEPLOYMENT_NOTES.md) | AWS Sandbox testing guide, 4 deployment paths, SSL fix, validation checklist |
| [NATIVE_IMAGE_GUIDE.md](docs/NATIVE_IMAGE_GUIDE.md) | GraalVM setup on EC2, benchmark template, comparison with JVM image |

---

## Running Tests

```bash
mvn test
```
