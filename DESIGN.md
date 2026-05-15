# CaNet — NEO Network Equipment Observer: Design Document

## 1. Overview

CaNet is a two-module Spring Boot system for querying handset/device details by TAC (Type Allocation Code) or IMEI. It replaces a legacy JavaScript stack (Node.js, npm, node_modules, webpack) with a JVM-only solution that eliminates client-side dependency management, CORS issues, and SSL certificate wrangling.

```
┌──────────────────────────────────────────────────────────────────┐
│  spring-boot-app  (port 8080)                                    │
│  Thymeleaf UI  →  DataController  →  HttpsDataService            │
│                                           │                       │
│                                     RestTemplate (HTTPS/HTTP)     │
└──────────────────────────────────────────┼───────────────────────┘
                                           │ HTTP
                              ┌────────────▼────────────────────────┐
                              │  mock-server  (port 8081)            │
                              │  HandsetController                   │
                              │  HandsetRepository (in-memory)       │
                              └──────────────────────────────────────┘
```

The main app and mock server are completely independent Spring Boot applications. In production the main app points at a real API; in development (`--spring.profiles.active=dev`) it points at the local mock server.

---

## 2. Why Not JavaScript?

| Concern | JavaScript Approach | CaNet / Spring Boot |
|---|---|---|
| HTTPS / SSL | Browser CORS policy, cert pinning | Server-side `RestTemplate`; JVM trust store |
| Dependencies | `node_modules`, `package-lock.json`, webpack | Single `pom.xml`; Maven Central |
| CI maintenance | Node version matrix, npm audit | JDK LTS version only |
| Dynamic fields | Manual DOM manipulation or a framework | `Map<String, Object>` + Thymeleaf; zero JS for table |
| Testing | Jest, Puppeteer, WireMock JS | `@WebMvcTest`, `@RestClientTest`, `MockRestServiceServer` |

---

## 3. Module Layout

```
canet/
├── spring-boot-app/          # Main application (port 8080)
│   ├── src/main/java/com/canet/app/
│   │   ├── CaNetApplication.java
│   │   ├── config/CaNetProperties.java
│   │   ├── controller/DataController.java
│   │   ├── service/HttpsDataService.java
│   │   ├── service/HttpsDataServiceImpl.java
│   │   └── util/JsonFlattener.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── templates/
│   │       ├── index.html        (main search UI)
│   │       └── about.html        (benefits comparison)
│   └── src/test/java/com/canet/app/
│       ├── controller/DataControllerTest.java
│       ├── service/HttpsDataServiceTest.java
│       └── mock/MockApiServerTest.java
│
└── mock-server/              # Simulated handset API (port 8081)
    ├── src/main/java/com/canet/mock/
    │   ├── controller/HandsetController.java
    │   ├── data/HandsetRepository.java
    │   └── model/
    │       ├── HandsetRecord.java
    │       ├── NetworkInfo.java
    │       ├── DeviceCapabilities.java
    │       └── TacSearchResponse.java
    └── src/test/java/com/canet/mock/
        └── controller/HandsetControllerTest.java
```

---

## 4. Data Model

### 4.1 HandsetRecord (mock-server)

```
HandsetRecord
├── tac                  String   — 8-digit Type Allocation Code
├── marketingName        String   — consumer device name
├── manufacturer         String   — OEM
├── modelName            String   — part number
├── operatingSystem      String   — "Android" | "iOS"
├── osVersion            String   — version at launch
├── network              NetworkInfo
│   ├── generations      String   — "2G/3G/4G/5G"
│   └── type             String   — "NSA" | "SA" | "NSA/SA" | "LTE"
├── displaySizeInches    double
├── releaseYear          int
└── capabilities         DeviceCapabilities
    ├── nfc              boolean
    └── wirelessCharging boolean
```

`network` and `capabilities` are intentionally nested. When the main app is configured with `flatten: true` and `flatten-separator: "-"`, they arrive in the UI as flat columns:

```
network.generations       →  network-generations
network.type              →  network-type
capabilities.nfc          →  capabilities-nfc
capabilities.wirelessCharging → capabilities-wirelessCharging
```

### 4.2 TacSearchResponse (mock-server API envelope)

