# =============================================================================
#  Multi-stage build – Generic Message Forwarder
# =============================================================================

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build the fat jar
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# Non-root user for security
RUN groupadd -r forwarder && useradd -r -g forwarder forwarder

WORKDIR /app

# Application jar
COPY --from=build /workspace/target/generic-forwarder-*.jar app.jar

# Default directories (override via env / volume mounts)
RUN mkdir -p /data/incoming /certs && \
    chown -R forwarder:forwarder /app /data /certs

USER forwarder

# Expose management port (optional – add spring-boot-actuator to enable)
EXPOSE 8080

# JVM tuning defaults – override via JAVA_OPTS env variable
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
