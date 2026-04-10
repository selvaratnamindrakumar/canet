# Legacy Windows Baseline Strategy

## 1. Current Baseline Reality

| Item | Current state | Implication |
|------|--------------|-------------|
| Host OS | Windows Server 2008 R2 | EOL Jan 2020. No security patches. |
| Dev workstation | Windows 7 VM | EOL Jan 2020. No security patches. |
| IIS | 7.5 | Max on WS2008 R2. No HTTP/2, limited TLS 1.2 config. |
| .NET Framework | ≤ 4.8 (likely 4.0–4.6 range) | Linux container requires migration. |
| Java | Likely 6, 7, or 8 | JDK ≤ 7 EOL. JDK 8 needs version pinning. |
| Apache Tomcat | Likely 6.x, 7.x, or 8.x | EOL versions in use — see §3. |
| TLS | Likely TLS 1.0/1.1 | Both deprecated. Check high-side compatibility. |

> **Key principle:** Containerisation does not require — and in this context
> should NOT trigger — a platform version upgrade. The container replicates
> the runtime environment the application was designed for.
> Upgrades are a separate, subsequent activity.

---

## 2. Migration Path Overview

```
Phase 0 — NOW (Before Containers)
  ├── Inventory all 53 components (inventory/app-inventory.json)
  ├── Confirm low-side active/retired status with ADMS on-site
  ├── Capture interface contracts at the boundary (Wireshark baselines)
  └── Identify version freeze requirements per component

Phase 1 — Lift & Containerise (No Code Changes)
  ├── Replicate WS2008 R2 runtime INSIDE the container
  ├── Use frozen, pinned versions of all dependencies
  ├── Validate identical wire behaviour against captured baseline
  └── Perform clean build using container-based build pipeline

Phase 2 — OS Modernisation (Optional, Later)
  ├── Move base image from WS2008 R2 equivalent to Windows Server 2022
  │   (for C# .NET Framework apps requiring Windows containers)
  │   OR
  ├── Move to Linux container (for Java apps / migrated .NET)
  ├── Requires regression testing against all interface contracts
  └── Requires explicit sign-off from ADMS

Phase 3 — Platform Upgrade (Out of current scope)
  ├── Java 6/7/8 → Java 11/17
  ├── .NET Fx 4.x → .NET 8
  └── Full compatibility testing programme
```

---

## 3. Technology-Specific Windows Legacy Guidance

### 3.1 Apache Tomcat (Java web services / brokers)

| Tomcat | Java compat | Status | WS2008 R2 capable | Recommended base image |
|--------|------------|--------|-------------------|------------------------|
| 6.0.x | Java 5–6 | EOL 2016 | Yes | `eclipse-temurin:6-jdk` (Azul) + manual Tomcat 6 |
| 7.0.x | Java 6–7 | EOL 2021 | Yes | `azul/zulu-openjdk-debian:7` + Tomcat 7 |
| 8.5.x | Java 7–8 | EOL 2024 | Yes | `eclipse-temurin:8-jre-focal` + Tomcat 8.5 |
| 9.0.x | Java 8+ | Active | Yes | `eclipse-temurin:11-jre-alpine` + Tomcat 9 |

**Version freeze approach for Tomcat:**

```dockerfile
# Pin the exact Tomcat version from your target environment
ARG TOMCAT_VERSION=8.5.100
# Verify SHA512 checksum before installing
RUN curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-8/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz \
      -o /tmp/tomcat.tar.gz \
 && echo "EXPECTED_SHA512  /tmp/tomcat.tar.gz" | sha512sum --check \
 && tar -xzf /tmp/tomcat.tar.gz -C /opt \
 && mv /opt/apache-tomcat-${TOMCAT_VERSION} /opt/tomcat \
 && rm /tmp/tomcat.tar.gz
```

### 3.2 IIS 7.5 (.NET Framework web services on Windows)

IIS 7.5 does not exist as a Linux image. Two options:

**Option A — Windows Server Core container (version freeze)**

```dockerfile
# Windows Server Core 2022 is the minimum available Windows container
# IIS is included and can host .NET Framework 4.x
FROM mcr.microsoft.com/windows/servercore/iis:windowsservercore-ltsc2022

# Install exact .NET Framework version
RUN powershell -NoProfile -Command \
    Add-WindowsFeature NET-Framework-45-ASPNET; \
    Add-WindowsFeature Web-Asp-Net45

COPY ./publish C:/inetpub/wwwroot/myapp
```