```json
{
  "requested": 3,
  "found":     2,
  "results":   [ { ...HandsetRecord... }, { ...HandsetRecord... } ],
  "notFound":  [ "99999999" ]
}
```

### 4.3 HandsetRepository (in-memory dataset)

20 records in two groups:

- **Original 10** — diverse manufacturers/OS versions (Samsung S24 Ultra, Apple iPhone 15 Pro Max, Google Pixel 8 Pro, OnePlus 12, Sony Xperia 1 VI, Xiaomi 14 Pro, Motorola Edge 50 Pro, Samsung Galaxy A54, Apple iPhone SE 3rd Gen, Google Pixel 7a)

- **Cell-mode 10** — each record has a documented 15-digit IMEI whose first 8 digits are the TAC:

| IMEI              | TAC      | Device                  |
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

## 5. Mock Server API

Base URL: `http://localhost:8081`

| Method | Path | Parameters | Response |
|--------|------|------------|----------|
| GET | `/handsetdetails` | — | `List<HandsetRecord>` (all 20) |
| GET | `/handsetdetails` | `?manufacturer=Samsung` | `List<HandsetRecord>` (filtered, case-insensitive) |
| GET | `/handsetdetails/{tac}` | — | `HandsetRecord` or 404 |
| GET | `/handsetdetails` | `?tac=T1,T2,...` (≤ 20) | `TacSearchResponse` envelope |
| GET | `/handsets` | same as above | alias path |
| GET | `/handsets/{tac}` | — | alias path |
| GET | `/rs/v1/handsetbyIMEIList` | `?imeis=I1,I2,...` (≤ 20) | `TacSearchResponse` envelope |

For `/rs/v1/handsetbyIMEIList`, each IMEI is trimmed to its first 8 characters to derive the TAC before lookup. Duplicate IMEIs are de-duplicated with `distinct()` before the search.

---

## 6. Main Application

### 6.1 Configuration — CaNetProperties

All endpoint behaviour is driven by YAML, with no code changes required when adding new endpoints or changing field mappings.

```yaml
canet:
  endpoints:
    - name: handset-search
      type: handset           # "handset" | "cell"
      url: http://localhost:8081/handsetdetails
      default-endpoint: true
      compact-fields: [tac, marketingName, manufacturer, modelName, operatingSystem]
      detail-fields: ["*"]    # "*" = all fields not in compact-fields
      labels:
        tac: "TAC"
        network-generations: "Network Generations"
        capabilities-nfc: "NFC Supported"
      flatten: true
      flatten-separator: "-"
      submit-delay-seconds: 3
      validation-pattern: "^\\d{8}$"
      validation-message: "Must be exactly 8 digits"
      max-entries: 20         # HANDSET_SEARCH_CONFIG
```

Key `EndpointMapping` fields:

| Field | Type | Purpose |
|---|---|---|
| `type` | String | Selects the endpoint when the UI switches between Handset / Cell mode |
| `compact-fields` | List\<String\> | Columns shown in the collapsed table row |
| `detail-fields` | List\<String\> | Columns shown in the expand panel; `["*"]` means all remaining |
| `labels` | Map\<String,String\> | Column header overrides; keys use post-flatten names |
| `flatten` | boolean | Recursively flatten nested JSON before display |
| `flatten-separator` | String | Separator inserted between key segments (default `-`) |
| `submit-delay-seconds` | int | Countdown before the Submit button activates |
| `validation-pattern` | String | Client-side regex; empty = no validation |
| `max-entries` | int | Hard cap on entries per search (HANDSET_SEARCH_CONFIG / CELL_SEARCH_CONFIG) |

### 6.2 Service Layer — HttpsDataServiceImpl

```
RestTemplateBuilder
  .setConnectTimeout(5s)
  .setReadTimeout(10s)
  .build()
```

Uses `ParameterizedTypeReference<List<Map<String, Object>>>` so the deserialized type is fully generic — no model class changes are needed when the API adds or removes fields.

Three methods:

| Method | Used by |
|---|---|
| `fetchAll(url)` | `/api/data` REST endpoint |
| `fetchById(url, id)` | `/api/data/{id}` REST endpoint |
| `fetchByTacs(url, tacs)` | Main search for both handset and cell modes |

