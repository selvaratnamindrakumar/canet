# SSL / TLS Test Setup

Two independent certificate chains — one for Kafka, one for the HTTP endpoint.
All stores use **PKCS12** format (Java 9+ default).

---

## 1 — Windows Desktop → Linux Kafka (one-way or mTLS)

### Quick start

**Linux / Mac**
```bash
# One-way TLS
BROKER_HOST=broker1.yourdomain.local BROKER_IP=10.0.0.10 ./setup-kafka-ssl.sh

# mTLS (also creates client keystore)
MTLS=true BROKER_HOST=broker1.yourdomain.local BROKER_IP=10.0.0.10 ./setup-kafka-ssl.sh
```

**Windows**
```bat
set BROKER_HOST=broker1.yourdomain.local
set BROKER_IP=10.0.0.10
set MTLS=true
setup-kafka-ssl.bat
```

### Files produced (`kafka-ssl/`)

| File | Deploy to | Purpose |
|---|---|---|
| `kafka-ca.p12` | CA only (keep secure) | Signs all Kafka certs |
| `kafka-ca.crt` | Both sides | CA public cert |
| `broker-1.p12` | Linux Kafka broker | Broker keystore |
| `broker-truststore.p12` | Linux Kafka broker | Trusts client certs (mTLS) |
| `client-truststore.p12` | Windows desktop `/certs` | Trusts broker cert |
| `client-keystore.p12` | Windows desktop `/certs` | Client identity (mTLS only) |

### Linux Kafka broker — `server.properties`

```properties
listeners=SSL://0.0.0.0:9093
ssl.keystore.location=/var/private/ssl/broker-1.p12
ssl.keystore.password=changeit
ssl.key.password=changeit
ssl.truststore.location=/var/private/ssl/broker-truststore.p12
ssl.truststore.password=changeit
ssl.client.auth=required          # set to "none" for one-way TLS
security.inter.broker.protocol=SSL
```

### generic-forwarder — `application.properties` (Kafka source)

```properties
source.type=kafka
source.kafka.bootstrap-servers=broker1.yourdomain.local:9093
source.kafka.security-protocol=SSL          # or SASL_SSL
source.ssl.enabled=true
source.ssl.truststore-path=C:\\ssl\\client-truststore.p12
source.ssl.truststore-password=changeit
# mTLS only:
source.ssl.keystore-path=C:\\ssl\\client-keystore.p12
source.ssl.keystore-password=changeit
```

### Validate

```bash
# From any host that can reach the broker
openssl s_client -connect broker1.yourdomain.local:9093 \
  -CAfile kafka-ssl/kafka-ca.crt -tls1_2

# Kafka producer smoke test
kafka-console-producer.sh \
  --bootstrap-server broker1.yourdomain.local:9093 \
  --topic test \
  --producer-property security.protocol=SSL \
  --producer-property ssl.truststore.location=kafka-ssl/client-truststore.p12 \
  --producer-property ssl.truststore.password=changeit
```

---

## 2 — generic-forwarder → mock-endpoint (one-way or mTLS)

### Quick start

**Linux / Mac**
```bash
# One-way TLS
ENDPOINT_HOST=localhost ./setup-endpoint-ssl.sh

# mTLS
MTLS=true ENDPOINT_HOST=api.example.com ENDPOINT_IP=10.0.0.20 ./setup-endpoint-ssl.sh
```

**Windows**
```bat
set ENDPOINT_HOST=localhost
set MTLS=true
setup-endpoint-ssl.bat
```

### Files produced (`endpoint-ssl/`)

| File | Deploy to | Purpose |
|---|---|---|
| `endpoint-ca.p12` | CA only | Signs endpoint certs |
| `endpoint-ca.crt` | Both services | CA public cert |
| `endpoint-server.p12` | mock-endpoint `/certs` | Server TLS identity |
| `endpoint-truststore.p12` | mock-endpoint `/certs` | Trusts forwarder client cert (mTLS) |
| `output-truststore.p12` | generic-forwarder `/certs` | Trusts endpoint server cert |
| `output-keystore.p12` | generic-forwarder `/certs` | Forwarder client identity (mTLS only) |

### mock-endpoint — `application.properties`

```properties
server.ssl.key-store=file:/certs/endpoint-server.p12
server.ssl.key-store-password=changeit
server.ssl.trust-store=file:/certs/endpoint-truststore.p12
server.ssl.trust-store-password=changeit
server.ssl.client-auth=NONE       # NONE | WANT | NEED (NEED = mTLS)
```

### generic-forwarder — `application.properties` (output)

```properties
endpoint.url=https://localhost:8443/ingest
endpoint.ssl.enabled=true
endpoint.ssl.truststore-path=/certs/output-truststore.p12
endpoint.ssl.truststore-password=changeit
# mTLS only:
endpoint.ssl.keystore-path=/certs/output-keystore.p12
endpoint.ssl.keystore-password=changeit
```

### Validate

```bash
# TLS handshake check
openssl s_client -connect localhost:8443 \
  -CAfile endpoint-ssl/endpoint-ca.crt

# HTTP smoke test (curl trusting the test CA)
curl -v --cacert endpoint-ssl/endpoint-ca.crt \
  -X POST https://localhost:8443/ingest \
  -H "Content-Type: text/plain" \
  -H "X-Feed: test-feed" \
  -H "X-Environment: dev" \
  -d "hello world"

# mTLS curl test
curl -v --cacert endpoint-ssl/endpoint-ca.crt \
  --cert endpoint-ssl/output-client-signed.crt \
  --key <(keytool -exportcert ...) \   # extract private key via openssl
  -X POST https://localhost:8443/ingest ...
```

---

## Environment variables reference

All scripts accept overrides via environment variables:

| Variable | Default | Applies to |
|---|---|---|
| `BROKER_HOST` | `broker1.yourdomain.local` | Kafka script |
| `BROKER_IP` | `127.0.0.1` | Kafka script (SAN) |
| `ENDPOINT_HOST` | `localhost` | Endpoint script |
| `ENDPOINT_IP` | `127.0.0.1` | Endpoint script (SAN) |
| `STORE_PASS` | `changeit` | Both |
| `MTLS` | `false` | Both |
| `VALIDITY_CA` | `3650` days | Both |
| `VALIDITY_CERT` | `825` days | Both |
