# Technology Compatibility Matrix

> Last updated: 2026-04  
> Legend: ✅ Supported  ⚠️ Workaround needed  ❌ Not supported  🔴 EOL

---

## Java Versions

| JDK | Release | EOL | Linux container | Alpine | Docker official image | Container-aware JVM |
|-----|---------|-----|----------------|--------|----------------------|---------------------|
| 5 | 2004 | 2009 | ⚠️ manual build | ❌ | ❌ | ❌ |
| 6 | 2006 | 2013 | ⚠️ manual build | ❌ | ❌ | ❌ |
| 7 | 2011 | 2022 | ⚠️ zulu/corretto | ❌ | ❌ | ❌ |
| 8u191+ | 2014 | 2030 (LTS) | ✅ | ⚠️ | ✅ eclipse-temurin | ✅ |
| 11 | 2018 | 2027 (LTS) | ✅ | ✅ | ✅ eclipse-temurin | ✅ |
| 17 | 2021 | 2029 (LTS) | ✅ | ✅ | ✅ eclipse-temurin | ✅ |
| 21 | 2023 | 2031 (LTS) | ✅ | ✅ | ✅ eclipse-temurin | ✅ |

**Recommended migration path:** JDK 8 → 11 → 17 (LTS to LTS)

---

## .NET / C# Versions

| Version | Framework | EOL | Linux container | Windows container | Notes |
|---------|-----------|-----|----------------|-------------------|-------|
| 2.0 | .NET Framework | 2011 | ❌ | 🔴 | COM-only era |
| 3.5 | .NET Framework | 2029 | ❌ | ✅ servercore | Bundled with Windows |
| 4.0 | .NET Framework | 2016 | ❌ | 🔴 | Upgrade to 4.8 |
| 4.5 | .NET Framework | 2016 | ❌ | 🔴 | Upgrade to 4.8 |
| 4.6 | .NET Framework | 2022 | ❌ | ⚠️ | Upgrade to 4.8 |
| 4.7 | .NET Framework | 2027 | ❌ | ✅ servercore | |
| 4.8 | .NET Framework | 2029 | ❌ | ✅ servercore | Last .NET Framework |
| Core 2.1 | .NET Core | 2021 | 🔴 | 🔴 | |
| Core 3.1 | .NET Core | 2022 | 🔴 | 🔴 | |
| 5.0 | .NET 5 | 2022 | 🔴 | 🔴 | |
| 6.0 | .NET 6 | 2024 | 🔴 | ✅ | LTS — migrate to 8 |
| 7.0 | .NET 7 | 2024 | 🔴 | ✅ | |
| 8.0 | .NET 8 | 2026-11 | ✅ | ✅ | **Current LTS** |
| 9.0 | .NET 9 | 2026-05 | ✅ | ✅ | STS |

**Recommended migration path:** .NET Fx 4.x → .NET 8 LTS

---

## Oracle Database Connectivity

| Oracle DB | JDBC thin jar | ODP.NET managed | Instant Client | Notes |
|-----------|--------------|----------------|----------------|-------|
| 11g R2 | ojdbc6.jar | 12.1+ | 11.2 | EOL 2013 |
| 12c R1 | ojdbc7.jar | 12.1+ | 12.1 | EOL 2022 |
| 12c R2 | ojdbc8.jar | 12.2+ | 12.2 | EOL 2022 |
| 18c | ojdbc8.jar | 18+ | 18.5 | EOL 2021 |
| 19c | ojdbc8.jar / ojdbc10 | 19+ | 19.x | LTS — 2027 |
| 21c | ojdbc11.jar | 21+ | 21.x | Innovation — 2024 |
| 23ai | ojdbc11.jar | 23+ | 23.x | Current LTS |

**JDBC interoperability rule:** Client version >= Server version − 1 major release

---

## Linux Base Images for Containers