`fetchByTacs` appends `?tac=T1,T2,...` and parses the `TacSearchResponse` envelope, returning a `TacSearchResult(results, notFound)` record.

### 6.3 Controller — DataController

#### Handset mode flow

```
GET /?mode=handset&entries=35674108,35282402
  → split on comma, trim, limit to maxEntries
  → fetchByTacs(url, [tacs])
  → if flatten: JsonFlattener.flatten(row, separator) for each row
  → resolve compactFields / detailFields from available keys
  → render index.html via Thymeleaf model
```

#### Cell mode flow

```
GET /?mode=cell&entries=353456789012345,354567890123456
  → split, trim, limit to maxEntries
  → extract TAC = entry.substring(0, 8) for each IMEI
  → build tacToInput LinkedHashMap (TAC → original IMEI)
  → fetchByTacs(url, extractedTacs)
  → annotate each result row with inputValue = original IMEI
  → map notFound TACs back to original IMEIs
  → if flatten: flatten rows
  → render
```

The `tacToInput` map uses `putIfAbsent` so duplicate IMEIs sharing the same TAC don't overwrite each other; `distinct()` prevents duplicate lookups.

#### CSV export

`GET /api/data/export.csv?mode=handset&entries=...` reuses `doSearch()` then writes RFC-4180 CSV with label-aware headers. The browser downloads it directly via `Content-Disposition: attachment`.

### 6.4 JsonFlattener

```
flatten({network: {generations: "2G/3G/4G/5G", type: "NSA/SA"}}, "-")
→ {"network-generations": "2G/3G/4G/5G", "network-type": "NSA/SA"}
```

Recursive descent — handles arbitrarily deep nesting. Null values and empty strings are dropped from the output (no empty columns in the UI).

---

## 7. UI — index.html

### Mode selection

Two Bootstrap buttons toggle `INIT_MODE` between `handset` and `cell`. Selecting a mode:
1. Updates the textarea placeholder text
2. Resets the entry count badge
3. Resets the submit button countdown from `ENDPOINT_CONFIGS[mode].delay`
4. Applies the correct `validationPattern` on input

### Entry textarea

Auto-resizing single textarea (one entry per line) replaces the earlier fixed 20-row grid. `autoResize()` sets `height = scrollHeight` on every `input` event. The entry count badge turns amber when the limit is reached.

### Validation

Client-side validation (triggered on blur and on submit) reads `ENDPOINT_CONFIGS[mode].pattern` (a regex string injected from YAML via Thymeleaf `th:inline="javascript"`). For digit-count patterns like `^\d{8}$` the JS extracts `8` from the regex to produce the error message:

```
Row 3 (12345): Must be exactly 8 digits — found 5 digits, need 8
Row 5 (abc123xx): Must be exactly 8 digits — contains non-digit characters (abc)
```

Ctrl+Enter is wired as a keyboard shortcut for submit.

### Results table

| Column type | How rendered |
|---|---|
| Compact fields | Always visible `<td>` columns |
| Detail fields | Hidden by default; toggle via expand row button |
| `inputValue` | First column in cell mode — shows the original IMEI |
| Labels | All column headers resolved through `labels` map; fallback to raw field name |

Empty values are not rendered (handled in the server-side flatten step). Rows with no field values don't appear.

### Server → client data handoff

```html
<script th:inline="javascript">
  const INIT_MODE        = /*[[${mode}]]*/ 'handset';
  const SEARCHED_ENTRIES = /*[[${searchedEntries}]]*/ [];
  const ENDPOINT_CONFIGS = /*[[${endpointConfigs}]]*/ {};
</script>
```

Thymeleaf serialises Java `Map`/`List` objects directly to JSON literals — no extra REST call from the browser.

---

## 8. Testing Strategy

### 8.1 Main app (26 tests)

