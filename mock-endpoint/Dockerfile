# =============================================================================
#  Mock Endpoint — Dockerfile
# =============================================================================

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r mockep && useradd -r -g mockep mockep
WORKDIR /app
COPY --from=build /workspace/target/mock-endpoint-*.jar app.jar
RUN mkdir -p /certs && chown -R mockep:mockep /app /certs
USER mockep

# HTTPS port
EXPOSE 8443

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