> Note: WS2008 R2 and WS2022 Server Core behave differently.
> Test all .NET Framework P/Invoke, reflection, and serialisation behaviour.

**Option B — Migrate to .NET 8 on Linux (requires code changes)**

Only if the application has no COM/MSMQ/Registry dependencies.
See `dockerfiles/dotnet/Dockerfile.dotnet-migration`.

### 3.3 Windows Service Wrappers

Common wrappers used on WS2008 R2:

| Wrapper | Description | Container equivalent |
|---------|-------------|---------------------|
| NSSM | Non-Sucking Service Manager | Use Docker `CMD` / `ENTRYPOINT` |
| YAJSW | Yet Another Java Service Wrapper | Replace with JVM `ENTRYPOINT` directly |
| Tanuki JSW | Java Service Wrapper | Replace with JVM `ENTRYPOINT` directly |
| sc.exe | Windows built-in | Use Docker `CMD` |
| WinSW | Windows Service Wrapper | Replace with Docker `ENTRYPOINT` |

**In containers, there is no Windows Service Manager.**
The process started by `ENTRYPOINT` IS the service.
It must run in the **foreground** (not daemonise itself).

```dockerfile
# If the app was wrapped with NSSM/YAJSW to run as a background service,
# ensure the container starts the JVM/app directly in the foreground:

# BAD — process daemonises and container exits immediately:
# CMD ["start-service.bat"]

# GOOD — JVM stays in foreground:
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3.4 C++ Components

Legacy C++ on WS2008 R2 typically compiled with Visual Studio 2008–2013,
using MSVCR90.dll / MSVCR110.dll C runtime.

**Option A — Windows container with matching VC++ Redistributable**

```dockerfile
FROM mcr.microsoft.com/windows/servercore:ltsc2022

# Install matching Visual C++ Redistributable
ADD https://download.microsoft.com/download/.../vcredist_x64.exe C:/vcredist.exe
RUN C:/vcredist.exe /quiet /norestart && del C:/vcredist.exe

COPY ./app C:/app
CMD ["C:/app/myservice.exe"]
```

**Option B — Cross-compile for Linux using MinGW (if no Windows APIs used)**

Only viable if the C++ code uses standard POSIX APIs with no Win32 dependencies.
Requires thorough testing against the high-side interface contracts.

---

## 4. TLS / SSL Considerations

WS2008 R2 defaults to TLS 1.0 and SSL 3.0. High-side components may only
accept these deprecated versions. The container must match:

```dockerfile
# For Java — allow legacy TLS (only if high-side requires it)
ENV JAVA_OPTS="-Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2 \
               -Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2"

# Also may need to modify java.security in JDK 8:
RUN sed -i 's/TLSv1, TLSv1.1,//' /opt/java/openjdk/jre/lib/security/java.security
```

> **Security note:** Only re-enable deprecated TLS versions when required to
> maintain compatibility with high-side. Document the justification.
> Plan to move high-side to TLS 1.2+ as a separate programme of work.

---

## 5. Character Encoding & Locale

Legacy applications on WS2008 R2 often rely on Windows codepages
(e.g. CP1252, CP850) rather than UTF-8. Containers default to UTF-8.

```dockerfile
# For Java — set file encoding to match the original environment
ENV JAVA_OPTS="-Dfile.encoding=Cp1252 \
               -Dsun.jnu.encoding=Cp1252"

# For .NET on Linux — install locale packages
RUN apt-get install -y --no-install-recommends locales \
 && locale-gen en_GB.UTF-8 \
 && update-locale LANG=en_GB.UTF-8
ENV LANG=en_GB.UTF-8
```

---

## 6. Time Zone

WS2008 R2 used Windows time zone IDs (e.g. `GMT Standard Time`).
Linux containers use IANA IDs (e.g. `Europe/London`).

```dockerfile
# Set timezone in container (Java and .NET both respect this)
ENV TZ=Europe/London
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
```

---

## 7. Shared File System / UNC Paths

Legacy BF components may read/write to UNC paths (\\server\share).
Containers cannot natively mount Windows UNC shares on Linux.

**Options:**
1. Replace UNC paths with object storage (S3 / Azure Blob) — requires code change
2. Mount NFS-exported share into Linux container
3. Use Windows container with SMB volume plugin for Kubernetes (`flexvolume-smb`)
4. Keep file exchange via the existing mechanism with a sidecar container

**Identify all file system paths per component during triage.**
