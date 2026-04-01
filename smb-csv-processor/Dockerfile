# =============================================================================
# Multi-stage Dockerfile — SMB CSV Processor
#
# Stage 1 (builder): compiles and packages the fat JAR using Maven
# Stage 2 (runtime): minimal JRE image with non-root user
#
# Build (with version):
#   docker build --build-arg APP_VERSION=1.2.3 -t smb-csv-processor:1.2.3 .
#   docker build -t smb-csv-processor:latest .          # version defaults to "dev"
#
# Or use the provided build scripts which extract the version from pom.xml:
#   ./docker-build.sh          (Linux/macOS)
#   docker-build.bat           (Windows)
#
# Run (production):
#   docker run -d \
#     -v /srv/processor/data:/data/processor \
#     -v /srv/processor/logs:/var/log/smb-csv-processor \
#     -v /srv/processor/ssh:/opt/app/.ssh:ro \
#     -e SMB_HOST=myserver \
#     -e SMB_PASSWORD=secret \
#     smb-csv-processor:1.2.3
#
# Run (test mode — no SMB/SFTP required):
#   docker run -d \
#     -v /srv/processor/data:/data/processor \
#     -v /srv/processor/logs:/var/log/smb-csv-processor \
#     -e PROCESSING_TEST-MODE_ENABLED=true \
#     smb-csv-processor:1.2.3
# =============================================================================

# Version passed in at build time: docker build --build-arg APP_VERSION=1.2.3
ARG APP_VERSION=dev

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM eclipse-temurin:11-jdk-jammy AS builder

WORKDIR /build

# Copy dependency descriptors first so Docker caches the layer
COPY pom.xml .
RUN mvn -B dependency:go-offline -q 2>/dev/null || true

# Copy source and build fat JAR (skip tests in build stage)
COPY src ./src
RUN mvn -B -DskipTests package -q

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:11-jre-jammy AS runtime

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="smb-csv-processor" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.description="SMB → CSV processing pipeline with IE/AA export"

# Create a non-root user for the application
RUN groupadd --gid 1001 csvproc \
 && useradd  --uid 1001 --gid csvproc --no-create-home --shell /sbin/nologin csvproc

# Application directories
RUN mkdir -p \
      /data/processor/input/zip \
      /data/processor/input/csv \
      /data/processor/output/success \
      /data/processor/output/quarantine \
      /data/processor/output/archive \
      /data/processor/test-drop \
      /var/log/smb-csv-processor \
      /opt/app/.ssh \
 && chown -R csvproc:csvproc \
      /data/processor \
      /var/log/smb-csv-processor \
      /opt/app

WORKDIR /opt/app

# Copy fat JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar
RUN chown csvproc:csvproc app.jar

# Switch to non-root user
USER csvproc

# Persistent volumes — override in docker run / docker-compose
VOLUME ["/data/processor", "/var/log/smb-csv-processor", "/opt/app/.ssh"]

# JVM tuning: container-aware heap limits, GC logging, timezone
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Duser.timezone=Europe/London"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/app/app.jar"]
