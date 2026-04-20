# Generic Forwarder — Deployment Guide

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build](#2-build)
3. [Configuration Reference](#3-configuration-reference)
4. [Local Development](#4-local-development)
5. [Docker Deployment](#5-docker-deployment)
6. [SSL / TLS Setup](#6-ssl--tls-setup)
7. [Running Multiple Source Instances](#7-running-multiple-source-instances)
8. [Health Checks](#8-health-checks)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Prerequisites

| Tool | Minimum version | Notes |
|---|---|---|
| JDK | 17 | Temurin / OpenJDK |
| Maven | 3.8 | or use `./mvnw` |
| Docker | 24 | for container deployment |
| Docker Compose | V2 | `docker compose` (not `docker-compose`) |
| keytool | JDK bundled | for SSL store generation |

---

## 2. Build

### generic-forwarder

```bash
cd generic-forwarder
mvn clean package -DskipTests
# Output: target/generic-forwarder-1.0.0-SNAPSHOT.jar

# Build Docker image
docker build -t canet/generic-forwarder:latest .
```

### mock-endpoint

```bash
cd mock-endpoint
mvn clean package -DskipTests
# Output: target/mock-endpoint-1.0.0-SNAPSHOT.jar

docker build -t canet/mock-endpoint:latest .
```

---

## 3. Configuration Reference

All properties live in `application.properties` and can be overridden by environment variables (Spring Boot relaxed binding: `source.type` → `SOURCE_TYPE`).

### 3.1 Source selection

```properties
# One of: kafka | rabbitmq | smb | file
source.type=kafka
```

### 3.2 Kafka

```properties
source.kafka.bootstrap-servers=broker1:9092,broker2:9092
source.kafka.topic=forwarder-input
source.kafka.group-id=generic-forwarder
source.kafka.auto-offset-reset=earliest         # earliest | latest
source.kafka.max-poll-records=500
source.kafka.session-timeout-ms=30000
source.kafka.heartbeat-interval-ms=10000
source.kafka.request-timeout-ms=30000
source.kafka.security-protocol=PLAINTEXT        # PLAINTEXT | SSL | SASL_PLAINTEXT | SASL_SSL
source.kafka.sasl-mechanism=PLAIN               # PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512
source.kafka.sasl-jaas-config=org.apache.kafka.common.security.plain.PlainLoginModule \
  required username="user" password="pass";
```

### 3.3 RabbitMQ

```properties
source.rabbitmq.host=rabbitmq.internal
source.rabbitmq.port=5672                       # 5671 for AMQPS
source.rabbitmq.username=forwarder
source.rabbitmq.password=secret
source.rabbitmq.virtual-host=/
source.rabbitmq.queue=forwarder-input
source.rabbitmq.prefetch-count=10
source.rabbitmq.concurrent-consumers=1
source.rabbitmq.max-concurrent-consumers=5
source.rabbitmq.requested-heartbeat=60          # seconds
source.rabbitmq.connection-timeout-ms=10000
source.rabbitmq.recovery-interval-ms=5000
```

### 3.4 SMB

```properties
source.smb.host=fileserver.internal
source.smb.port=445
source.smb.share=DataShare
source.smb.directory=/incoming
source.smb.username=svcaccount
source.smb.password=secret
source.smb.domain=CORP
source.smb.min-version=SMB2
source.smb.max-version=SMB3
source.smb.file-pattern=*.*
source.smb.poll-delay=5000
source.smb.max-files-per-poll=100
source.smb.delete-after-read=true
source.smb.local-staging-directory=/tmp/smb-staging
```

### 3.5 File

```properties
source.file.directory=/data/incoming
source.file.file-pattern=(?i).*\.(log|xml|json|txt|csv|ndjson)$
source.file.poll-delay=5000
source.file.max-files-per-poll=50
source.file.done-directory=.done               # empty = delete after forwarding
source.file.max-file-size-bytes=104857600      # 100 MB limit; 0 = unlimited
```

### 3.6 Input SSL (Kafka / RabbitMQ)

```properties
source.ssl.enabled=true
source.ssl.truststore-path=/certs/input-truststore.p12
source.ssl.truststore-password=changeit
source.ssl.truststore-type=PKCS12
source.ssl.keystore-path=/certs/input-keystore.p12  # only for mTLS
source.ssl.keystore-password=changeit
source.ssl.keystore-type=PKCS12
```

### 3.7 Endpoint

```properties
endpoint.url=https://api.example.com/ingest
endpoint.feed=my-feed
endpoint.environment=prod
endpoint.content-type=application/octet-stream
endpoint.connect-timeout-ms=5000
endpoint.socket-timeout-ms=30000
endpoint.max-retries=3
endpoint.retry-delay-ms=2000
```

### 3.8 Output SSL (HTTPS endpoint)

```properties
endpoint.ssl.enabled=true
endpoint.ssl.truststore-path=/certs/output-truststore.p12
endpoint.ssl.truststore-password=changeit
endpoint.ssl.truststore-type=PKCS12
endpoint.ssl.keystore-path=/certs/output-keystore.p12   # only for mTLS
endpoint.ssl.keystore-password=changeit
endpoint.ssl.keystore-type=PKCS12
```

---

## 4. Local Development

Both applications use a `dev` Spring profile that removes SSL requirements so both can run on the same machine without any certificates.

### Step 1 — Start mock-endpoint (HTTP mode)

```bash
cd mock-endpoint
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Listening on http://localhost:8080
```

### Step 2 — Create a test input folder

```bash
# Linux / Mac
mkdir -p /tmp/forwarder-dev-input

# Windows (PowerShell)
New-Item -ItemType Directory -Force "$env:TEMP\forwarder-dev-input"
```

### Step 3 — Start generic-forwarder

```bash
cd generic-forwarder
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Polls /tmp/forwarder-dev-input (or %TEMP%\forwarder-dev-input on Windows)
# Posts to http://localhost:8080/ingest
```

### Step 4 — Drop a test file

```bash
echo '{"test":"hello"}' > /tmp/forwarder-dev-input/test.json
```

Check the mock-endpoint console for the ingest log line.

### Testing HTTPS with dev trust-all

If you want to exercise the SSL code path locally, in `application-dev.properties` switch to Mode B:

```properties
# Comment out Mode A
#endpoint.url=http://localhost:8080/ingest
#endpoint.ssl.enabled=false

# Uncomment Mode B
endpoint.url=https://localhost:8443/ingest
endpoint.ssl.enabled=true
endpoint.ssl.dev-trust-all=true      # accepts any self-signed cert
```

Generate test stores first:

```bash
cd generic-forwarder/ssl-test
./setup-endpoint-ssl.sh   # creates endpoint-ssl/ directory
```

Start mock-endpoint on HTTPS using the generated stores:

```bash
SERVER_SSL_KEY_STORE=file:generic-forwarder/ssl-test/endpoint-ssl/endpoint-server.p12 \
SERVER_SSL_KEY_STORE_PASSWORD=changeit \
mvn spring-boot:run -f mock-endpoint/pom.xml
```

> **`endpoint.ssl.dev-trust-all=true` must never be set in production.**
> It disables all certificate and hostname validation.

---

## 5. Docker Deployment

### Single instance (file source example)

```bash
docker run -d \
  --name forwarder-file \
  -e SOURCE_TYPE=file \
  -e SOURCE_FILE_DIRECTORY=/data/incoming \
  -e ENDPOINT_URL=https://api.example.com/ingest \
  -e ENDPOINT_FEED=my-feed \
  -e ENDPOINT_ENVIRONMENT=prod \
  -e ENDPOINT_SSL_ENABLED=true \
  -v /host/data:/data/incoming \
  -v /host/certs:/certs:ro \
  canet/generic-forwarder:latest
```

### Docker Compose profiles

The `docker-compose.yml` uses Compose profiles — only the selected source starts:

```bash
# Copy and customise the env file
cp generic-forwarder/.env.example generic-forwarder/.env

# Start Kafka forwarder + Kafka broker
cd generic-forwarder
docker compose --profile kafka up -d

# Start RabbitMQ forwarder + RabbitMQ broker
docker compose --profile rabbitmq up -d

# Start file forwarder
docker compose --profile file up -d

# Start SMB forwarder (no infrastructure service bundled)
docker compose --profile smb up -d
```

### Mock endpoint

```bash
docker run -d \
  --name mock-endpoint \
  -p 8443:8443 \
  -e SERVER_SSL_KEY_STORE=file:/certs/endpoint-server.p12 \
  -e SERVER_SSL_KEY_STORE_PASSWORD=changeit \
  -e SERVER_SSL_TRUST_STORE=file:/certs/endpoint-truststore.p12 \
  -e SERVER_SSL_TRUST_STORE_PASSWORD=changeit \
  -e SERVER_SSL_CLIENT_AUTH=NONE \
  -v /host/certs:/certs:ro \
  canet/mock-endpoint:latest
```

---

## 6. SSL / TLS Setup

### Generate test stores (run once)

```bash
cd generic-forwarder/ssl-test

# Kafka stores
BROKER_HOST=broker1.corp.local BROKER_IP=10.0.0.10 MTLS=true \
  ./setup-kafka-ssl.sh
# Output: kafka-ssl/

# Endpoint stores
ENDPOINT_HOST=api.example.com ENDPOINT_IP=10.0.0.20 MTLS=true \
  ./setup-endpoint-ssl.sh
# Output: endpoint-ssl/
```

Windows equivalents: `setup-kafka-ssl.bat` and `setup-endpoint-ssl.bat`

### Deploy stores

| File | Copy to |
|---|---|
| `kafka-ssl/broker-1.p12` | Linux Kafka broker `/var/private/ssl/` |
| `kafka-ssl/broker-truststore.p12` | Linux Kafka broker `/var/private/ssl/` |
| `kafka-ssl/client-truststore.p12` | generic-forwarder container `/certs/` |
| `kafka-ssl/client-keystore.p12` | generic-forwarder container `/certs/` (mTLS only) |
| `endpoint-ssl/endpoint-server.p12` | mock-endpoint container `/certs/` |
| `endpoint-ssl/endpoint-truststore.p12` | mock-endpoint container `/certs/` |
| `endpoint-ssl/output-truststore.p12` | generic-forwarder container `/certs/` |
| `endpoint-ssl/output-keystore.p12` | generic-forwarder container `/certs/` (mTLS only) |

### Validate TLS

```bash
# Kafka broker
openssl s_client -connect broker1.corp.local:9093 \
  -CAfile kafka-ssl/kafka-ca.crt -tls1_2

# HTTP endpoint
openssl s_client -connect api.example.com:8443 \
  -CAfile endpoint-ssl/endpoint-ca.crt

# curl smoke test
curl -v --cacert endpoint-ssl/endpoint-ca.crt \
  -X POST https://api.example.com/ingest \
  -H "X-Feed: my-feed" -H "X-Environment: prod" \
  -H "Content-Type: text/plain" -d "test payload"
```

---

## 7. Running Multiple Source Instances

Each instance reads from one source and posts to the same endpoint. Run as separate containers, each with a different `SOURCE_TYPE`:

```yaml
# docker-compose.override.yml example
services:
  forwarder-kafka:
    environment:
      SOURCE_TYPE: kafka
      SOURCE_KAFKA_TOPIC: feed-a

  forwarder-rabbitmq:
    environment:
      SOURCE_TYPE: rabbitmq
      SOURCE_RABBITMQ_QUEUE: feed-b

  forwarder-file:
    environment:
      SOURCE_TYPE: file
      SOURCE_FILE_DIRECTORY: /data/feed-c
```

For **Kafka horizontal scaling** (higher throughput), run multiple containers with the same `SOURCE_KAFKA_GROUP_ID` and the topic must have at least as many partitions as containers.

---

## 8. Health Checks

The generic-forwarder does not currently expose an actuator endpoint. Add the following to `pom.xml` if needed:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

The **mock-endpoint** already exposes actuator:

```
GET http://localhost:8080/actuator/health   → {"status":"UP"}
GET http://localhost:8080/ping              → {"status":"UP","timestamp":"..."}
```

Docker health check example:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 5s
  retries: 3
```

---

## 9. Troubleshooting

### 9.1 SSL / TLS Errors

#### `SunCertPathBuilderException: unable to find valid certification path`

**Cause:** The forwarder's JVM does not trust the endpoint's certificate.

**Fix (production):** Ensure `endpoint.ssl.enabled=true` and `endpoint.ssl.truststore-path` points to a truststore containing the endpoint CA certificate.

**Fix (dev/test on same host):** Use the `dev` Spring profile (plain HTTP) or set `endpoint.ssl.dev-trust-all=true`.

```bash
# Dev — plain HTTP, no certs needed
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

#### `SSLHandshakeException: PKIX path building failed`

**Cause:** Same as above, or the truststore file path is wrong / password is incorrect.

**Checks:**
```bash
# Verify truststore contents
keytool -list -storetype PKCS12 -keystore /certs/output-truststore.p12 \
  -storepass changeit

# Confirm the CA alias is present
keytool -list -v -storetype PKCS12 -keystore /certs/output-truststore.p12 \
  -storepass changeit | grep "Alias name"
```

---

#### `SSLHandshakeException: Certificate for <hostname> doesn't match`

**Cause:** The server certificate Subject Alternative Names (SANs) do not include the hostname being connected to.

**Fix:** Re-generate the server certificate with the correct SAN:

```bash
ENDPOINT_HOST=api.example.com ENDPOINT_IP=10.0.0.20 ./setup-endpoint-ssl.sh
```

The script adds `DNS:<host>`, `DNS:localhost`, `IP:<ip>`, and `IP:127.0.0.1` automatically.

---

#### `FileNotFoundException: /certs/output-truststore.p12`

**Cause:** The truststore file is not mounted into the container, or the path property is wrong.

**Fix:** Check the Docker volume mount and the `endpoint.ssl.truststore-path` value. Prefix with `file:` when using an absolute path:

```properties
endpoint.ssl.truststore-path=file:/certs/output-truststore.p12
```

---

### 9.2 Kafka Errors

#### `TimeoutException: Topic forwarder-input not present in metadata after 60000ms`

**Cause:** The topic does not exist, or `bootstrap-servers` is unreachable.

**Fix:**
```bash
# Test connectivity
kafka-topics.sh --bootstrap-server broker1:9092 --list

# Create topic if missing
kafka-topics.sh --bootstrap-server broker1:9092 \
  --create --topic forwarder-input --partitions 3 --replication-factor 1
```

---

#### `SaslAuthenticationException` / `Authentication failed`

**Cause:** SASL credentials are wrong or `sasl-jaas-config` is malformed.

**Fix:** Check the JAAS config syntax — it must end with a semicolon:

```properties
source.kafka.sasl-jaas-config=org.apache.kafka.common.security.plain.PlainLoginModule \
  required username="user" password="pass";
```

---

#### Consumer lag growing / messages not being forwarded

**Cause:** Endpoint is slow or rejecting messages; retry back-off is accumulating.

**Fix:**
- Check forwarder logs for HTTP error codes from the endpoint
- Increase `endpoint.socket-timeout-ms` if the endpoint is slow
- Increase `source.kafka.max-poll-interval-ms` to match endpoint latency × `max-poll-records`

---

### 9.3 RabbitMQ Errors

#### `com.rabbitmq.client.ShutdownSignalException: connection error`

**Cause:** RabbitMQ is unreachable or the vhost/credentials are wrong.

**Fix:**
```bash
# Test with rabbitmq management API
curl -u guest:guest http://rabbitmq-host:15672/api/vhosts

# Or use CLI
rabbitmqctl list_connections
```

---

#### Messages acknowledged but not forwarded

**Cause:** `AcknowledgeMode.AUTO` acknowledges on delivery, not on successful forward. If the Camel route throws, the message is lost.

**Mitigation:** Set `endpoint.max-retries` to a value greater than zero. For guaranteed delivery consider switching to `AcknowledgeMode.MANUAL` (requires code change).

---

### 9.4 SMB Errors

#### `jcifs.smb.SmbAuthException: Logon failure`

**Cause:** Wrong username, password, or domain.

**Fix:** Verify credentials against the share manually:

```bash
# Linux: smbclient
smbclient //smb-server/DataShare -U DOMAIN\\user%password -c "ls /incoming"
```

---

#### `jcifs.smb.SmbException: The network name cannot be found`

**Cause:** `source.smb.host` or `source.smb.share` is incorrect.

**Fix:** Check share name (case-sensitive on some servers):

```bash
smbclient -L //smb-server -U DOMAIN\\user%password
```

---

#### Files stuck in local staging directory

**Cause:** The SMB synchroniser downloaded files locally but the Camel route failed before acknowledging them. The `AcceptOnceFileListFilter` holds them in memory and won't reprocess on restart.

**Fix:**
```bash
# Clear the local staging directory to force re-sync
rm -rf /tmp/smb-staging/*

# Then restart the forwarder
```

---

### 9.5 File Source Errors

#### No files being picked up

**Cause:** Pattern mismatch or the directory does not exist.

**Fix:**
```bash
# Test the regex against your filenames
echo "myfile.log" | grep -P "(?i).*\.(log|xml|json|txt|csv|ndjson)$"

# Confirm directory exists and is readable
ls -la /data/incoming/
```

---

#### Files processed multiple times after restart

**Cause:** `AcceptOnceFileListFilter` is in-memory only — it resets on restart.

**Fix:** Set `source.file.done-directory=.done` so processed files are moved out of the polling directory. They will not be re-processed on next start.

---

### 9.6 General Startup Errors

#### `BeanCreationException: Could not resolve placeholder 'endpoint.url'`

**Cause:** Required property is missing from `application.properties` or environment.

**Fix:** Ensure all required properties are set. The minimum set for the file source:

```properties
source.type=file
source.file.directory=/data/incoming
endpoint.url=https://api.example.com/ingest
endpoint.feed=my-feed
endpoint.environment=dev
```

---

#### Application starts but stops immediately

**Cause:** `camel.springboot.main-run-controller=true` is missing. Camel exits when no long-running component is registered.

**Fix:** Confirm this line is in `application.properties`:

```properties
camel.springboot.main-run-controller=true
```

---

#### `QueueChannel` backing up (memory warning in logs)

**Cause:** The endpoint is slower than the source delivery rate.

**Fix:**
- Reduce `source.kafka.max-poll-records` or `source.file.max-files-per-poll`
- Increase `endpoint.socket-timeout-ms`
- Scale the endpoint horizontally

---

### 9.7 Useful Log Levels

Add to `application.properties` to increase detail for a specific area:

```properties
# Show every message forwarded
logging.level.com.canet.forwarder=DEBUG

# Show Camel route tracing
logging.level.org.apache.camel=DEBUG

# Show Spring Integration channel activity
logging.level.org.springframework.integration=DEBUG

# Show raw Kafka consumer activity
logging.level.org.apache.kafka.clients.consumer=DEBUG

# Show RabbitMQ AMQP frames
logging.level.com.rabbitmq=DEBUG
```
