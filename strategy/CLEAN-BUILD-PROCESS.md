# Container-Based Clean Build Process

## 1. Purpose

The BF system has an agreed legacy clean build process for low-side
components that produces verifiable "clean builds" suitable for low-side
deployment. This document describes how to replicate and formalise that
process using containers, making it reproducible, auditable, and automatable.

> A container-based clean build is *more* rigorous than the original process:
> the build environment is completely isolated, versioned, and can be
> byte-for-byte reproduced at any future date.

---

## 2. Clean Build Principles

| Principle | Description |
|-----------|-------------|
| **Isolation** | No internet access during build. No host filesystem access beyond source. |
| **Reproducibility** | Same inputs → identical binary outputs. No timestamps in artifacts. |
| **Traceability** | Every dependency version is pinned and recorded in the build log. |
| **Minimalism** | Build environment contains only approved tools — nothing else. |
| **Auditability** | Build log signed and retained alongside the artifact. |

---

## 3. Clean Build Container Architecture

```
┌───────────────────────────────────────────────────────────┐
│                  BUILD PREPARATION (online)               │
│                                                           │
│  1. Pull base image (pinned digest)                       │
│  2. Install build tools (pinned versions)                 │
│  3. Pre-fetch dependency cache (from approved mirror)     │
│  4. Seal the cache (make read-only / hash it)             │
│  5. Save as: clean-build-env:<version> image              │
└───────────────────────────────────────────────────────────┘
                          │
                          │ (image saved to local registry)
                          ▼
┌───────────────────────────────────────────────────────────┐
│              CLEAN BUILD EXECUTION (offline)              │
│                                                           │
│  FROM clean-build-env:<version>    (no network)           │
│  COPY source code only                                    │
│  RUN build (--network=none, --offline)                    │
│  → Produces: artifact JAR/DLL/EXE                         │
│  → Produces: build.log + dependency.manifest              │
│  → Signs artifact with build key                          │
└───────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────┐
│              ARTIFACT VERIFICATION                        │
│                                                           │
│  - Verify artifact signature                              │
│  - Compare dependency manifest to approved list           │
│  - Smoke test (unit test stage)                           │
│  - Record: source commit hash + build env image digest    │
└───────────────────────────────────────────────────────────┘
```

---

## 4. Java / Maven Clean Build

### 4.1 Build Environment Image

```dockerfile
# Dockerfile.clean-build-env-java
# =============================================================================
# STEP 1: Build this image ONCE on a networked machine.
# STEP 2: Save it: docker save clean-build-java:1.0 | gzip > clean-build-java.tar.gz
# STEP 3: Transfer to air-gapped build machine and load:
#         docker load < clean-build-java.tar.gz
# =============================================================================

# Pin the exact JDK version matching the target environment
# REPLACE: 8u392-b08 with actual version from CONFIRM step
FROM eclipse-temurin:8u392-b08-jdk-focal AS build-env

# Install Maven — pin exact version matching dev environment
ARG MAVEN_VERSION=3.6.3
ARG MAVEN_SHA512=REPLACE_WITH_ACTUAL_SHA512

RUN curl -fsSL \
    https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    -o /tmp/mvn.tar.gz \
 && echo "${MAVEN_SHA512}  /tmp/mvn.tar.gz" | sha512sum --check \
 && tar -xzf /tmp/mvn.tar.gz -C /opt \
 && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn \
 && rm /tmp/mvn.tar.gz

ENV MAVEN_HOME=/opt/apache-maven-${MAVEN_VERSION}
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Pre-populate the local Maven repository (dependency cache)
# Run this with network access; the result is baked into the image
WORKDIR /prebuild
COPY pom.xml .
RUN mvn dependency:go-offline -B \
    --settings /opt/settings-mirror.xml   # point to approved internal mirror

# Make the .m2 cache read-only so builds cannot fetch new dependencies
RUN chmod -R a-w /root/.m2/repository
```

### 4.2 Clean Build Execution

