# Containerisation Strategy for Legacy Applications

## 1. Goals

| Goal | Description |
|------|-------------|
| Cloud-readiness | Applications deployable to any OCI-compliant runtime (Docker, Kubernetes, ECS, AKS, GKE) |
| Reproducibility | "Works on my machine" eliminated; image is the artifact |
| Incremental migration | Move workloads one component at a time, not big-bang |
| Observability | Logging, metrics, and health-checks built into every image |
| Security | No root users, minimal base images, secrets via env / vault |

---

## 2. Migration Phases

### Phase 0 — Discovery & Assessment
- Inventory all applications (language, runtime version, OS, dependencies)
- Run `scripts/detect-versions.sh` and `scripts/pre-migration-assessment.sh`
- Classify each app by containerisation difficulty (see §4)
- Identify shared infrastructure: databases, message queues, file shares

### Phase 1 — Lift & Containerise (no code changes)
- Write a `Dockerfile` that replicates the existing runtime environment exactly
- Mount config files from volumes; externalise secrets
- Validate functional parity against the bare-metal baseline
- Target: container runs the app; behaviour is identical

### Phase 2 — Cloud-Optimise
- Replace file-based logging with stdout/stderr (12-factor)
- Add `/health` and `/ready` HTTP endpoints
- Externalise all configuration to environment variables
- Replace local file storage with object storage (S3 / Azure Blob / GCS)
- Right-size the image (multi-stage builds, slim/alpine bases)

### Phase 3 — Orchestrate
- Write Kubernetes manifests (Deployment, Service, ConfigMap, Secret, HPA)
- Set resource requests/limits
- Configure liveness / readiness probes
- Add horizontal pod autoscaling based on CPU/memory
- Integrate with CI/CD pipeline

### Phase 4 — Decommission Legacy
- Run container and legacy side-by-side (traffic splitting / feature flags)
- Validate metrics, error rates, latency
- Migrate data stores if needed
- Decommission legacy hosts

---

## 3. Technology-Specific Strategies

### 3.1 Java (JDK 5–21)

**Key challenges:**
- Very old versions (JDK 5, 6, 7) are EOL and lack Docker-official images
- JVM heap defaults are not container-aware before JDK 8u191 / JDK 10
- Class-path applications vs. fat-jar vs. modular (JPMS)

**Strategy:**
1. For JDK ≤ 7: use `eclipse-temurin` or `amazoncorretto` custom builds on `ubuntu:20.04`
2. For JDK 8: use `eclipse-temurin:8-jre-focal`; add `-XX:+UseContainerSupport`
3. For JDK 11+: use `eclipse-temurin:11-jre-alpine` or `17-jre-alpine`
4. Fat-jar preferred; avoid classpath scanning at startup
5. Use `jlink` for JDK 17+ to create minimal custom JRE

**JVM flags for containers:**
```
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:InitialRAMPercentage=50.0
-Djava.security.egd=file:/dev/./urandom
```

### 3.2 .NET / C# (Framework 2.0–4.8 and .NET 5–8)

**Key challenges:**
- .NET Framework requires **Windows containers** (no Linux support)
- .NET Framework 4.x → .NET 6/7/8 migration requires code changes
- COM interop, WCF, Windows Registry usage blocks Linux migration

**Strategy:**
- **Tier A — No code changes possible**: Use Windows Server Core containers
  (`mcr.microsoft.com/dotnet/framework/runtime:4.8-windowsservercore-ltsc2022`)
- **Tier B — Partial refactor feasible**: Target .NET 6 LTS on Linux via migration tooling
- **Tier C — Full rewrite**: Migrate to ASP.NET Core; target `mcr.microsoft.com/dotnet/aspnet:8.0-alpine`

**Migration path:**
```
.NET Framework 4.x
      ↓  (Upgrade Assistant)
.NET 6 (LTS) on Linux
      ↓
.NET 8 (Current LTS)
```

### 3.3 Oracle Database Connectivity

**Key challenges:**
- Oracle Instant Client must match server major version (or be one major ahead)
- ODP.NET (unmanaged) requires native libraries; use managed ODP.NET where possible
- JDBC thin driver is pure Java — preferred for containers
- TNS_ADMIN, wallet files, and `tnsnames.ora` must be injected at runtime

**Strategy:**
1. Use JDBC thin driver for Java apps (no native libs needed)
2. For .NET: use Oracle.ManagedDataAccess (NuGet) — pure managed, Linux-compatible
3. Mount `tnsnames.ora` and wallet via Kubernetes Secret / ConfigMap
4. Oracle Instant Client in image only when OCI / SQL*Plus tooling is required
5. Connection pool sizing: set `connection_pool_min=2`, `connection_pool_max=20`

### 3.4 Linux / OS

**Recommended base image hierarchy:**

| Use case | Base image | Rationale |
|----------|-----------|-----------|
| Production Java | `eclipse-temurin:17-jre-alpine` | Minimal, well-maintained |
| Production .NET | `mcr.microsoft.com/dotnet/aspnet:8.0-alpine` | Official MS, minimal |
| Oracle client | `oraclelinux:8-slim` | Best Oracle compatibility |
| Legacy deps | `ubuntu:22.04` | Widest package compatibility |
| CI tooling | `ubuntu:22.04` | Broadest tool support |
| .NET Framework | `mcr.microsoft.com/dotnet/framework/runtime:4.8` | Windows only |

---

## 4. Application Classification Matrix

| Tier | Description | Container base | Effort |
|------|-------------|---------------|--------|
| **Green** | Stateless, JVM/CLR, no native deps | Alpine / slim | Low |
| **Yellow** | Stateful OR native deps OR old TLS | Ubuntu 22.04 | Medium |
| **Orange** | Windows-only (.NET Fw, COM, MSMQ) | Windows Server Core | High |
| **Red** | Hardware-bound, OS kernel modules | VM + sidecar pattern | Very High |

---

## 5. Security Baseline

Every container must satisfy:

```dockerfile
# Non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

# Read-only filesystem (set in k8s securityContext)
# readOnlyRootFilesystem: true

# No new privileges
# allowPrivilegeEscalation: false

# Drop all capabilities
# capabilities: { drop: [ALL] }
```

- Secrets via environment variables injected by Kubernetes Secrets or Vault
- No secrets in image layers (`docker history` check in CI)
- Image scanning (Trivy / Snyk) as CI gate
- Base image pinned to digest, not just tag

---

## 6. CI/CD Integration Points

```
Git push
  → lint Dockerfile (hadolint)
  → build image (docker buildx)
  → scan image (trivy)
  → run unit tests inside container
  → push to registry (tag: git-sha + semver)
  → deploy to staging (kubectl apply)
  → smoke tests
  → promote to production (tag: stable)
```

---

## 7. Decision Tree — Which Dockerfile to use?

```
Is it Java?
  Yes → What JDK version?
          ≤ 7  → dockerfiles/java/Dockerfile.java7-legacy
          8    → dockerfiles/java/Dockerfile.java8
          11   → dockerfiles/java/Dockerfile.java11
          17   → dockerfiles/java/Dockerfile.java17
          21   → dockerfiles/java/Dockerfile.java21

Is it .NET?
  Yes → Is it .NET Framework (≤ 4.8)?
          Yes → Windows container → dockerfiles/dotnet/Dockerfile.netfx48
          No  → Target .NET 6/8 → dockerfiles/dotnet/Dockerfile.dotnet8

Needs Oracle client?
  Yes → Add oracle client layer → dockerfiles/oracle/Dockerfile.oracle-client
      OR use java+oracle combo → dockerfiles/multi-tech/Dockerfile.java-oracle
```
