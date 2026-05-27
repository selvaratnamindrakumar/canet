# ============================================================
# Multi-Stage Dockerfile — Cloud Readiness Demo
# ============================================================
#
# STAGE 1 — Build
#   Uses the full Maven + JDK image only during build.
#   The compiled artifact is extracted in layers so Docker
#   can cache unchanged layers (dependencies rarely change).
#
# STAGE 2 — Runtime
#   Copies only the application layers into a minimal JRE
#   image, reducing the final image size and attack surface.
#
# AWS Note:
#   This image is designed to be pushed to Amazon ECR and run
#   on ECS Fargate or EC2 instances.
#   Tag pattern: <account>.dkr.ecr.<region>.amazonaws.com/cloud-readiness-demo:<version>
# ============================================================

# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# Copy the POM first — Docker caches this layer until pom.xml changes,
# avoiding a full dependency download on every code change.
COPY pom.xml .

# Download dependencies (cached as a separate layer)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml package -DskipTests -q

# Extract Spring Boot layered JAR
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /workspace/layers

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy Spring Boot layers in order of change frequency
# (least volatile → most volatile) for optimal layer caching
COPY --from=builder /workspace/layers/dependencies/ ./
COPY --from=builder /workspace/layers/spring-boot-loader/ ./
COPY --from=builder /workspace/layers/snapshot-dependencies/ ./
COPY --from=builder /workspace/layers/application/ ./

# ── Port & Health check ───────────────────────────────────────
EXPOSE 8080

# Docker / ECS native health check
# ECS also uses the /actuator/health/liveness and /readiness probes
# configured in the task definition healthCheck block.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# ── Environment defaults (overridden by ECS task definition) ──
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    APP_ENVIRONMENT=production \
    APP_VERSION=1.0.0 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# ── Entrypoint ────────────────────────────────────────────────
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
