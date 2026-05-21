# CaNet — NEO Network Equipment Observer: Design & Deployment Guide

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Module Design](#3-module-design)
4. [Data Flow](#4-data-flow)
5. [Configuration Reference](#5-configuration-reference)
6. [Prerequisites](#6-prerequisites)
7. [Local Development — No Docker](#7-local-development--no-docker)
8. [Local Development — Docker Compose](#8-local-development--docker-compose)
9. [Building Deployable Artifacts](#9-building-deployable-artifacts)
10. [Docker — Individual Images](#10-docker--individual-images)
11. [Production Deployment](#11-production-deployment)
12. [Environment Variable Overrides](#12-environment-variable-overrides)
13. [Health & Monitoring](#13-health--monitoring)
14. [Running Tests](#14-running-tests)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. System Overview

CaNet (NEO — Network Equipment Observer) is a two-module Spring Boot system for searching handset/device details by TAC (Type Allocation Code) or IMEI.

| Module | Purpose | Port |
|---|---|---|
| `spring-boot-app` | Main UI + REST API; makes server-side HTTP calls to a handset API | 8080 |
| `mock-server` | Simulates a handset-details API; 20 sample records; used in development and testing | 8081 |

**Key properties:**
- No Node.js, no npm, no `node_modules` — pure JVM stack
- Bootstrap CSS is bundled inside the JAR (no CDN call at runtime — works in air-gapped / secure networks)
- All endpoint behaviour (fields, labels, validation, limits) is driven by YAML with no code changes

---

## 2. Architecture

### 2.1 Component Diagram

```
Browser
  │  HTTP GET /  (Thymeleaf UI)
  │  HTTP GET /api/data/export.csv
  ▼
┌─────────────────────────────────────────────────────────────────┐
│  spring-boot-app  (port 8080)                                   │
│                                                                 │
│  DataController  ──►  HttpsDataServiceImpl                      │
│       │                    │                                    │
│  Thymeleaf                 │  RestTemplate                      │
│  index.html                │  (connect timeout 5s,             │
│  about.html                │   read timeout 10s)               │
│                            │                                    │
│  CaNetProperties           ▼                                    │
│  (YAML config)    HTTP GET /handsetdetails?tac=T1,T2,...         │
└────────────────────────────┼────────────────────────────────────┘
                             │
              ┌──────────────▼──────────────────────┐
              │  mock-server  (port 8081)             │
              │  or real handset API in production    │
              │                                      │
              │  HandsetController                   │
              │  HandsetRepository (20 records)      │
              └──────────────────────────────────────┘
```

### 2.2 Request Flow Summary

```
User types IMEIs/TACs in textarea
        │
        ▼  (form submit → GET /?mode=cell&entries=...)
DataController.doSearch()
        │
        ├─ [cell mode]  extract TAC = IMEI.substring(0,8)
        │               build tacToInput map (TAC → original IMEI)
        │
        ├─ [handset mode]  tacToInput = {TAC → TAC}
        │
        ▼
HttpsDataServiceImpl.fetchByTacs(url, tacs)
        │  GET {url}?tac=T1,T2,...
        ▼
TacSearchResponse { requested, found, results[], notFound[] }
        │
        ▼
annotate each row with inputValue (original IMEI or TAC)
        │
        ▼  (if flatten:true)
JsonFlattener.flatten(row, "-")
  network:{generations,type}  →  network-generations, network-type
  capabilities:{nfc,wc}       →  capabilities-nfc, capabilities-wirelessCharging
        │
        ▼
Thymeleaf renders index.html
  compact columns + expandable detail rows + CSV export
```

### 2.3 Static Asset Strategy

Bootstrap 5.3.3 CSS is served from the JAR via WebJar — no CDN request is made at runtime. This is essential for secure/air-gapped environments where `cdn.jsdelivr.net` is blocked.

```
Browser  →  GET /webjars/bootstrap/css/bootstrap.min.css
Spring Boot serves from: BOOT-INF/lib/bootstrap-5.3.3.jar
                          META-INF/resources/webjars/bootstrap/5.3.3/css/bootstrap.min.css
```

---

## 3. Module Design

### 3.1 spring-boot-app

```
src/main/java/com/canet/app/
├── CaNetApplication.java              @SpringBootApplication + @EnableConfigurationProperties
├── config/
│   └── CaNetProperties.java           @ConfigurationProperties(prefix="canet")
│                                      Binds application.yml canet.endpoints[] list
├── controller/
│   └── DataController.java            GET /  GET /api/data  GET /api/data/export.csv
├── service/
│   ├── HttpsDataService.java          Interface: fetchAll, fetchById, fetchByTacs
│   └── HttpsDataServiceImpl.java      RestTemplate with 5s connect / 10s read timeouts
└── util/
    └── JsonFlattener.java             Recursive nested-map flattener
```

**Key design choices:**

| Choice | Rationale |
|---|---|
| `Map<String, Object>` everywhere | Schema-agnostic — works with any JSON without model changes |
| `LinkedHashMap` | Preserves JSON field insertion order through flattening into rendered columns |
| Server-side HTTP | Avoids CORS, certificate trust and network topology issues in the browser |
| `RestTemplateBuilder` | Compatible with `@RestClientTest` slice — no separate `HttpClientConfig` bean |
| Thymeleaf (not React/Vue) | Zero npm; no `node_modules`; server renders everything |
| YAML-driven config | Fields, labels, validation, limits all change without a code deployment |

### 3.2 mock-server

```
src/main/java/com/canet/mock/
├── MockServerApplication.java
├── controller/
│   └── HandsetController.java      GET /handsetdetails  GET /handsets  GET /rs/v1/handsetbyIMEIList
├── data/
│   └── HandsetRepository.java      In-memory store of 20 HandsetRecord entries
└── model/
    ├── HandsetRecord.java           Java record: tac, marketingName, network, capabilities, …
    ├── NetworkInfo.java             Nested: generations, type
    ├── DeviceCapabilities.java      Nested: nfc, wirelessCharging
    └── TacSearchResponse.java       Envelope: requested, found, results[], notFound[]
```

The nested `NetworkInfo` / `DeviceCapabilities` objects are intentional — they demonstrate the JSON-flattening feature end-to-end.

### 3.3 Mock Server API Reference

| Method | Path | Parameters | Response |
|---|---|---|---|
| GET | `/handsetdetails` | — | `List<HandsetRecord>` (all 20) |
| GET | `/handsetdetails` | `?manufacturer=Samsung` | Filtered list (case-insensitive) |
| GET | `/handsetdetails/{tac}` | — | `HandsetRecord` or 404 |
| GET | `/handsetdetails` | `?tac=T1,T2,...` (≤ 20) | `TacSearchResponse` envelope |
| GET | `/handsets` | same | Alias path |
| GET | `/handsets/{tac}` | — | Alias path |
| GET | `/rs/v1/handsetbyIMEIList` | `?imeis=I1,I2,...` (≤ 20) | `TacSearchResponse`; TAC = first 8 chars of each IMEI |

---

## 4. Data Flow

### 4.1 Handset Mode

User enters one TAC per line (8 digits). Example: `35674108`

```
Input:   35674108
         35282402

tacToInput:  { "35674108" → "35674108",
               "35282402" → "35282402" }

GET /handsetdetails?tac=35674108,35282402

Response envelope:
  results:  [ {tac:35674108, marketingName:"Samsung Galaxy S24 Ultra", …},
              {tac:35282402, marketingName:"Apple iPhone 15 Pro Max",  …} ]
  notFound: []

After annotate:
  row["inputValue"] = "35674108"   ← same as tac in handset mode

After flatten (separator "-"):
  network-generations: "2G/3G/4G/5G"
  network-type: "NSA/SA"
  capabilities-nfc: true
  capabilities-wirelessCharging: true

UI compact columns:
  Searched Value | TAC      | Marketing Name           | Manufacturer | Model    | OS
  35674108       | 35674108 | Samsung Galaxy S24 Ultra | Samsung      | SM-S928B | Android
```

### 4.2 Cell Mode

User enters one IMEI per line (15 digits). Example: `353456789012345`

```
Input:   353456789012345
         354567890123456

TAC extraction:  "353456789012345".substring(0,8) = "35345678"
                 "354567890123456".substring(0,8) = "35456789"

tacToInput:  { "35345678" → "353456789012345",
               "35456789" → "354567890123456" }

GET /handsetdetails?tac=35345678,35456789

Response envelope:
  results:  [ {tac:35345678, marketingName:"Samsung Galaxy S23 Ultra", …},
              {tac:35456789, marketingName:"Apple iPhone 14 Pro",      …} ]
  notFound: []

After annotate:
  row["inputValue"] = "353456789012345"   ← original IMEI

UI compact columns:
  Searched Value   | TAC      | Marketing Name           | Manufacturer | Model
  353456789012345  | 35345678 | Samsung Galaxy S23 Ultra | Samsung      | SM-S918B
  354567890123456  | 35456789 | Apple iPhone 14 Pro      | Apple        | A2651
```

### 4.3 Cell Mode Test IMEIs (mock server)

The following 15-digit IMEIs are valid for cell mode testing against the mock server:

| IMEI              | → TAC    | Device                  |
|-------------------|----------|-------------------------|
| 353456789012345   | 35345678 | Samsung Galaxy S23 Ultra|
| 354567890123456   | 35456789 | Apple iPhone 14 Pro     |
| 352678012345678   | 35267801 | Google Pixel 6a         |
| 866789012345678   | 86678901 | OnePlus 11              |
| 867890123456789   | 86789012 | Xiaomi 13 Pro           |
| 868901234567890   | 86890123 | OPPO Find X6 Pro        |
| 359012345678901   | 35901234 | Nokia G60 5G            |
| 860123456789012   | 86012345 | Realme GT 5 Pro         |
| 861234567890123   | 86123456 | Vivo X90 Pro+           |
| 352345678901234   | 35234567 | Motorola Razr 40 Ultra  |

---

## 5. Configuration Reference

All endpoint behaviour is controlled by `canet.endpoints[]` in `application.yml`. No code changes are required to add an endpoint or adjust field mappings.

### 5.1 Endpoint Configuration Fields

| YAML key | Type | Default | Description |
|---|---|---|---|
| `name` | String | — | Human identifier (logs, diagnostics) |
| `type` | String | `handset` | `"handset"` or `"cell"` — selects this entry when the UI switches mode |
| `url` | String | — | Full URL of the handset API |
| `default-endpoint` | boolean | `false` | Used when no matching `type` is found |
| `compact-fields` | List | `[]` | Columns shown in the collapsed table row |
| `detail-fields` | List | `[]` | Columns shown when row is expanded; `["*"]` = all remaining fields |
| `labels` | Map | `{}` | Column header overrides; keys use **post-flatten** field names |
| `flatten` | boolean | `false` | Recursively flatten nested JSON before display |
| `flatten-separator` | String | `"-"` | Separator between parent and child key when flattening |
| `submit-delay-seconds` | int | `0` | Countdown before the Submit button activates |
| `validation-pattern` | String | `""` | Client-side regex for each entry; empty = no validation |
| `validation-message` | String | `"Invalid input"` | Error shown when pattern fails |
| `max-entries` | int | `20` | Hard cap on entries per search (HANDSET_SEARCH_CONFIG / CELL_SEARCH_CONFIG) |

### 5.2 Active Profiles

| Profile | Endpoints | API URL | Notes |
|---|---|---|---|
| `default` | `todos` (handset), `todos-cell` (cell) | `https://jsonplaceholder.typicode.com/todos` | Demo; no validation; no flatten |
| `dev` | `handset-search`, `cell-search` | `http://localhost:8081/handsetdetails` | Points at local mock server; 3s delay; flatten on |

### 5.3 Adding a New Endpoint (zero code changes)

```yaml
canet:
  endpoints:
    - name: my-api
      type: handset
      url: https://api.example.com/devices
      default-endpoint: true
      compact-fields: [inputValue, id, name, vendor]
      detail-fields: ["*"]
      labels:
        inputValue: "Searched Value"
        id: "Device ID"
        name: "Device Name"
      flatten: true
      flatten-separator: "."
      submit-delay-seconds: 2
      validation-pattern: "^[A-Z]{3}\\d{5}$"
      validation-message: "Must be 3 letters followed by 5 digits"
      max-entries: 10
```

---

## 6. Prerequisites

| Tool | Minimum version | Purpose |
|---|---|---|
| JDK | 17 | Compile and run both modules |
| Maven | 3.8+ | Build tool (`mvnw` wrapper included) |
| Docker | 20.10+ | Container builds and Compose |
| Docker Compose | 2.x | Run both services together |
| Git | Any | Source checkout |

> **Internet access at build time only.** Maven downloads dependencies from Maven Central when building. At runtime the application makes no external network calls — Bootstrap CSS is bundled in the JAR.

---

## 7. Local Development — No Docker

### 7.1 Clone and build

```bash
git clone <repo-url> canet
cd canet
```

### 7.2 Run mock server (Terminal 1)

```bash
cd mock-server
mvn spring-boot:run
# Listening on http://localhost:8081
# Test: curl http://localhost:8081/handsetdetails/35674108
```

### 7.3 Run main app pointing at mock (Terminal 2)

```bash
cd spring-boot-app
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# UI at http://localhost:8080
```

### 7.4 Run main app in demo mode (no mock needed)

```bash
cd spring-boot-app
mvn spring-boot:run
# Points at https://jsonplaceholder.typicode.com/todos
# UI at http://localhost:8080
```

### 7.5 Quick smoke-test URLs

```
http://localhost:8080                                   # Main UI
http://localhost:8080/about                             # Why Spring Boot? page
http://localhost:8080/actuator/health                   # Health check

http://localhost:8081/handsetdetails                    # All 20 records (JSON)
http://localhost:8081/handsetdetails/35674108           # Single record by TAC
http://localhost:8081/handsetdetails?tac=35674108,35282402   # Multi-TAC search
http://localhost:8081/handsetdetails?manufacturer=Samsung    # Filter
```

---

## 8. Local Development — Docker Compose

Docker Compose starts both services on a shared network and injects the correct mock URL automatically.

```bash
# From the repo root
docker compose up --build

# Main app:   http://localhost:8080
# Mock API:   http://localhost:8081/handsetdetails

# Tail logs
docker compose logs -f canet
docker compose logs -f mock

# Stop and remove containers
docker compose down
```

**What Compose does for you:**
- Builds both images from their respective `Dockerfile`s
- Starts `mock` first; waits for its healthcheck to pass before starting `canet`
- Injects `SPRING_PROFILES_ACTIVE=dev` and overrides the mock URL to `http://mock:8081` (Docker service name, not `localhost`)

---

## 9. Building Deployable Artifacts

### 9.1 JAR files

```bash
# Main app
cd spring-boot-app
mvn package                          # runs tests + creates JAR
mvn package -DskipTests              # skip tests (CI pre-checked)
ls target/canet-app-1.0.0-SNAPSHOT.jar

# Mock server
cd mock-server
mvn package -DskipTests
ls target/mock-server-1.0.0-SNAPSHOT.jar
```

The JARs are self-contained (fat JARs): they include Tomcat, all dependencies, Bootstrap CSS, and Thymeleaf templates. No external application server is required.

### 9.2 Running the JAR directly

```bash
# Mock server (start first)
java -jar mock-server/target/mock-server-1.0.0-SNAPSHOT.jar

# Main app — dev profile (points at local mock)
java -jar spring-boot-app/target/canet-app-1.0.0-SNAPSHOT.jar \
     --spring.profiles.active=dev

# Main app — custom API URL (no profile needed)
java -jar spring-boot-app/target/canet-app-1.0.0-SNAPSHOT.jar \
     --canet.endpoints[0].url=https://real-api.example.com/handsetdetails \
     --canet.endpoints[1].url=https://real-api.example.com/handsetdetails
```

---

## 10. Docker — Individual Images

### 10.1 Build images

```bash
# Mock server
docker build -t canet/mock-server:latest ./mock-server

# Main app
docker build -t canet/app:latest ./spring-boot-app
```

### 10.2 Run containers manually

```bash
# 1. Create a shared network
docker network create canet-net

# 2. Start mock server
docker run -d \
  --name canet-mock \
  --network canet-net \
  -p 8081:8081 \
  canet/mock-server:latest

# 3. Start main app — point at mock via Docker hostname
docker run -d \
  --name canet-app \
  --network canet-net \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e CANET_ENDPOINTS_0_URL=http://canet-mock:8081/handsetdetails \
  -e CANET_ENDPOINTS_1_URL=http://canet-mock:8081/handsetdetails \
  canet/app:latest
```

### 10.3 Dockerfile design notes

Both Dockerfiles use **two-stage builds**:

```
Stage 1 (maven:3.9-eclipse-temurin-17-alpine)
  └── mvn dependency:go-offline   ← cached layer; only re-runs if pom.xml changes
  └── mvn package -DskipTests     ← re-runs only when src/ changes

Stage 2 (eclipse-temurin:17-jre-alpine)
  └── Non-root user (appuser)     ← security hardening
  └── COPY target/*.jar app.jar
  └── ENTRYPOINT ["java","-jar","app.jar"]
```

The JRE-only runtime image (~85 MB) is significantly smaller than the JDK build image. Alpine base keeps the final image lean.

---

## 11. Production Deployment

### 11.1 Recommended architecture

```
Internet / Corporate Network
        │
        ▼
  Load Balancer / Reverse Proxy (nginx / AWS ALB / etc.)
        │  HTTPS termination here
        ▼
  canet-app  (port 8080, HTTP internally)
        │
        ▼  (internal network only)
  Real Handset API  (your production endpoint)
```

The mock server is **not deployed to production**. Point `canet.endpoints[*].url` at the real API.

### 11.2 Minimum production application.yml

Create a file outside the JAR (e.g. `/etc/canet/application-prod.yml`) and activate the `prod` profile:

```yaml
spring:
  config:
    activate:
      on-profile: prod
  thymeleaf:
    cache: true     # enable in production

server:
  port: 8080

canet:
  endpoints:
    - name: handset-search
      type: handset
      url: https://real-api.example.com/handsetdetails
      default-endpoint: true
      compact-fields: [inputValue, tac, marketingName, manufacturer, modelName, operatingSystem]
      detail-fields: ["*"]
      labels:
        inputValue: "Searched Value"
        tac: "TAC"
        marketingName: "Marketing Name"
        manufacturer: "Manufacturer"
        modelName: "Model Name"
        operatingSystem: "Operating System"
        osVersion: "OS Version"
        network-generations: "Network Generations"
        network-type: "Network Type"
        displaySizeInches: "Display (inches)"
        releaseYear: "Release Year"
        capabilities-nfc: "NFC Supported"
        capabilities-wirelessCharging: "Wireless Charging"
      flatten: true
      flatten-separator: "-"
      submit-delay-seconds: 3
      validation-pattern: "^\\d{8}$"
      validation-message: "Must be exactly 8 digits"
      max-entries: 20

    - name: cell-search
      type: cell
      url: https://real-api.example.com/handsetdetails
      default-endpoint: false
      compact-fields: [inputValue, tac, marketingName, manufacturer, modelName]
      detail-fields: ["*"]
      labels:
        inputValue: "Searched Value"
        tac: "TAC"
        marketingName: "Marketing Name"
        manufacturer: "Manufacturer"
        modelName: "Model Name"
        operatingSystem: "Operating System"
        osVersion: "OS Version"
        network-generations: "Network Generations"
        network-type: "Network Type"
        displaySizeInches: "Display (inches)"
        releaseYear: "Release Year"
        capabilities-nfc: "NFC Supported"
        capabilities-wirelessCharging: "Wireless Charging"
      flatten: true
      flatten-separator: "-"
      submit-delay-seconds: 3
      validation-pattern: "^\\d{15}$"
      validation-message: "Must be exactly 15 digits"
      max-entries: 20

logging:
  level:
    com.canet: INFO   # reduce to INFO in production
```

Start with external config:

```bash
java -jar canet-app-1.0.0-SNAPSHOT.jar \
     --spring.profiles.active=prod \
     --spring.config.additional-location=/etc/canet/
```

### 11.3 Docker in production

```bash
docker run -d \
  --name canet-app \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /etc/canet:/etc/canet:ro \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=/etc/canet/ \
  canet/app:latest
```

### 11.4 SSL / TLS

The application speaks plain HTTP on port 8080. TLS termination should be handled by the upstream reverse proxy (nginx, AWS ALB, etc.). This is the standard pattern for containerised Spring Boot applications.

If TLS must be terminated in the application itself:

```yaml
server:
  port: 8443
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

---

## 12. Environment Variable Overrides

Spring Boot maps environment variables to properties by uppercasing and replacing `.` and `-` with `_`.

| Environment variable | Equivalent YAML key | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `dev` |
| `SERVER_PORT` | `server.port` | `9090` |
| `CANET_ENDPOINTS_0_URL` | `canet.endpoints[0].url` | `http://api:8081/handsetdetails` |
| `CANET_ENDPOINTS_1_URL` | `canet.endpoints[1].url` | `http://api:8081/handsetdetails` |
| `CANET_ENDPOINTS_0_MAX_ENTRIES` | `canet.endpoints[0].max-entries` | `10` (HANDSET_SEARCH_CONFIG) |
| `CANET_ENDPOINTS_1_MAX_ENTRIES` | `canet.endpoints[1].max-entries` | `5`  (CELL_SEARCH_CONFIG) |
| `CANET_ENDPOINTS_0_SUBMIT_DELAY_SECONDS` | `canet.endpoints[0].submit-delay-seconds` | `5` |
| `LOGGING_LEVEL_COM_CANET` | `logging.level.com.canet` | `INFO` |

---

## 13. Health & Monitoring

Spring Boot Actuator is included and exposes two endpoints:

| URL | Purpose |
|---|---|
| `http://localhost:8080/actuator/health` | Liveness + readiness; returns `{"status":"UP"}` when healthy |
| `http://localhost:8080/actuator/info` | Application metadata |

These are used as healthcheck targets in the `docker-compose.yml` and can be wired to Kubernetes probes, AWS ALB target group health checks, or any monitoring tool.

### Kubernetes example

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 5
```

---

## 14. Running Tests

### 14.1 All tests

```bash
# Main app (27 tests)
cd spring-boot-app && mvn test

# Mock server (30 tests)
cd mock-server && mvn test
```

### 14.2 Test suite breakdown

**spring-boot-app (27 tests):**

| Class | Tests | Scope | What it covers |
|---|---|---|---|
| `DataControllerTest` | 13 | `@WebMvcTest` | Handset mode, cell IMEI→TAC extraction, `inputValue` annotation, not-found mapping, flatten, CSV export |
| `HttpsDataServiceTest` | 7 | `@RestClientTest` | `MockRestServiceServer`; fetchAll, fetchById, fetchByTacs envelope parsing, 404 and 500 handling |
| `MockApiServerTest` | 6 | `@SpringBootTest` + WireMock | Full integration: WireMock HTTP stub → RestTemplate → controller → rendered HTML |
| `CaNetApplicationTests` | 1 | `@SpringBootTest` | Context loads without errors |

**mock-server (30 tests):**

| Category | Tests | Key assertions |
|---|---|---|
| Full list | 4 | 20 records, nested `network`/`capabilities` objects present |
| Cell-mode TAC pairs | 1 | All 10 IMEI-derived TACs are in the dataset |
| Manufacturer filter | 6 | Samsung/Apple/Google return 3 each; Nokia returns 1; Blackberry returns 0 |
| Single TAC | 4 | Known TAC, nested fields, cell-mode derived TAC, unknown 404 |
| Multi-TAC | 6 | Mixed found/not-found, 20-TAC limit, 21-TAC truncation |
| IMEI list | 5 | TAC extraction from full 15-digit IMEI, dedup, no match |
| Alternate path | 1 | `/handsets` parity with `/handsetdetails` |

---

## 15. Troubleshooting

### Page displays with no styling (all content stacked, no layout, no table borders)

**Cause:** Bootstrap CSS is not loading.

**With WebJar (current setup):** Bootstrap is bundled inside the JAR — this should never happen unless the JAR is corrupt. Verify:
```bash
jar tf canet-app-*.jar | grep bootstrap
# Expected: BOOT-INF/lib/bootstrap-5.3.3.jar
```

**With old CDN setup:** `cdn.jsdelivr.net` is blocked by corporate firewall. The solution (already applied) is to use the WebJar.

---

### Port already in use

```bash
# Find and kill process using port 8080
lsof -ti:8080 | xargs kill -9

# Or change the port
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

---

### `Connection refused` when calling mock server

The main app (dev profile) calls `http://localhost:8081`. Ensure the mock server is running:

```bash
curl http://localhost:8081/handsetdetails/35674108
# Should return: {"tac":"35674108","marketingName":"Samsung Galaxy S24 Ultra",...}
```

If using Docker, the mock server is on a different hostname. Use the Compose file (which injects `CANET_ENDPOINTS_0_URL=http://mock:8081/handsetdetails`) rather than running containers manually.

---

### TAC not found (handset mode)

Verify the TAC is exactly 8 digits and exists in the dataset:

```bash
curl http://localhost:8081/handsetdetails?tac=35674108
# Expected: {"requested":1,"found":1,"results":[...],"notFound":[]}
```

If `found` is 0, the TAC is not in the mock server's 20-record dataset. Use one of the known TACs listed in Section 4.3.

---

### IMEI not resolving (cell mode)

Cell mode extracts the first 8 characters of the IMEI as the TAC. Verify:

```
IMEI: 353456789012345
TAC:  35345678   ← first 8 chars

curl http://localhost:8081/handsetdetails/35345678
# Should return Samsung Galaxy S23 Ultra
```

If the IMEI is valid (15 digits) but the device isn't found, the TAC prefix is not in the mock dataset. Use one of the IMEIs listed in Section 4.3.

---

### Validation errors on submit

| Error | Cause | Fix |
|---|---|---|
| "Must be exactly 8 digits — found N digits" | Wrong length TAC | Enter exactly 8 digits |
| "Must be exactly 15 digits — found N digits" | Wrong length IMEI | Enter exactly 15 digits |
| "contains non-digit characters" | Letters or symbols in input | TACs/IMEIs are digits only |
| "Exceeds limit of 20 entries" | More than max-entries lines | Reduce entries or raise `max-entries` in config |

---

### Docker Compose — main app starts before mock is ready

The Compose file uses `depends_on` with `condition: service_healthy`. The mock server healthcheck must pass before the main app starts. If startup is slow (e.g., on a low-resource machine), increase `start_period` in `docker-compose.yml`:

```yaml
healthcheck:
  start_period: 30s   # increase from 15s
```

---

### `connectTimeout` / `readTimeout` compilation error

If you see:
```
[ERROR] symbol: method connectTimeout(java.time.Duration)
```

This means an older version of the code used `RestTemplateBuilder.connectTimeout()` which was removed in Spring Boot 3.2. The current code correctly uses `setConnectTimeout()` and `setReadTimeout()`. Pull the latest branch.