| OS | Image | Size | Best for |
|----|-------|------|----------|
| Alpine 3.19 | `alpine:3.19` | ~7 MB | Minimal tooling, Go, static binaries |
| Alpine + JRE | `eclipse-temurin:17-jre-alpine` | ~180 MB | Java production |
| Debian Slim | `debian:12-slim` | ~75 MB | Wide package support, less glibc issues |
| Ubuntu 22.04 | `ubuntu:22.04` | ~77 MB | Best compat for legacy apt packages |
| Oracle Linux 8 | `oraclelinux:8-slim` | ~110 MB | Oracle DB client, RHEL-compatible |
| UBI 9 Minimal | `registry.access.redhat.com/ubi9-minimal` | ~105 MB | OpenShift, RHEL-certified |
| Windows Server Core | `mcr.microsoft.com/.../windowsservercore-ltsc2022` | ~4 GB | .NET Framework 4.x |
| Nano Server | `mcr.microsoft.com/windows/nanoserver:ltsc2022` | ~280 MB | .NET 8 on Windows |

---

## JDK Distribution Comparison

| Distribution | Vendor | LTS Policy | Alpine | ARM64 | Commercial support |
|-------------|--------|-----------|--------|-------|--------------------|
| Eclipse Temurin | Adoptium | Yes | ✅ | ✅ | Via vendors |
| Amazon Corretto | AWS | Yes | ✅ | ✅ | AWS customers |
| Microsoft OpenJDK | Microsoft | Yes | ✅ | ✅ | Azure customers |
| Azul Zulu | Azul | Yes | ✅ | ✅ | Paid |
| GraalVM CE | Oracle | LTS only | ✅ | ✅ | Oracle support |
| Oracle JDK | Oracle | Yes | ❌ | ✅ | NFTC / paid |
| IBM Semeru | IBM | Yes | ✅ | ✅ | IBM customers |

---

## Maven / Gradle vs Java Compatibility

| Maven | Min Java | Max Java tested | Notes |
|-------|---------|----------------|-------|
| 3.2.x | 6 | 8 | EOL |
| 3.6.x | 7 | 14 | Widely deployed |
| 3.8.x | 7 | 17 | Recommended for Java 11/17 |
| 3.9.x | 8 | 21+ | Current stable |

| Gradle | Min Java | Max Java tested | Notes |
|--------|---------|----------------|-------|
| 6.x | 8 | 15 | EOL |
| 7.x | 8 | 19 | Maintenance |
| 8.x | 8 | 21+ | Current stable |

---

## .NET Framework → .NET 8 Feature Compatibility

| Feature | .NET Framework | .NET 8 Linux | Action |
|---------|---------------|-------------|--------|
| ASP.NET WebForms | ✅ | ❌ | Rewrite to Razor Pages / Blazor |
| WCF Server | ✅ | ⚠️ CoreWCF | Use CoreWCF or gRPC |
| WCF Client | ✅ | ✅ | System.ServiceModel.Http |
| ASMX Web Services | ✅ | ❌ | Rewrite to Web API |
| Windows Registry | ✅ | ❌ | Externalise to config / env |
| Windows Auth (NTLM/Kerberos) | ✅ | ⚠️ | Negotiate auth via IIS proxy |
| COM Interop | ✅ | ❌ | Isolate to Windows sidecar |
| MSMQ | ✅ | ❌ | Replace with RabbitMQ / Azure SB |
| ADO.NET (SQL Server) | ✅ | ✅ | Full support |
| ADO.NET (Oracle managed) | ✅ | ✅ | Oracle.ManagedDataAccess.Core |
| Entity Framework 6 | ✅ | ⚠️ | Migrate to EF Core |
| Entity Framework Core | ✅ | ✅ | Full support |
| SignalR (classic) | ✅ | ❌ | Use ASP.NET Core SignalR |
| NuGet packages | ✅ | ⚠️ | Check .NET Standard / .NET 8 compat |

---

## Container Resource Recommendations

| App type | Min CPU | Recommended RAM | JVM heap / CLR |
|----------|---------|-----------------|----------------|
| Java microservice | 0.25 | 512 Mi | 256–384 Mi |
| Java monolith | 1.0 | 2 Gi | 1.5 Gi |
| .NET 8 API | 0.25 | 256 Mi | Managed |
| .NET Fx WebApp (Windows) | 0.5 | 1 Gi | Managed |
| Oracle JDBC app | 0.5 | 1 Gi | Pool overhead |
| Spring Boot | 0.5 | 1 Gi | 768 Mi |
