# Cloud Readiness PoC — Summary
## NEO Cloud Readiness Activity | Spring Boot 3.3 + Docker + AWS

---

## Application Overview

The **Cloud Readiness Demo** is a Spring Boot 3.3 REST API application built as a
Proof of Concept to demonstrate the key patterns, tooling, and techniques required
to migrate a traditional Java application to a cloud-native containerised deployment
on AWS.

The application implements a Task Management API as the functional vehicle, keeping
the domain deliberately simple so that attention remains on the cloud-readiness
patterns rather than business logic complexity.

---

## Main Features

### REST API
- Full CRUD Task Management API (`GET`, `POST`, `PUT`, `DELETE`)
- `GET /api/v1/tasks` — list all tasks (pre-seeded with cloud migration demo data)
- `GET /api/v1/tasks/{id}` — retrieve a single task
- `POST /api/v1/tasks` — create a task with bean validation
- `PUT /api/v1/tasks/{id}` — update task title, description, and status
- `DELETE /api/v1/tasks/{id}` — remove a task
- `GET /api/v1/info` — runtime environment info including active profile, region, and cloud readiness feature flags

### Health & Observability
- `GET /actuator/health` — overall application health with custom backing-store indicator
- `GET /actuator/health/liveness` — JVM liveness probe (used by ECS container health check)
- `GET /actuator/health/readiness` — traffic readiness probe (used by ALB target group health check)
- `GET /actuator/prometheus` — Prometheus metrics endpoint (compatible with CloudWatch Container Insights and Grafana)
- `GET /actuator/info` — build metadata, Java version, and OS info

### Error Handling
- Centralised RFC 7807 Problem Details JSON error responses
- Input validation with descriptive field-level error messages
- Structured error logging

---

## How to Run

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Java (Temurin or GraalVM) | 17 |
| Maven | 3.8 |
| AWS CLI (for AWS steps) | 2.x |

### Option 1 — Run directly with Maven

```bash
cd cloud-readiness-demo
mvn spring-boot:run -Dspring.profiles.active=local
```

### Option 2 — Build and run the JAR

```bash
mvn clean package -DskipTests
java -jar target/cloud-readiness-demo-1.0.0.jar
```

### Option 3 — Build Jib container tar (no Docker daemon)

```bash
# Standard (AWS / CI environments)
mvn clean package jib:buildTar -DskipTests

# Windows enterprise (SSL bypass for corporate networks)
mvn clean package jib:buildTar -DskipTests -Djib.allowInsecureRegistries=true

# Output: target/jib-image.tar
```

### Option 4 — GraalVM Native Executable

```bash
# Requires GraalVM JDK 17+ installed (install via SDKMAN on Linux/EC2)
mvn -Pnative native:compile -DskipTests

# Run the native executable directly — no JVM required
./target/cloud-readiness-demo
```

### Option 5 — Full local observability stack (Docker Compose)

```bash
docker compose up --build
```

---

## How to Test

Once the application is running on `http://localhost:8080`:

### Browser / curl test URLs

