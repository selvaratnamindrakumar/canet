# ☁️ Cloud Readiness Demo — Spring Boot + Docker + AWS

> **Proof of Concept** demonstrating the migration of a Spring Boot application to a cloud-native, Docker-packaged service running on AWS ECS Fargate.

---

## 🚀 Quick Start

```bash
# Option 1: Maven (no Docker required)
mvn spring-boot:run -Dspring.profiles.active=local

# Option 2: Full stack with Docker Compose (includes Prometheus + Grafana)
docker compose up --build
```

Application: **http://localhost:8080**  
Grafana:     **http://localhost:3000** (admin/admin)  
Prometheus:  **http://localhost:9090**

---

## 📋 API Endpoints

| Method | URL                     | Description       |
|--------|-------------------------|-------------------|
| GET    | /api/v1/tasks           | List all tasks    |
| GET    | /api/v1/tasks/{id}      | Get task by id    |
| POST   | /api/v1/tasks           | Create task       |
| PUT    | /api/v1/tasks/{id}      | Update task       |
| DELETE | /api/v1/tasks/{id}      | Delete task       |
| GET    | /api/v1/info            | Runtime info      |

## 🏥 Health & Observability

| Endpoint                          | Purpose                         |
|-----------------------------------|---------------------------------|
| GET /actuator/health              | Overall health                  |
| GET /actuator/health/liveness     | Liveness probe (ECS)            |
| GET /actuator/health/readiness    | Readiness probe (ALB)           |
| GET /actuator/prometheus          | Prometheus metrics              |
| GET /actuator/info                | Build & environment metadata    |

---

## ☁️ Cloud Readiness Features

- ✅ **12-Factor App** compliant (all 12 factors)  
- ✅ **Multi-stage Docker build** with layered JAR (~180 MB final image)  
- ✅ **Graceful shutdown** — drains in-flight requests before exit  
- ✅ **Liveness & Readiness probes** via Spring Actuator  
- ✅ **Prometheus metrics** for CloudWatch / Grafana  
- ✅ **Externalized configuration** via ENV variables  
- ✅ **Non-root container** user for security  
- ✅ **AWS ECS task definition** template included  
- ✅ **ECR push** and **ECS deploy** scripts included  
- ✅ **LocalStack** integration for local AWS simulation  

---

## 📁 Project Structure

```
cloud-readiness-demo/
├── aws/              # ECR push & ECS deploy scripts
├── config/           # Prometheus + Grafana config
├── docs/             # Full migration guide
├── src/main/java/    # Spring Boot application
├── Dockerfile        # Multi-stage build
├── docker-compose.yml
└── pom.xml
```

## 📖 Full Documentation

See **[docs/CLOUD_MIGRATION_GUIDE.md](docs/CLOUD_MIGRATION_GUIDE.md)** for:
- Architecture diagrams
- AWS deployment walkthrough (ECR → ECS → ALB → Auto-scaling)
- 12-Factor compliance details
- Security controls
- Migration roadmap (Phases 1–5)
- Cost estimates (~$52/month for 2-task setup)

---

## 🔧 AWS Deployment

```bash
# 1. Push image to ECR
./aws/ecr-push.sh 1.0.0

# 2. Deploy to ECS Fargate
./aws/deploy-ecs.sh cloud-readiness-cluster cloud-readiness-svc
```

*Requires AWS CLI v2 + appropriate IAM permissions.*

---

## 🧪 Running Tests

```bash
mvn test
```