| Test class | Scope | What it covers |
|---|---|---|
| `DataControllerTest` (12) | `@WebMvcTest` | Handset mode, cell IMEI→TAC extraction, `inputValue` annotation, not-found mapping, flatten toggle, CSV export, empty entries |
| `HttpsDataServiceTest` (7) | `@RestClientTest` | `MockRestServiceServer` stubs; fetchAll, fetchById, fetchByTacs envelope parsing, 404 and 500 error handling |
| `MockApiServerTest` (6) | `@SpringBootTest` + WireMock | Full integration: WireMock HTTP stub → RestTemplate → controller → Thymeleaf rendered HTML assertions |
| `CaNetApplicationTests` (1) | `@SpringBootTest` | Context loads without errors |

### 8.2 Mock server (30 tests)

| Test category | Count | Key assertions |
|---|---|---|
| Full list | 4 | 20 records, first record fields, nested network/capabilities objects present, all TACs 8 digits |
| Cell-mode TAC pairs | 1 | All 10 IMEI-derived TACs present in dataset |
| Manufacturer filter | 6 | Samsung/Apple/Google return 3 each; Nokia returns 1; case-insensitive; Blackberry returns 0 |
| Single TAC | 4 | Known TAC, nested fields, cell-mode TAC, unknown 404 |
| Multi-TAC | 6 | Two found, cell-mode TACs, mixed found/not-found, all unknown, single TAC, 20-TAC limit, 21-TAC truncation |
| IMEI list | 5 | TAC extraction from full IMEI, cell-mode IMEIs, short codes, dedup, no match |
| Alternate path | 1 | `/handsets` parity with `/handsetdetails` |

---

## 9. Running the System

### Development (full stack)

```bash
# Terminal 1 — mock server
cd mock-server
mvn spring-boot:run
# Listening on http://localhost:8081

# Terminal 2 — main app with dev profile
cd spring-boot-app
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# UI at http://localhost:8080
```

### Default profile (no mock server)

```bash
cd spring-boot-app
mvn spring-boot:run
# Points at https://jsonplaceholder.typicode.com/todos
```

### Run all tests

```bash
cd mock-server      && mvn test   # 30 tests
cd spring-boot-app  && mvn test   # 26 tests
```

---

## 10. Configuration Reference

### application.yml profiles

| Profile | Endpoints | URL |
|---|---|---|
| default | `todos` (handset), `todos-cell` (cell) | `https://jsonplaceholder.typicode.com/todos` |
| dev | `handset-search` (handset), `cell-search` (cell) | `http://localhost:8081/handsetdetails` |

### Environment variable overrides (Spring externalized config)

```bash
# Override max entries per search
CANET_ENDPOINTS_0_MAX_ENTRIES=10   # HANDSET_SEARCH_CONFIG
CANET_ENDPOINTS_1_MAX_ENTRIES=5    # CELL_SEARCH_CONFIG
```

---

## 11. Adding a New Endpoint

No Java code changes required. Add a new entry to `application.yml`:

```yaml
canet:
  endpoints:
    - name: my-endpoint
      type: handset
      url: https://api.example.com/devices
      compact-fields: [id, name, vendor]
      detail-fields: ["*"]
      labels:
        id: "Device ID"
        name: "Device Name"
      flatten: true
      flatten-separator: "."
      submit-delay-seconds: 2
      validation-pattern: "^[A-Z]{3}\\d{5}$"
      validation-message: "Must be 3 letters followed by 5 digits"
      max-entries: 10
```

The UI, validation, CSV export, and column labels all adapt automatically.

---

## 12. Key Design Decisions

| Decision | Rationale |
|---|---|
| `Map<String, Object>` for all JSON | Schema-agnostic; works with any API response without model changes |
| `LinkedHashMap` for field order | JSON insertion order is preserved through flattening and into the rendered table columns |
| Server-side HTTP calls | Avoids browser CORS, certificate trust, and network topology issues |
| `RestTemplateBuilder` (not `RestTemplate` bean) | Compatible with `@RestClientTest` slice; no separate `HttpClientConfig` class needed |
| Thymeleaf over React/Vue | Zero npm dependencies; no `node_modules`; server renders everything; simpler CI |
| Nested `NetworkInfo`/`DeviceCapabilities` records | Demonstrates the flatten feature end-to-end; one record structure serves both raw and flattened consumers |
| YAML-driven endpoint config | Team can change field mappings, validation, and display labels without a code deployment |
| `putIfAbsent` for tacToInput | Preserves the first IMEI seen when two IMEIs share the same TAC prefix |
