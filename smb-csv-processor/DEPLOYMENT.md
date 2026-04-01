# SMB CSV Processor — Deployment Guide

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Directory Setup](#2-directory-setup)
3. [Configuration](#3-configuration)
4. [Building the Application](#4-building-the-application)
5. [Docker Image](#5-docker-image)
6. [Running in Production](#6-running-in-production)
7. [Running in Test Mode (no SMB/SFTP)](#7-running-in-test-mode-no-smbsftp)
8. [Verifying the Deployment](#8-verifying-the-deployment)
9. [Log Monitoring](#9-log-monitoring)
10. [Updating to a New Version](#10-updating-to-a-new-version)
11. [Directory Reference](#11-directory-reference)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Prerequisites

### Server
- Linux host (Ubuntu 20.04+ or RHEL 8+ recommended)
- Docker 24.x or later
- 2 GB free RAM (JVM heap is capped at 75 % of container memory)
- Network access to the SMB share (port 445) and SFTP destination (port 22)

### Build Machine
- JDK 17 or later
- Maven 3.8+
- Docker (if building the image locally)

---

## 2. Directory Setup

Create the data and log directories on the host **before** starting the container.
The application user inside the container runs as UID/GID 1001 (`csvproc`).

```bash
# Data directories
sudo mkdir -p /srv/processor/data/input/zip
sudo mkdir -p /srv/processor/data/input/csv
sudo mkdir -p /srv/processor/data/output/success
sudo mkdir -p /srv/processor/data/output/quarantine
sudo mkdir -p /srv/processor/data/output/archive
sudo mkdir -p /srv/processor/data/test-drop

# Log directory
sudo mkdir -p /srv/processor/logs

# SSH keys (for SFTP private-key auth — skip if using password auth)
sudo mkdir -p /srv/processor/ssh

# Set ownership so the container's non-root user (1001) can write
sudo chown -R 1001:1001 /srv/processor
```

---

## 3. Configuration

### 3.1 Environment Variables

Spring Boot maps environment variables to properties automatically.
Dots (`.`) become underscores (`_`) and hyphens (`-`) become underscores:

| Property | Environment variable |
|---|---|
| `smb.host` | `SMB_HOST` |
| `smb.password` | `SMB_PASSWORD` |
| `sftp.host` | `SFTP_HOST` |
| `sftp.password` | `SFTP_PASSWORD` |
| `sftp.private-key-path` | `SFTP_PRIVATE_KEY_PATH` |
| `processing.test-mode.enabled` | `PROCESSING_TEST_MODE_ENABLED` |

### 3.2 Config File Override

Mount a custom `application.properties` at `/opt/app/config/application.properties`
inside the container and add `--spring.config.additional-location=/opt/app/config/`
to `JAVA_OPTS`.

### 3.3 SFTP Authentication

**Option A — Password (development/test)**
```properties
sftp.password=your_password
sftp.private-key-path=
```

**Option B — Private key (production)**
```bash
# Copy private key to the SSH directory
sudo cp ~/.ssh/id_rsa /srv/processor/ssh/id_rsa
sudo chmod 600 /srv/processor/ssh/id_rsa
sudo chown 1001:1001 /srv/processor/ssh/id_rsa
```
```properties
sftp.password=
sftp.private-key-path=/opt/app/.ssh/id_rsa
sftp.known-hosts-file=/opt/app/.ssh/known_hosts
```

### 3.4 Output File Naming

Output filenames follow the pattern: `<sourceBaseName>_<prefix>_<timestamp>.csv`

| Property | Default | Description |
|---|---|---|
| `processing.output.success-prefix` | `success` | Prefix for valid-row output files |
| `processing.output.quarantine-prefix` | `quarantine` | Prefix for rejected-row files |
| `processing.output.timestamp-format` | `yyyyMMddHHmmss` | `SimpleDateFormat` pattern |
| `processing.output.upload-error-retry-enabled` | `true` | Retry failed SFTP uploads |
| `processing.output.upload-error-retry-interval-ms` | `900000` | Retry interval (ms) |

### 3.5 SMB Share Simulation (Windows development)

To simulate an SMB share on the local Windows C: drive for development/testing:

1. Enable `SMBv1`/`SMBv2` loopback in Windows (run as Administrator):
   ```powershell
   # Enable loopback (required for connecting to \\localhost\...)
   Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\LanmanServer\Parameters" `
       -Name "DisableLoopbackCheck" -Value 1 -Type DWord
   ```
2. Share a folder: right-click folder → Properties → Sharing → Share
3. Set in `application.properties`:
   ```properties
   smb.host=localhost
   smb.share-name=YourShareName
   smb.username=YourWindowsUsername
   smb.password=YourWindowsPassword
   smb.domain=
   ```

---

## 4. Building the Application

### 4.1 Build the fat JAR

```bash
cd smb-csv-processor
mvn -DskipTests package
# Output: target/smb-csv-processor-<version>.jar
```

### 4.2 Run tests

```bash
mvn test
```

---

## 5. Docker Image

### 5.1 Build with version script (recommended)

**Linux/macOS:**
```bash
cd smb-csv-processor
./docker-build.sh
# Builds smb-csv-processor:<version> and smb-csv-processor:latest
```

**Windows:**
```bat
cd smb-csv-processor
docker-build.bat
```

**Options:**
```bash
./docker-build.sh --no-latest   # skip the :latest tag
./docker-build.sh --push        # push to registry after build
```

### 5.2 Manual build

```bash
# Read version from pom.xml
APP_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

docker build \
    --build-arg APP_VERSION=${APP_VERSION} \
    -t smb-csv-processor:${APP_VERSION} \
    -t smb-csv-processor:latest \
    .
```

### 5.3 Verify the image

```bash
docker inspect smb-csv-processor:latest \
    --format '{{index .Config.Labels "org.opencontainers.image.version"}}'
```

---

## 6. Running in Production

### 6.1 docker run (password auth)

```bash
docker run -d \
  --name smb-csv-processor \
  --restart unless-stopped \
  -v /srv/processor/data:/data/processor \
  -v /srv/processor/logs:/var/log/smb-csv-processor \
  -e SMB_HOST=smb-server.example.com \
  -e SMB_USERNAME=svc_reader \
  -e SMB_PASSWORD=secret \
  -e SMB_SHARE_NAME=shared \
  -e SMB_REMOTE_DIRECTORY=/uploads/daily \
  -e SFTP_HOST=sftp-server.example.com \
  -e SFTP_USERNAME=svc_uploader \
  -e SFTP_PASSWORD=secret \
  smb-csv-processor:latest
```

### 6.2 docker run (private key auth)

```bash
docker run -d \
  --name smb-csv-processor \
  --restart unless-stopped \
  -v /srv/processor/data:/data/processor \
  -v /srv/processor/logs:/var/log/smb-csv-processor \
  -v /srv/processor/ssh:/opt/app/.ssh:ro \
  -e SMB_HOST=smb-server.example.com \
  -e SMB_USERNAME=svc_reader \
  -e SMB_PASSWORD=secret \
  -e SFTP_HOST=sftp-server.example.com \
  -e SFTP_USERNAME=svc_uploader \
  -e SFTP_PRIVATE_KEY_PATH=/opt/app/.ssh/id_rsa \
  smb-csv-processor:latest
```

### 6.3 docker-compose example

```yaml
version: "3.9"
services:
  smb-csv-processor:
    image: smb-csv-processor:latest
    restart: unless-stopped
    volumes:
      - /srv/processor/data:/data/processor
      - /srv/processor/logs:/var/log/smb-csv-processor
      - /srv/processor/ssh:/opt/app/.ssh:ro
    environment:
      SMB_HOST: smb-server.example.com
      SMB_USERNAME: svc_reader
      SMB_PASSWORD: "${SMB_PASSWORD}"
      SMB_SHARE_NAME: shared
      SMB_REMOTE_DIRECTORY: /uploads/daily
      SFTP_HOST: sftp-server.example.com
      SFTP_USERNAME: svc_uploader
      SFTP_PRIVATE_KEY_PATH: /opt/app/.ssh/id_rsa
    mem_limit: 2g
```

---

## 7. Running in Test Mode (no SMB/SFTP)

Test mode bypasses all live SMB and SFTP connections.
Drop CSV or ZIP files into the `test-drop` directory to inject them directly
into the pipeline.

```bash
docker run -d \
  --name smb-csv-processor-test \
  -v /srv/processor/data:/data/processor \
  -v /srv/processor/logs:/var/log/smb-csv-processor \
  -e PROCESSING_TEST_MODE_ENABLED=true \
  smb-csv-processor:latest

# Inject a test file
cp cells_export.csv /srv/processor/data/test-drop/
cp cells_export.zip /srv/processor/data/test-drop/
```

Output files appear in `/srv/processor/data/output/success/` and
`/srv/processor/data/output/quarantine/`.

---

## 8. Verifying the Deployment

```bash
# Check container is running
docker ps --filter name=smb-csv-processor

# Tail the application log
docker logs -f smb-csv-processor

# Check output directories after a test injection
ls -lh /srv/processor/data/output/success/
ls -lh /srv/processor/data/output/quarantine/

# View image version label
docker inspect smb-csv-processor:latest \
    --format '{{index .Config.Labels "org.opencontainers.image.version"}}'
```

---

## 9. Log Monitoring

Logs are written to `/var/log/smb-csv-processor/application.log` inside the
container (mounted as `/srv/processor/logs/application.log` on the host).

The rolling policy keeps:
- One log file per day
- Max 1 GB per file
- 30 days retention
- Total cap 10 GB
- Older files are gzip compressed

**Key log messages to watch:**

| Message | Meaning |
|---|---|
| `Polling SMB share: smb://...` | Normal poll — no action needed |
| `Downloading '*.zip' (N bytes)` | New file found, download started |
| `Download complete: ...` | Download + remote delete succeeded |
| `Extracting ZIP: ...` | ZIP processing started |
| `CSV done: ... total=N, success=M, quarantine=P` | Processing summary |
| `SFTP upload: ...` | File handed off to SFTP route |
| `Retry: moving '*.csv' from .uploadError back` | Retry of failed upload |
| `SMB access error` | Network/auth problem with SMB share |
| `SFTP upload failed` | Network/auth problem with SFTP server |

---

## 10. Updating to a New Version

```bash
# 1. Pull (or build) the new image
./docker-build.sh                         # build from source
# OR: docker pull myregistry/smb-csv-processor:1.2.3

# 2. Stop and remove the running container (data directories are preserved on the host)
docker stop smb-csv-processor
docker rm   smb-csv-processor

# 3. Start the new version
docker run -d --name smb-csv-processor ... smb-csv-processor:1.2.3

# 4. Verify
docker logs -f smb-csv-processor
```

---

## 11. Directory Reference

```
/data/processor/           (host: /srv/processor/data)
  input/
    zip/                   ZIP files downloaded from SMB (deleted after extraction)
      .error/              ZIPs that failed extraction
    csv/                   Extracted CSVs awaiting processing (deleted after processing)
      .error/              CSVs that caused a processing exception
  output/
    success/               Valid-row output CSVs (picked up by SFTP route)
      .uploadError/        Files that failed SFTP upload (retried automatically)
      .retryFailed/        Files that failed even after retry
    quarantine/            Invalid-row output CSVs (rows rejected by validation)
    archive/               Reserved for manual archival
  test-drop/               (test mode only) drop files here to inject into pipeline

/var/log/smb-csv-processor/ (host: /srv/processor/logs)
  application.log          Current log file
  application.YYYY-MM-DD.log.gz   Rolled and compressed log files

/opt/app/.ssh/             (host: /srv/processor/ssh)
  id_rsa                   SFTP private key (optional, for key-based auth)
  known_hosts              SFTP host fingerprint (optional but recommended)
```

---

## 12. Troubleshooting

### Container exits immediately
Check logs: `docker logs smb-csv-processor`  
Common cause: misconfigured Spring Boot property or missing directory.

### `No enum constant jcifs.DialectVersion.SMB2`
Set `smb.min-smb-version=SMB202` (not `SMB2`).  
Valid values: `SMB202 | SMB210 | SMB300 | SMB302 | SMB311`.

### `SFTP upload failed — Connection refused`
- Verify SFTP host/port: `sftp.host`, `sftp.port=22`
- Test connectivity from inside the container:
  ```bash
  docker exec -it smb-csv-processor sh -c "nc -zv sftp-server.example.com 22"
  ```

### Files accumulating in `.uploadError`
SFTP uploads are failing. The retry route will attempt again every 15 minutes.  
Check SFTP credentials and network. If the issue is persistent, inspect
`.retryFailed/` — files there require manual intervention.

### Output quarantine file has many rows
Review the `QUARANTINE_REASON` column in the quarantine CSV for details.  
Common causes: wrong `generation` value, out-of-range numeric fields,
invalid postcode format.

### `deriveEci` returns null for 5G rows
Ensure the source CSV contains `gnodeb_id` and `cell_id` columns.  
The optional `gnodeb_id_lenth` column (note: source typo preserved) defaults
to 22 bits if absent.
