# Broker and Marshaller Containerisation Patterns

## 1. Component Types in BF Low-Side

### 1.1 Brokers

A broker in the BF system acts as an intermediary that:
- Receives messages/data from one system (upstream — may be high-side via gateway)
- Applies business rules, routing, filtering, or enrichment
- Forwards to one or more downstream systems

```
High-side (via gateway)
        │
        ▼
  ┌──────────────────────────────────────────────┐
  │              BROKER (low-side)               │
  │                                              │
  │  Inbound listener  →  Transform  →  Outbound │
  │  (HTTP/JMS/TCP)       (rules)      (HTTP/JMS)│
  └──────────────────────────────────────────────┘
        │
        ▼
  Other low-side or downstream consumers
```

**Characteristics to preserve in containerisation:**
- Listener port(s) — must match what gateway/upstream expects
- Message format transformations — must be identical
- Connection pool settings — timeouts, retries
- Logging format — downstream log aggregation may parse specific formats

### 1.2 Marshallers

A marshaller converts data between formats or protocols at the boundary:

```
  [High-side format]  →  MARSHALLER  →  [Low-side format]
   (binary/XML/EDI)                      (JSON/REST/DB)
```

**Critical:** Marshalling logic is the most likely place where a subtle
version change (e.g. XML parser, character encoding, numeric precision)
can corrupt data without immediately obvious errors.

---

## 2. Java Broker Container Pattern

For brokers built in Java, running under Apache Tomcat or standalone JAR:

### 2.1 Tomcat-hosted Java broker

```
dockerfiles/java/Dockerfile.java8-tomcat-broker
```

Key considerations:
- Deploy the WAR into a pinned Tomcat version
- Copy the original `server.xml`, `context.xml`, `web.xml` verbatim
- Mount `logging.properties` from a ConfigMap / volume

### 2.2 Standalone JAR broker

```
dockerfiles/java/Dockerfile.java8-broker
```

If the broker ran as a Windows service via YAJSW/Tanuki, the JVM
arguments from the wrapper config must be translated to Docker `ENTRYPOINT` args:

```
# Original YAJSW wrapper.conf:
wrapper.java.additional.1=-Dbroker.config=/etc/bf/broker.properties
wrapper.java.additional.2=-Xms256m
wrapper.java.additional.3=-Xmx512m

# Equivalent Docker ENTRYPOINT:
ENTRYPOINT ["java",
  "-Dbroker.config=/etc/bf/broker.properties",
  "-Xms256m",
  "-Xmx512m",
  "-jar", "broker.jar"]
```

---

## 3. C# Broker Container Pattern

For brokers built in C#/.NET Framework hosted under IIS or as Windows services:

### 3.1 Windows container (IIS-hosted)

```
dockerfiles/dotnet/Dockerfile.netfx-broker-iis
```

### 3.2 Windows container (Windows Service → foreground process)

If the broker ran as a Windows Service, it must be adapted to run
in the foreground inside the container:

```csharp
// Original: OnStart() / OnStop() pattern via ServiceBase
// Container adaptation: run as a console app with the same logic

static void Main(string[] args)
{
    if (Environment.UserInteractive)
    {
        // Running in a container or console — foreground mode
        var svc = new BrokerService();
        svc.RunAsConsole(args);
        Console.CancelKeyPress += (s, e) => { svc.Stop(); };
        Thread.Sleep(Timeout.Infinite);
    }
    else
    {
        // Legacy: running as a Windows Service
        ServiceBase.Run(new BrokerService());
    }
}
```

---

## 4. C++ Component Pattern

For any C++ components (if present in low-side):

**Windows container approach:**

```dockerfile
FROM mcr.microsoft.com/windows/servercore:ltsc2022

# Install matching Visual C++ Redistributable for the compiler version used
# VS2008 → vcredist2008, VS2010 → vcredist2010, etc.
# CONFIRM which VS version was used to compile the component

ARG VCREDIST_URL=https://download.microsoft.com/download/.../vcredist_x64.exe
RUN powershell -Command \
    Invoke-WebRequest -Uri $env:VCREDIST_URL -OutFile C:/vcredist.exe; \
    Start-Process C:/vcredist.exe -Wait -ArgumentList '/quiet'; \
    Remove-Item C:/vcredist.exe

WORKDIR C:/app
COPY ./bin .

# Run as a specific user (not LocalSystem)
RUN net user appuser /add && net localgroup Users appuser /add
USER appuser

ENTRYPOINT ["C:/app/component.exe"]
```

---

## 5. Shared Configuration Pattern

Many BF brokers share structural similarities. Externalise config:

```
/etc/bf/
├── broker.properties        ← mounted from Kubernetes ConfigMap
├── logging.properties       ← mounted from Kubernetes ConfigMap
└── ssl/
    ├── keystore.jks         ← mounted from Kubernetes Secret
    └── truststore.jks       ← mounted from Kubernetes Secret
```

