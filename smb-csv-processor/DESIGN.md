# SMB CSV Processor — Requirements, Design & Development

## Table of Contents

1. [Overview](#1-overview)
2. [Requirements](#2-requirements)
3. [Architecture Design](#3-architecture-design)
4. [Component Design](#4-component-design)
5. [Data Design](#5-data-design)
6. [Configuration Design](#6-configuration-design)
7. [Development Guide](#7-development-guide)

---

## 1. Overview

**SMB CSV Processor** is a Java Spring Boot batch service that automates the ingestion and processing of network cell site configuration data delivered as zipped CSV files on an SMB share.

The service:
- Polls a remote SMB share for new ZIP files
- Downloads, extracts, and processes each CSV through a configurable mapping and validation pipeline
- Writes valid rows to a success output file (IE/AA quoted-uppercase format)
- Quarantines invalid rows with a reason column
- Uploads success files to a remote SFTP server
- Retries failed uploads automatically

---

## 2. Requirements

### 2.1 Functional Requirements

| ID | Requirement |
|---|---|
| FR-01 | The system shall poll a configurable SMB share at a configurable interval for new ZIP files |
| FR-02 | Downloaded ZIP files shall be extracted to a local staging directory |
| FR-03 | The system shall delete the remote SMB file after a successful download |
| FR-04 | The system shall delete local input ZIP and CSV files after successful processing |
| FR-05 | Each CSV row shall be validated against a YAML-driven mapping configuration |
| FR-06 | Valid rows shall be written to a success output file in IE/AA quoted-uppercase format |
| FR-07 | Invalid rows shall be written to a quarantine file with a `QUARANTINE_REASON` column |
| FR-08 | Success output files shall be uploaded to a configurable SFTP server |
| FR-09 | Files that fail SFTP upload shall be automatically retried on a configurable interval |
| FR-10 | The system shall support generation-aware formulas for 4G and 5G network parameters |
| FR-11 | The system shall support a test mode that bypasses SMB and SFTP, accepting files via a local drop directory |
| FR-12 | Output file name prefixes and timestamp format shall be configurable |

### 2.2 Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-01 | Processing shall be fully streaming — no CSV file shall be fully loaded into memory |
| NFR-02 | The service shall handle arbitrarily large files (tested up to multi-GB CSVs) |
| NFR-03 | The service shall run as a non-root user inside a Docker container |
| NFR-04 | All configuration shall be externalisable via environment variables |
| NFR-05 | The application shall produce rolling, async log files with 30-day retention |
| NFR-06 | The service shall be deployable in an offline/air-gapped environment with no internet access |
| NFR-07 | SMB connectivity shall support SMB 2.x and SMB 3.x (NTLMv2 authentication) |
| NFR-08 | SFTP connectivity shall support both password and private key authentication |

### 2.3 Field Processing Requirements

Each output field may be:

- **Direct** — a source column copied (and transformed) to an output column
- **Formula-derived** — computed from multiple source columns using a named formula

Supported transformations: `TRIM`, `UPPERCASE`, `LOWERCASE`, `REMOVE_SPACES`, `NUMERIC_ONLY`, `REMOVE_NON_PRINTABLE`

Supported validation types: `STRING`, `INTEGER`, `DECIMAL`, `BOOLEAN`

Supported validation constraints: pattern (regex), min/max length, numeric range, allowed values list

### 2.4 Network Generation Formulas

| Formula | 4G Behaviour | 5G Behaviour |
|---|---|---|
| `calculateNodebId` | reads `enodeb_id` | reads `gnodeb_id` |
| `calculateTac` | reads `4g_tac` (0–65535) | reads `5g_tac` (0–16777215) |
| `calculateIpAddress` | reads `4g_enb_ip_address` | reads `5g_gnb_ip_address` |
| `calculatePci` | reads `4g_pci` (0–503) | reads `5g_pci` (0–1007) |
| `calculateCbMhz` | reads `4g_cb_mhz` | reads `5g_cb_mhz` |
| `deriveEci` | `eNB_ID × 256 + (cell_id & 0xFF)` | `gNB_ID × 2^(36 − gNB_ID_length) + cell_id` (3GPP TS 38.413) |
| `deriveLatitude` | BNG OSGB36 easting/northing → WGS84 latitude (OS 7-parameter Helmert) |
| `deriveLongitude` | BNG OSGB36 easting/northing → WGS84 longitude (OS 7-parameter Helmert) |

> **5G NCI formula:** uses `BigInteger` arithmetic to handle the full 36-bit range without integer overflow.

---

## 3. Architecture Design

### 3.1 High-Level Flow

```
SMB Share ──► SmbDownloadService ──► input/zip/
                                          │
                                    ZipExtractionRoute
                                    (Apache Camel)
                                          │
                                     input/csv/
                                          │
                                   CsvProcessingRoute
                                   (Apache Camel)
                                    │           │
                              output/success/  output/quarantine/
                                    │
                              SftpUploadRoute ──► SFTP Server
                                    │
                              (on failure)
                              output/success/.uploadError/
                                    │
                              RetryUploadRoute (every 15 min)
                                    │
                              output/success/   (retry)
```

### 3.2 Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Runtime | Java | 17 |
| Framework | Spring Boot | 3.5.3 |
| Routing | Apache Camel | 4.18.0 |
| SMB Client | jcifs-ng | 2.1.3 |
| SFTP Client | Apache MINA SSHD | (via camel-ftp) |
| CSV Parsing | Apache Commons CSV | 1.9.0 |
| ZIP Extraction | Apache Commons Compress | 1.21 |
| YAML Parsing | SnakeYAML | (managed by Spring Boot) |
| Logging | Logback + SLF4J | (managed by Spring Boot) |
| Container | Docker (eclipse-temurin JRE 11) | multi-stage |

> **Offline environment note:** No BOM imports are used in `pom.xml`. All dependency versions are declared explicitly to work in air-gapped Maven repositories that carry specific JARs but not BOM POM artifacts.

### 3.3 Processing Modes

| Mode | SMB Polling | SFTP Upload | File Injection |
|---|---|---|---|
| **Production** | Active | Active | Via SMB share |
| **Test** | Disabled | Disabled | Drop files into `test-drop/` directory |

Test mode is controlled by `processing.test-mode.enabled=true` and disables `SmbPollingRoute` and `SftpUploadRoute` via `@ConditionalOnProperty`.

---

## 4. Component Design

### 4.1 Package Structure

```
com.example.csvprocessor/
├── CsvProcessorApplication.java       Entry point (@EnableIntegration, @EnableScheduling)
├── config/
│   ├── DirectoryProperties.java       Local directory paths (@ConfigurationProperties)
│   ├── OutputProperties.java          Output file naming settings (@ConfigurationProperties)
│   ├── SftpProperties.java            SFTP connection settings (@ConfigurationProperties)
│   ├── SmbProperties.java             SMB connection settings (@ConfigurationProperties)
│   └── TestModeProperties.java        Test mode toggle (@ConfigurationProperties)
├── model/
│   ├── FieldMapping.java              One output column definition
│   ├── InputFieldRef.java             Reference to a source column (name or index)
│   ├── MappingConfiguration.java      Root YAML mapping model
│   ├── ProcessingResult.java          Row counts + output file references
│   ├── RangeRule.java                 Numeric range constraint (min/max as String)
│   └── ValidationRule.java           All validation constraints for one field
├── route/
│   ├── CsvProcessingRoute.java        Camel: input/csv/ → CsvRowProcessor
│   ├── RetryUploadRoute.java          Camel: .uploadError/ → output/success/ (retry)
│   ├── SftpUploadRoute.java           Camel: output/success/ → SFTP server
│   ├── SmbPollingRoute.java           Camel: SMB share → SmbDownloadService
│   ├── TestDropRoute.java             Camel: test-drop/ → input dirs (test mode only)
│   └── ZipExtractionRoute.java        Camel: input/zip/ → ZipExtractorService
└── service/
    ├── BngConverter.java              OSGB36 BNG → WGS84 (OS Helmert transformation)
    ├── CsvRowProcessor.java           Core streaming CSV pipeline (map/transform/validate/write)
    ├── FormulaService.java            Named formula evaluation (generation-aware)
    ├── MappingConfigService.java      Loads and caches mapping.yml at startup
    ├── SmbDownloadService.java        jcifs-ng SMB2/3 file download + remote delete
    └── ZipExtractorService.java       ZIP streaming extraction (Commons Compress)
```

### 4.2 Camel Routes

#### ZipExtractionRoute
```
from(file://input/zip?include=*.zip&readLock=changed&delete=true&moveFailed=.error)
  → ZipExtractorService.extractZip()
  → extracted CSVs land in input/csv/
```
- `delete=true` — ZIP deleted after successful extraction (no `.done` accumulation)
- `moveFailed=.error` — failed ZIPs preserved for investigation
- `maxMessagesPerPoll=1` — one ZIP at a time
- `readLockTimeout=1h` — waits for slow downloads to stabilise

#### CsvProcessingRoute
```
from(file://input/csv?include=*.csv&readLock=changed&delete=true&moveFailed=.error)
  → CsvRowProcessor.processFile()
  → success file → output/success/
  → quarantine file → output/quarantine/
```
- `delete=true` — CSV deleted after successful processing
- `readLockTimeout=2h` — accommodates very large CSV files

#### SftpUploadRoute
```
from(file://output/success?include=*.csv&readLock=changed&move=.uploaded&moveFailed=.uploadError)
  → to(sftp://host:22/remote/dir)
```
- `moveFailed=.uploadError` — failed uploads held for retry (not lost)
- No `handled(true)` — exceptions propagate so Camel applies `moveFailed`

#### RetryUploadRoute
```
from(file://output/success/.uploadError?delay=900000&move=../&moveFailed=.retryFailed)
  → log("moving back for retry")
```
- Polls every 15 minutes (configurable via `processing.output.upload-error-retry-interval-ms`)
- Disabled by `processing.output.upload-error-retry-enabled=false`

### 4.3 CsvRowProcessor — Processing Pipeline

For each row in the input CSV:

```
1. Build rowContext map  (lower-cased column name → raw value)
        ↓
2. For each FieldMapping:
   a. Extract value
      ├── Formula field  → FormulaService.evaluate(formula, rowContext)
      └── Direct field   → record.get(sourceColumnName or sourceColumnIndex)
        ↓
   b. Apply transformations (TRIM, UPPERCASE, ...)
        ↓
   c. Nullable check  →  if required and blank: quarantine
        ↓
   d. Type validation  →  INTEGER / DECIMAL / BOOLEAN
        ↓
   e. Constraint validation  →  pattern, length, range, allowedValues
        ↓
3. All fields valid  →  successWriter.println(quotedRow)
   Any field invalid →  quarantineWriter.println(quotedRow + QUARANTINE_REASON)
```

**Streaming constants:**
- Read buffer: 64 KB
- Write buffer: 128 KB
- Flush every: 50,000 rows
- Progress log every: 10,000 rows

### 4.4 FormulaService — Generation Detection

Generation is determined by the `generation` field in the row context:

```java
boolean is5G = rowContext.getOrDefault("generation","").toUpperCase().contains("5G");
```

All formula methods receive the full `rowContext` map and return a `String` result (or `null` if inputs are missing/invalid).

### 4.5 MappingConfigService — YAML Loading

```
mapping.yml
    │
    ├── SnakeYAML.load()  →  Map<String, Object>  (raw tree, no typed Constructor)
    │
    └── Manual map traversal  →  MappingConfiguration
                                      └── List<FieldMapping>
                                              ├── List<InputFieldRef>
                                              └── ValidationRule
                                                      └── RangeRule
```

> Jackson is deliberately **not used**. The offline environment carries only the base `spring-boot-starter` which does not include `jackson-databind`. SnakeYAML (managed by Spring Boot) is sufficient for all YAML parsing needs.

### 4.6 SmbDownloadService

1. Connect to SMB share using jcifs-ng (`SmbFile`, `NtlmPasswordAuthenticator`)
2. List files matching the configured pattern
3. For each file: stream to a local `.tmp` file, then atomic rename to final path
4. Delete the remote file after successful rename
5. Log a warning (non-fatal) if remote delete fails

**Valid `DialectVersion` values:** `SMB202`, `SMB210`, `SMB300`, `SMB302`, `SMB311`
(Note: `SMB2` is not a valid enum value in jcifs-ng 2.x)

---

## 5. Data Design

### 5.1 Directory Layout

```
/data/processor/
  input/
    zip/                  ZIPs downloaded from SMB (deleted on successful extraction)
      .error/             ZIPs that failed extraction
    csv/                  Extracted CSVs awaiting processing (deleted on success)
      .error/             CSVs that caused a processing exception
  output/
    success/              Valid-row output CSVs — picked up by SftpUploadRoute
      .uploaded/          Successfully uploaded files
      .uploadError/       Failed SFTP uploads — retried by RetryUploadRoute
      .retryFailed/       Files that failed even after retry (manual action needed)
    quarantine/           Invalid-row CSVs with QUARANTINE_REASON column
    archive/              Reserved for manual archival
  test-drop/              (test mode) drop CSV or ZIP here to inject into pipeline
```

### 5.2 Output File Naming

```
<sourceBaseName>_<prefix>_<timestamp>.csv

Examples:
  cells_export_success_20240101120000.csv
  cells_export_quarantine_20240101120000.csv
```

Configurable via `application.properties`:
```properties
processing.output.success-prefix=success
processing.output.quarantine-prefix=quarantine
processing.output.timestamp-format=yyyyMMddHHmmss
```

### 5.3 Output CSV Format (IE/AA Quoted Uppercase)

All headers and values are wrapped in double quotes and uppercased:

```
"MCC","MNC","LAC","CELL_ID","GENERATION","TAC","PCI","DERIVED_ECI",...
"234","30","1234","5","4G","100","200","256005",...
```

Quarantine files append a `QUARANTINE_REASON` column:
```
"MCC","MNC",...,"QUARANTINE_REASON"
"","30",...,"MCC: required field is empty"
```

### 5.4 Mapping Configuration (mapping.yml)

```yaml
mapping:
  inputDelimiter: ","
  outputDelimiter: ","
  hasHeader: true
  encoding: UTF-8
  skipLines: 0
  outputFormat: QUOTED_UPPERCASE

  fields:
    # Direct field example
    - sourceColumnName: mcc
      targetColumnName: MCC
      expectedType: INTEGER
      nullable: false
      transformations: [TRIM]
      validation:
        range:
          min: "1"
          max: "999"

    # Formula field example
    - targetColumnName: TAC
      inputFields:
        - name: 4g_tac
        - name: 5g_tac
        - name: generation
      formula: calculateTac
      expectedType: INTEGER
      nullable: true
```

---

## 6. Configuration Design

### 6.1 application.properties Reference

```properties
# SMB Source
smb.host=smb-server.example.com
smb.port=445
smb.domain=
smb.username=svc_reader
smb.password=change_me
smb.share-name=shared
smb.remote-directory=/uploads/daily
smb.file-pattern=*.zip
smb.polling-interval-ms=300000
smb.min-smb-version=SMB202       # Valid: SMB202|SMB210|SMB300|SMB302|SMB311
smb.max-smb-version=SMB311
smb.connect-timeout-ms=30000
smb.response-timeout-ms=60000

# SFTP Destination
sftp.host=sftp-server.example.com
sftp.port=22
sftp.username=svc_uploader
sftp.password=change_me
sftp.private-key-path=            # leave blank for password auth
sftp.private-key-passphrase=
sftp.known-hosts-file=
sftp.remote-directory=/inbound/processed
sftp.connect-timeout-ms=30000

# Local Directories
processing.directories.input-zip=/data/processor/input/zip
processing.directories.input-csv=/data/processor/input/csv
processing.directories.output-success=/data/processor/output/success
processing.directories.output-quarantine=/data/processor/output/quarantine
processing.directories.output-archive=/data/processor/output/archive

# Output File Naming
processing.output.success-prefix=success
processing.output.quarantine-prefix=quarantine
processing.output.timestamp-format=yyyyMMddHHmmss
processing.output.upload-error-retry-enabled=true
processing.output.upload-error-retry-interval-ms=900000

# Test Mode
processing.test-mode.enabled=false
processing.test-mode.drop-directory=/data/processor/test-drop
```

### 6.2 Environment Variable Override

Spring Boot maps environment variables to properties automatically (dots → underscores, hyphens → underscores):

| Property | Environment Variable |
|---|---|
| `smb.host` | `SMB_HOST` |
| `smb.password` | `SMB_PASSWORD` |
| `sftp.host` | `SFTP_HOST` |
| `sftp.password` | `SFTP_PASSWORD` |
| `sftp.private-key-path` | `SFTP_PRIVATE_KEY_PATH` |
| `processing.test-mode.enabled` | `PROCESSING_TEST_MODE_ENABLED` |

### 6.3 Logging Configuration (logback-spring.xml)

| Appender | Description |
|---|---|
| `CONSOLE` | Coloured, human-readable — development use |
| `FILE_ROLLING` | Daily rotation, 1 GB/file, 30-day retention, 10 GB total cap, gzip |
| `ASYNC_FILE` | Non-blocking wrapper around FILE_ROLLING (queue depth 2048) |

Log levels:
- `com.example.csvprocessor` → `DEBUG`
- `org.apache.camel` → `INFO`
- `jcifs` → `WARN`
- Root → `INFO`

---

## 7. Development Guide

### 7.1 Prerequisites

- JDK 17+
- Maven 3.8+
- Docker (for container builds)

### 7.2 Building

```bash
# Compile and package fat JAR
mvn -DskipTests package

# Run all unit tests
mvn test

# Full build with tests
mvn package
```

### 7.3 Running Locally (Test Mode)

No SMB or SFTP server needed. Set `processing.test-mode.enabled=true` in `application.properties`
and adjust local directory paths, then:

```bash
java -jar target/smb-csv-processor-1.0.0-SNAPSHOT.jar \
  --processing.test-mode.enabled=true \
  --processing.directories.input-zip=C:/processor/input/zip \
  --processing.directories.input-csv=C:/processor/input/csv \
  --processing.directories.output-success=C:/processor/output/success \
  --processing.directories.output-quarantine=C:/processor/output/quarantine \
  --processing.directories.output-archive=C:/processor/output/archive \
  --processing.test-mode.drop-directory=C:/processor/test-drop
```

Drop a `.csv` or `.zip` file into the `test-drop` directory and watch the output.

### 7.4 SMB Simulation on Windows (Development)

To simulate an SMB share on the local `C:` drive:

1. Share a local folder (right-click → Properties → Sharing)
2. Enable loopback connections (run PowerShell as Administrator):
   ```powershell
   Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\LanmanServer\Parameters" `
       -Name "DisableLoopbackCheck" -Value 1 -Type DWord
   ```
3. Configure `application.properties`:
   ```properties
   smb.host=localhost
   smb.share-name=YourShareName
   smb.username=YourWindowsUsername
   smb.password=YourWindowsPassword
   smb.domain=
   ```

### 7.5 Adding a New Formula

1. Add the formula method to `FormulaService.java`:
   ```java
   private String calculateMyField(Map<String, String> row) {
       String val = row.getOrDefault("source_column", "");
       if (val.isEmpty()) return null;
       // ... compute ...
       return result;
   }
   ```
2. Register it in the `evaluate()` switch:
   ```java
   case "calculateMyField": return calculateMyField(rowContext);
   ```
3. Reference it in `mapping.yml`:
   ```yaml
   - targetColumnName: MY_FIELD
     inputFields:
       - name: source_column
     formula: calculateMyField
     nullable: true
   ```

### 7.6 Adding a New Validation Constraint

1. Add a field to `ValidationRule.java` with getter/setter
2. Add the mapping entry to `MappingConfigService.toValidationRule()`
3. Add the check to `CsvRowProcessor.validate()`
4. Use it in `mapping.yml` under the field's `validation:` block

### 7.7 Unit Testing

Tests use JUnit 5 + Mockito with no Spring context. Dependencies are wired manually via `ReflectionTestUtils`:

```java
processor = new CsvRowProcessor();
ReflectionTestUtils.setField(processor, "mappingConfigService", mockMappingService);
ReflectionTestUtils.setField(processor, "formulaService", formulaService);
ReflectionTestUtils.setField(processor, "directoryProperties", dirs);
ReflectionTestUtils.setField(processor, "outputProperties", new OutputProperties());
```

All five test methods in `CsvRowProcessorTest` cover: valid rows (IE/AA format), nullable violations, range violations, no-quarantine-file-when-all-valid, and formula field (`deriveEci`).

### 7.8 Known Constraints

| Constraint | Detail |
|---|---|
| Offline Maven repo | No BOM POM artifacts available — all dependency versions must be declared explicitly in `pom.xml` |
| SnakeYAML version | Spring Boot 3.5.3 manages SnakeYAML 2.x — do not use the old `Constructor(Class<?>)` API |
| Jackson | Not available in the offline environment — `MappingConfigService` uses pure SnakeYAML map traversal |
| jcifs-ng `DialectVersion` | Valid values: `SMB202`, `SMB210`, `SMB300`, `SMB302`, `SMB311` — `SMB2` does not exist |
| Camel 4.x SFTP | Uses Apache MINA SSHD — do not add `com.jcraft:jsch` dependency |
| Jakarta EE | Spring Boot 3.x uses `jakarta.*` namespace — all `javax.annotation.*` imports must be updated |