```bash
# Task API — pre-seeded data visible immediately
curl http://localhost:8080/api/v1/tasks

# Runtime info + cloud readiness feature flags
curl http://localhost:8080/api/v1/info

# Create a task
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Test cloud readiness","description":"NEO PoC validation"}'

# Health check
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### Run automated tests

```bash
mvn test
```

Tests cover: CRUD API, input validation, liveness probe, readiness probe, and cloud readiness feature flags via the info endpoint.

---

## Cloud Readiness Extensions

### 12-Factor App Compliance

All twelve factors of the [12-Factor App methodology](https://12factor.net/) are satisfied:

| Factor | Implementation |
|---|---|
| I. Codebase | Single Git repository, multiple deployment targets |
| II. Dependencies | All dependencies declared in `pom.xml` |
| III. Config | All environment-specific values from ENV variables (no hard-coded config) |
| IV. Backing services | Data store is an attached resource, replaceable with DynamoDB/RDS |
| V. Build/release/run | Separated via Maven build → Jib/Docker image → ECS deployment |
| VI. Processes | Fully stateless — no in-process session state |
| VII. Port binding | Port driven by `SERVER_PORT` environment variable |
| VIII. Concurrency | Scales via ECS task count, not threads |
| IX. Disposability | `server.shutdown=graceful` with 30s drain; fast startup (especially native) |
| X. Dev/prod parity | Docker Compose mirrors ECS task definition; same image in all environments |
| XI. Logs | stdout only → CloudWatch Logs; structured JSON in production profile |
| XII. Admin processes | Database migrations run as one-off ECS tasks |

### Container Image Strategies

Two containerisation approaches were implemented and validated:

**Dockerfile (multi-stage, layered JAR)**
- Standard approach for environments with Docker available
- Multi-stage build: full JDK builder → minimal JRE runtime
- Spring Boot layered JAR separates dependencies from application code for optimal layer caching
- Non-root container user

**Jib Maven Plugin (no Docker daemon required)**
- Builds and pushes OCI-compliant images directly from Maven
- No Docker installation needed on the build machine
- Ideal for enterprise Windows workstations and non-Docker CI environments
- Direct ECR push: `mvn jib:build -Djib.to.image=<ECR-URI>`
- Tar file output for transfer: `mvn jib:buildTar`

### GraalVM Native Image

A `native` Maven profile was added to support ahead-of-time (AOT) compilation:

```bash
mvn -Pnative native:compile      # local GraalVM required
mvn -Pnative spring-boot:build-image  # via Paketo buildpacks (Docker required)
```

Spring Boot 3.3 generates AOT reflection hints automatically, making most of the application compatible with native compilation without manual configuration.

### AWS Deployment Assets

| Asset | Purpose |
|---|---|
| `aws/jib-ecr-push.sh` | Build and push to ECR via Jib — no Docker |
| `aws/ecr-push.sh` | Build and push to ECR via Docker |
| `aws/deploy-ecs.sh` | Register ECS task definition and update service |
| `aws/ecs-task-definition.json` | Fargate task definition with Secrets Manager, health checks, CloudWatch Logs |

### Observability Stack

- **Prometheus** metrics at `/actuator/prometheus` — compatible with CloudWatch Container Insights and Amazon Managed Grafana
- **Spring Actuator** health indicators — liveness and readiness probes mapped to ECS health check and ALB target group health check
- **Structured JSON logging** in production profile — queryable with CloudWatch Logs Insights
- **Local Grafana dashboard** available via Docker Compose for development

---

## Deployment Model Comparison — Observed Results

The following measurements were recorded during PoC testing on a Windows development workstation:

| Deployment Model | Artefact Size | Startup Time | Notes |
|---|---|---|---|
| **JVM JAR** | 25.6 MB | 2.929 sec | Standard `java -jar` execution |
| **Jib Container** | 89.2 MB | Similar to JVM | Full JRE base image + application layers |
| **Native Executable** | 99.7 MB | **0.225 sec** | GraalVM AOT compiled, no JVM required |

### Key Observations

**1. Native executable startup is approximately 13× faster than the JVM deployment.**
Startup reduced from 2.929 seconds to 0.225 seconds — a 92% reduction. This is directly relevant to cloud deployment scenarios such as ECS Fargate Spot (where tasks start and stop frequently) and any future AWS Lambda consideration.

**2. The Jib container image is larger than the JAR but entirely expected.**
The 89.2 MB image bundles the JRE runtime and application layers together. In a container environment this is the correct unit of deployment — the image is self-contained and environment-independent, unlike a JAR which requires a pre-installed JVM on the host.

**3. The native executable is larger than the JAR but smaller than the Jib JVM container.**
At 99.7 MB the native executable includes the runtime it needs (no separate JVM), making it comparable in size to a minimal container image while delivering the fastest startup of all three approaches.

**4. All three deployment models passed health and API validation.**
Liveness, readiness, and all REST endpoints were verified as working across JVM JAR, Jib container, and native executable configurations.

### Deployment Model Selection Guide

| Scenario | Recommended Model | Reason |
|---|---|---|
| Existing app migration (low risk) | JVM Container (Jib) | Familiar runtime, well-understood behaviour |
| New microservice | Native Executable in container | Fastest startup, lowest memory, best cloud economics |
| Fargate Spot workloads | Native Executable | Frequent start/stop makes startup time critical |
| Developer workstation (no Docker) | JVM JAR or Jib tar | No daemon dependency |
| CI/CD pipeline build | Jib direct ECR push | No Docker daemon in build agent needed |

---

## Recommended Next Steps

1. **Deploy and validate in AWS Sandbox** — run the Jib image on ECS Fargate and confirm health probes register with the ALB target group
2. **Install GraalVM on EC2 and repeat native benchmark** — establish baseline figures for the cloud environment (EC2 startup is faster than Windows workstation)
3. **Compare native vs JVM image under load** — use `wrk` or `ab` to test throughput and memory at steady state, not just startup
4. **Document recommended approach** for future NEO cloud-readiness and containerisation activities based on sandbox results

---

*NEO Cloud Readiness PoC | Spring Boot 3.3 | Jib 3.4.3 | GraalVM Native 0.10.3*
