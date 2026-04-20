# Generic Forwarder — High-Level Design

## 1. Purpose

The **Generic Forwarder** replaces a NiFi workflow with a lightweight, fully configurable Spring Boot application. It reads messages from one configurable source (Kafka, RabbitMQ, SMB, or a local file folder) and forwards each message as an HTTP POST to a target endpoint, enriching each request with feed and environment identity headers.

One running instance handles exactly one source type. Multiple instances are deployed in parallel to fan-in several sources to the same endpoint.

---

## 2. System Context

```
┌─────────────────────────────────────────────────────────────────┐
│                     Data Producers                              │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  Kafka   │  │ RabbitMQ │  │   SMB    │  │  File Share  │  │
│  │ (SSL/    │  │ (AMQPS/  │  │ (SMB2/3) │  │  (log/xml/   │  │
│  │  SASL)   │  │  plain)  │  │          │  │   json/csv)  │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬───────┘  │
└───────┼─────────────┼─────────────┼────────────────┼──────────┘
        │  one per instance          │                │
        ▼             ▼             ▼                ▼
┌───────────────────────────────────────────────────────────────┐
│              Generic Forwarder  (Spring Boot 3 / Java 17)     │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │   Spring Integration Inbound Adapter (active source)   │  │
│  │   KafkaMessageDrivenChannelAdapter                     │  │
│  │   AmqpInboundChannelAdapter                            │  │
│  │   SmbInboundFileSynchronizingMessageSource             │  │
│  │   FileReadingMessageSource                             │  │
│  └──────────────────────────┬─────────────────────────────┘  │
│                             │ QueueChannel                    │
│  ┌──────────────────────────▼─────────────────────────────┐  │
│  │   CamelBridge  (@ServiceActivator)                      │  │
│  │   Unwraps SI message → calls ProducerTemplate           │  │
│  └──────────────────────────┬─────────────────────────────┘  │
│                             │ direct:forwarderInput           │
│  ┌──────────────────────────▼─────────────────────────────┐  │
│  │   Apache Camel Route  (ForwarderRoute)                  │  │
│  │   • Set X-Feed, X-Environment, Content-Type headers     │  │
│  │   • Retry with exponential back-off                     │  │
│  │   • POST via camel-http (HTTPS + optional mTLS)         │  │
│  └──────────────────────────┬─────────────────────────────┘  │
└──────────────────────────────┼────────────────────────────────┘
                               │ HTTPS POST
                               ▼
                  ┌────────────────────────┐
                  │   Target HTTP Endpoint  │
                  │   X-Feed: <feed>        │
                  │   X-Environment: <env>  │
                  └────────────────────────┘
```

---

## 3. Component Breakdown

### 3.1 Source Layer — Spring Integration

Each source type is implemented as a Spring Integration inbound adapter. Only the adapter matching `source.type` is activated at startup via `@ConditionalOnProperty`.

| Source Type | Spring Integration Component | Activation |
|---|---|---|
| `kafka` | `KafkaMessageDrivenChannelAdapter` | `source.type=kafka` |
| `rabbitmq` | `AmqpInboundChannelAdapter` | `source.type=rabbitmq` |
| `smb` | `SmbInboundFileSynchronizingMessageSource` | `source.type=smb` |
| `file` | `FileReadingMessageSource` | `source.type=file` |

All adapters write to a shared `QueueChannel` named `forwarderInputChannel`. The queue buffers up to 1,000 messages to absorb burst traffic without back-pressuring the source.

### 3.2 Bridge Layer — CamelBridge

`CamelBridge` is a `@ServiceActivator` listening on `forwarderInputChannel`. It:

- Unwraps the Spring Integration `Message<?>` envelope
- Converts `byte[]`, `String`, and `java.io.File` payloads to a normalised form
- Calls `ProducerTemplate.sendBodyAndHeaders("direct:forwarderInput", ...)` to hand the message to Camel

This is the only integration point between the Spring Integration and Apache Camel runtimes. No extra artifact is required.

### 3.3 Routing Layer — Apache Camel

`ForwarderRoute` defines a `direct:forwarderInput` route that:

1. Sets required HTTP headers: `X-Feed`, `X-Environment`, `Content-Type`
2. Removes internal Spring Integration headers before forwarding
3. POSTs to the configured `endpoint.url` via the Camel HTTP component
4. Applies exponential back-off retry (configurable `maxRetries` and `retryDelayMs`)

### 3.4 SSL / TLS

Two independent SSL configurations are supported:

| Direction | Configuration prefix | Applied by |
|---|---|---|
| **Input** (Kafka SSL / RabbitMQ AMQPS) | `source.ssl.*` | Native Kafka/RabbitMQ client properties |
| **Output** (HTTPS endpoint) | `endpoint.ssl.*` | Camel `SSLContextParameters` on the HTTPS component |

Both support keystore + truststore (PKCS12 or JKS). The output SSL also supports a `dev-trust-all` flag that disables certificate and hostname validation for local testing.

---

## 4. Configuration Model

```
source.type = kafka | rabbitmq | smb | file     ← selects active adapter

source.kafka.*        ← Kafka connection + security
source.rabbitmq.*     ← RabbitMQ connection + concurrency
source.smb.*          ← SMB host + credentials + protocol version
source.file.*         ← local directory + file pattern + polling
source.ssl.*          ← input keystore / truststore (Kafka SSL, AMQPS)

endpoint.url          ← target HTTP(S) URL
endpoint.feed         ← X-Feed header value
endpoint.environment  ← X-Environment header value
endpoint.ssl.*        ← output keystore / truststore (mTLS to endpoint)
```

All properties can be overridden by environment variables using Spring Boot's relaxed binding:

```
SOURCE_TYPE=kafka
SOURCE_KAFKA_BOOTSTRAP_SERVERS=broker:9092
ENDPOINT_URL=https://api.example.com/ingest
ENDPOINT_FEED=my-feed
```

---

## 5. Deployment Model

```
┌──────────────────────────────────────────────────────────┐
│                  Container Host / K8s Namespace          │
│                                                          │
│  ┌──────────────────┐   ┌──────────────────┐            │
│  │ forwarder-kafka  │   │ forwarder-file   │            │
│  │ SOURCE_TYPE=kafka│   │ SOURCE_TYPE=file │  ...        │
│  └──────────────────┘   └──────────────────┘            │
│                                                          │
│  All instances share the same Docker image.              │
│  Configuration is injected via environment variables.    │
│  Secrets (passwords) via mounted secrets or Vault.       │
└──────────────────────────────────────────────────────────┘
```

One container image — `canet/generic-forwarder` — is built once and configured at runtime. Horizontal scaling for a single source type is achieved by running additional containers in the same consumer group (Kafka) or competing consumers (RabbitMQ).

---

## 6. Key Design Decisions

| Decision | Rationale |
|---|---|
| One source type per instance | Simplifies configuration, enables independent scaling, and isolates failures |
| Spring Integration for inbound adapters | Proven, production-grade connectors for Kafka, AMQP, SMB, and file with built-in retry and ack semantics |
| Apache Camel for routing | Concise DSL for header manipulation, retry, and HTTP posting; decouples routing logic from protocol concerns |
| Bridge via `@ServiceActivator` + `ProducerTemplate` | Avoids the non-existent `camel-spring-integration` artifact; clean hand-off with no extra dependency |
| `QueueChannel(1000)` | Decouples the inbound adapter thread from the Camel processing thread; prevents head-of-line blocking |
| PKCS12 keystores | Java 9+ default; supported natively by Kafka, RabbitMQ, and Camel without extra conversion |
| `@ConditionalOnProperty` | Ensures only the active source adapter's beans are created; avoids classpath-scanning conflicts |
| Separate input/output SSL chains | Source and endpoint certificates can be rotated independently |

---

## 7. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Runtime | Java | 17 LTS |
| Framework | Spring Boot | 3.2.5 |
| Source adapters | Spring Integration | 6.x (managed by Boot) |
| Routing / HTTP | Apache Camel | 4.6.0 |
| Kafka client | Apache Kafka | 3.x (managed by Boot) |
| AMQP client | RabbitMQ Java Client | via spring-rabbit |
| SMB client | JCIFS-NG | via spring-integration-smb |
| Containerisation | Docker / Docker Compose | — |

---

## 8. Mock Endpoint (Testing)

A companion Spring Boot application (`mock-endpoint`) simulates the target HTTP endpoint for integration testing. It:

- Accepts `POST /ingest` with any payload
- Logs `X-Feed`, `X-Environment`, payload size, and all request headers
- Returns a JSON acknowledgement `{"status":"accepted", ...}`
- Supports HTTPS with configurable keystore/truststore and optional mTLS
- Provides `GET /ping` and `/actuator/health` for liveness checks

In the `dev` Spring profile it runs on plain HTTP (`http://localhost:8080`) to remove the need for certificates during local development.