```yaml
# Kubernetes ConfigMap for broker config
apiVersion: v1
kind: ConfigMap
metadata:
  name: bf-broker-001-config
  namespace: low-side
data:
  broker.properties: |
    broker.upstream.host=gateway.internal
    broker.upstream.port=8443
    broker.upstream.protocol=HTTPS
    broker.downstream.host=consumer.internal
    broker.downstream.port=8080
    broker.connection.timeout=30000
    broker.retry.count=3
    broker.retry.interval=5000
---
# Kubernetes Secret for keystore
apiVersion: v1
kind: Secret
metadata:
  name: bf-broker-001-ssl
  namespace: low-side
type: Opaque
data:
  keystore.jks: BASE64_ENCODED_JKS
  keystore.password: BASE64_ENCODED_PASSWORD
  truststore.jks: BASE64_ENCODED_TRUSTSTORE
```

---

## 6. Health Check Pattern for Brokers

Brokers are long-running listener processes. Health checks must verify
that the listener is active, not just that the JVM/CLR is running:

```dockerfile
# Java broker health check — test the listener port
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD nc -z localhost ${BROKER_LISTEN_PORT:-8080} || exit 1

# Alternative — check a dedicated health endpoint if the broker exposes one
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -qO- http://localhost:${HEALTH_PORT:-9090}/health || exit 1
```

```yaml
# Kubernetes liveness + readiness probes for a broker
livenessProbe:
  tcpSocket:
    port: 8080
  initialDelaySeconds: 120   # Brokers can take time to connect to upstream
  periodSeconds: 30
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /health
    port: 9090
  initialDelaySeconds: 60
  periodSeconds: 15
```

---

## 7. Startup Order and Dependencies

Brokers depend on the gateway/upstream being available.
Containers start faster than the upstream may be ready.
Use an init container or retry loop:

```dockerfile
# Entrypoint wrapper that waits for upstream before starting broker
COPY wait-for-upstream.sh /usr/local/bin/
ENTRYPOINT ["/usr/local/bin/wait-for-upstream.sh"]
CMD ["java", "-jar", "broker.jar"]
```

```bash
#!/bin/bash
# wait-for-upstream.sh
UPSTREAM_HOST="${BROKER_UPSTREAM_HOST:?}"
UPSTREAM_PORT="${BROKER_UPSTREAM_PORT:?}"
MAX_WAIT=300
INTERVAL=5
elapsed=0

echo "Waiting for upstream $UPSTREAM_HOST:$UPSTREAM_PORT..."
until nc -z "$UPSTREAM_HOST" "$UPSTREAM_PORT" 2>/dev/null; do
    if [[ $elapsed -ge $MAX_WAIT ]]; then
        echo "ERROR: Upstream not available after ${MAX_WAIT}s — exiting"
        exit 1
    fi
    echo "  Upstream not yet available (${elapsed}s elapsed). Retrying in ${INTERVAL}s..."
    sleep $INTERVAL
    elapsed=$((elapsed + INTERVAL))
done
echo "Upstream available — starting broker"
exec "$@"
```

---

## 8. Broker Similarity and Code Reuse

Since brokers are expected to share significant code:

1. **Common base image:** Create `bf-broker-base:1.0` from which all brokers inherit
2. **Shared configuration schema:** All brokers use the same `broker.properties` key names
3. **Single health-check sidecar pattern:** One standard health-check approach
4. **Parameterised Dockerfile:** One template, different `ARG` values per broker

```dockerfile
# Dockerfile.bf-broker-base — shared base for all Java brokers
FROM eclipse-temurin:8u392-b08-jre-focal AS bf-broker-base

# Common OS packages needed by all brokers
RUN apt-get update -qq \
 && apt-get install -y --no-install-recommends \
      netcat-openbsd \
      curl \
 && rm -rf /var/lib/apt/lists/*

# Common non-root user
RUN groupadd -r -g 1001 bfgroup \
 && useradd  -r -u 1001 -g bfgroup -d /app -s /sbin/nologin bfuser

# Standard config directory layout
RUN mkdir -p /etc/bf/ssl && chown -R bfuser:bfgroup /etc/bf

# Standard wait-for-upstream script
COPY scripts/wait-for-upstream.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/wait-for-upstream.sh

WORKDIR /app
USER bfuser
```

```dockerfile
# Dockerfile.bf-broker-001 — specific broker
FROM bf-broker-base:1.0

COPY target/broker-001.jar app.jar
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/wait-for-upstream.sh"]
CMD ["java", \
     "-Xms256m", "-Xmx512m", \
     "-Dbroker.config=/etc/bf/broker.properties", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", "app.jar"]
```
