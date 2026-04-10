# canet — Containerisation for Cloud-Readiness

A toolkit for migrating legacy applications (Java, C#/.NET, Oracle, and mixed stacks)
to container-based deployments.

## What's in this repo

| Directory | Purpose |
|-----------|---------|
| `strategy/` | Architecture decisions, compatibility matrices, migration roadmap |
| `scripts/` | Version-detection and compatibility-check shell scripts |
| `dockerfiles/` | Production-grade Dockerfiles per technology / version |
| `docker-compose/` | Compose stacks for local development and integration testing |
| `versioning/` | JSON lookup tables — supported versions, EOL dates, base images |
| `quickstart/` | Original Maven Java sample (used as a migration reference) |

## Quick start

```bash
# 1. Detect all runtime versions on the current host
bash scripts/detect-versions.sh

# 2. Run a full pre-migration assessment of a project directory
bash scripts/pre-migration-assessment.sh /path/to/your/app

# 3. Check Java compatibility for a specific version
bash scripts/java-compatibility.sh 8

# 4. Check .NET compatibility
bash scripts/dotnet-compatibility.sh 4.8

# 5. Spin up a local Java app + Oracle DB stack
docker-compose -f docker-compose/docker-compose.oracle-stack.yml up
```

## Supported technology tracks

- **Java** — JDK 5 through 21, Maven & Gradle projects
- **.NET / C#** — .NET Framework 2.0–4.8, .NET Core 2.1+, .NET 5/6/7/8
- **Oracle** — Database client connectivity (11g → 23ai)
- **Linux** — RHEL/CentOS, Debian/Ubuntu, Alpine base image guidance

## Technology compatibility overview

See [`versioning/`](versioning/) for full JSON lookup tables and
[`strategy/COMPATIBILITY_MATRIX.md`](strategy/COMPATIBILITY_MATRIX.md) for the
human-readable matrix.