```bash
#!/usr/bin/env bash
# clean-build-java.sh
# Runs a clean build of a Java/Maven component in an isolated container.
# Usage: bash clean-build-java.sh <source-dir> <component-id>

set -euo pipefail

SOURCE_DIR="$(realpath "${1:?Source dir required}")"
COMPONENT_ID="${2:?Component ID required}"
BUILD_ENV_IMAGE="clean-build-java:1.0"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTPUT_DIR="./clean-builds/${COMPONENT_ID}/${TIMESTAMP}"

mkdir -p "$OUTPUT_DIR"

echo "[CLEAN BUILD] Component: $COMPONENT_ID"
echo "[CLEAN BUILD] Source:    $SOURCE_DIR"
echo "[CLEAN BUILD] Output:    $OUTPUT_DIR"
echo "[CLEAN BUILD] Started:   $(date -u)"

# Run the build with NO network access
docker run --rm \
  --network=none \
  --read-only \
  --tmpfs /tmp \
  --volume "${SOURCE_DIR}:/workspace/source:ro" \
  --volume "${OUTPUT_DIR}:/workspace/output:rw" \
  --env MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository" \
  "${BUILD_ENV_IMAGE}" \
  /bin/bash -c "
    set -euo pipefail
    cd /workspace/source
    echo '=== Build started ===' | tee /workspace/output/build.log
    echo 'Source commit: $(git rev-parse HEAD 2>/dev/null || echo UNKNOWN)' \
         | tee -a /workspace/output/build.log
    mvn package \
      --batch-mode \
      --offline \
      --no-transfer-progress \
      -DskipTests=false \
      | tee -a /workspace/output/build.log
    cp target/*.jar /workspace/output/
    echo '=== Build completed ===' | tee -a /workspace/output/build.log
    # Record dependency manifest
    mvn dependency:list --offline -q -DoutputFile=/workspace/output/dependency.manifest
  "

echo "[CLEAN BUILD] Artifacts:"
ls -la "$OUTPUT_DIR"
echo "[CLEAN BUILD] Complete: $(date -u)"
```

---

## 5. C# / .NET Framework Clean Build

### 5.1 Build Environment Image (Windows container required)

```dockerfile
# Dockerfile.clean-build-env-dotnet
# Windows Server Core with MSBuild toolchain
FROM mcr.microsoft.com/dotnet/framework/sdk:4.8-windowsservercore-ltsc2022
# escape=`

# Install NuGet CLI — pin version
ARG NUGET_VERSION=6.8.0
RUN powershell -NoProfile -Command `
    Invoke-WebRequest -Uri "https://dist.nuget.org/win-x86-commandline/v${NUGET_VERSION}/nuget.exe" `
      -OutFile C:/tools/nuget.exe

# Pre-restore packages into a local cache
WORKDIR C:/prebuild
COPY *.sln .
COPY **/*.csproj ./
RUN C:/tools/nuget.exe restore -PackagesDirectory C:/packages -NonInteractive

# Seal the packages cache
# (In practice: copy packages dir to a read-only layer)
```

### 5.2 Clean Build Script (PowerShell)

```powershell
# clean-build-dotnet.ps1
param(
    [Parameter(Mandatory)] [string]$SourceDir,
    [Parameter(Mandatory)] [string]$ComponentId,
    [string]$BuildEnvImage = "clean-build-dotnet:1.0"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = ".\clean-builds\$ComponentId\$timestamp"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

Write-Host "[CLEAN BUILD] Component: $ComponentId"
Write-Host "[CLEAN BUILD] Source:    $SourceDir"

docker run --rm `
    --isolation=process `
    --volume "${SourceDir}:C:/workspace/source:ro" `
    --volume "${outputDir}:C:/workspace/output:rw" `
    $BuildEnvImage `
    powershell -NoProfile -Command @"
        Set-StrictMode -Version Latest
        cd C:/workspace/source
        msbuild /p:Configuration=Release ``
                /p:OutputPath=C:/workspace/output ``
                /p:RestorePackagesPath=C:/packages ``
                /nodeReuse:false ``
                /t:Build ``
                /v:normal ``
                | Tee-Object -FilePath C:/workspace/output/build.log
"@

Write-Host "[CLEAN BUILD] Complete"
Get-ChildItem $outputDir
```

---

## 6. Build Artefact Record

For each clean build, record and retain:

```json
{
  "build_record": {
    "component_id": "BF-LOW-001",
    "build_timestamp_utc": "2026-04-10T09:00:00Z",
    "build_env_image": "clean-build-java:1.0",
    "build_env_image_digest": "sha256:abc123...",
    "source_repository": "REPO_URL",
    "source_commit_hash": "GIT_SHA",
    "source_branch": "main",
    "artifact_filename": "bf-broker-1.jar",
    "artifact_sha256": "sha256:def456...",
    "dependency_manifest": "dependency.manifest",
    "build_log": "build.log",
    "built_by": "NAME",
    "sign_off": "NAME",
    "notes": ""
  }
}
```

---

## 7. Frequency and Triggers

| Trigger | Action |
|---------|--------|
| Any source code change | Clean build required |
| Any base image update | Clean build required + interface regression test |
| Any dependency version change | Clean build required + security review |
| Scheduled (quarterly) | Clean build as confidence check — even with no changes |
| Before deployment to target | Clean build mandatory |

> Previous BF clean builds: last performed [DATE — CONFIRM WITH ADMS].
> TRS clean builds still occur every 1–3 months using a similar but not
> identical procedure — use TRS process as reference only.
