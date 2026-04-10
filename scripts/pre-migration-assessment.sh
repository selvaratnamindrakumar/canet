#!/usr/bin/env bash
# =============================================================================
# pre-migration-assessment.sh
# Scans a project directory and produces a containerisation readiness report.
# Usage: bash pre-migration-assessment.sh <project-dir>
#        bash pre-migration-assessment.sh /opt/myapp
# =============================================================================

set -euo pipefail

PROJECT_DIR="${1:-.}"

if [[ ! -d "$PROJECT_DIR" ]]; then
    echo "ERROR: Directory not found: $PROJECT_DIR"
    exit 1
fi

PROJECT_DIR=$(realpath "$PROJECT_DIR")

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
BOLD='\033[1m'
pass()  { echo -e "  ${GREEN}✅${NC}  $*"; ((PASS_COUNT++)); }
warn()  { echo -e "  ${YELLOW}⚠️ ${NC}  $*"; ((WARN_COUNT++)); }
fail()  { echo -e "  ${RED}❌${NC}  $*"; ((FAIL_COUNT++)); }
info()  { echo -e "  ${BLUE}ℹ️ ${NC}  $*"; }
section(){ echo -e "\n${BOLD}--- $* ---${NC}"; }

PASS_COUNT=0; WARN_COUNT=0; FAIL_COUNT=0
DETECTED_TECH=()

echo ""
echo "======================================================"
echo -e "${BOLD} Pre-Migration Assessment Report${NC}"
echo " Project: $PROJECT_DIR"
echo " Date:    $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================================"

# ---------- Technology detection ----------------------------------------------
section "Technology Detection"

# Java
if find "$PROJECT_DIR" -maxdepth 5 -name "pom.xml" | grep -q .; then
    DETECTED_TECH+=("java-maven")
    pass "Maven project detected (pom.xml found)"
    POM=$(find "$PROJECT_DIR" -maxdepth 5 -name "pom.xml" | head -1)
    if grep -q '<java.version>' "$POM" 2>/dev/null; then
        JAVA_VER=$(grep -oP '(?<=<java.version>)[^<]+' "$POM" | head -1)
        info "pom.xml declares java.version: $JAVA_VER"
    fi
    if grep -q '<source>' "$POM" 2>/dev/null; then
        SRC_VER=$(grep -oP '(?<=<source>)[^<]+' "$POM" | head -1)
        info "maven-compiler-plugin source: $SRC_VER"
    fi
fi

if find "$PROJECT_DIR" -maxdepth 5 -name "build.gradle" -o -name "build.gradle.kts" | grep -q .; then
    DETECTED_TECH+=("java-gradle")
    pass "Gradle project detected"
fi

if find "$PROJECT_DIR" -maxdepth 5 -name "*.java" | grep -q .; then
    JAVA_COUNT=$(find "$PROJECT_DIR" -maxdepth 10 -name "*.java" | wc -l)
    info "$JAVA_COUNT .java source files found"
fi

# .NET / C#
if find "$PROJECT_DIR" -maxdepth 5 -name "*.csproj" | grep -q .; then
    DETECTED_TECH+=("dotnet")
    pass ".NET/C# project detected (*.csproj found)"
    CSPROJ=$(find "$PROJECT_DIR" -maxdepth 5 -name "*.csproj" | head -1)
    if grep -qi 'net4\|net3\|net2\|netframework' "$CSPROJ" 2>/dev/null; then
        fail ".NET Framework detected in $CSPROJ — Windows container required"
        warn "Target framework requires Windows container or migration to .NET 8+"
    elif grep -qi 'net8\|net9' "$CSPROJ" 2>/dev/null; then
        pass ".NET 8/9 target detected — Linux container ready"
    elif grep -qi 'net6\|net7' "$CSPROJ" 2>/dev/null; then
        warn ".NET 6/7 detected — supported but EOL/approaching EOL; consider .NET 8"
    fi
fi

if find "$PROJECT_DIR" -maxdepth 5 -name "*.vbproj" | grep -q .; then
    DETECTED_TECH+=("vb-dotnet")
    warn "VB.NET project detected — verify .NET version and Linux compatibility"
fi

if find "$PROJECT_DIR" -maxdepth 5 -name "*.fsproj" | grep -q .; then
    DETECTED_TECH+=("fsharp")
    pass "F# project detected — .NET 8 F# supports Linux"
fi

if find "$PROJECT_DIR" -maxdepth 5 -name "packages.config" | grep -q .; then
    warn "packages.config found — old NuGet format; migrate to PackageReference"
fi

if find "$PROJECT_DIR" -maxdepth 5 -name "web.config" | grep -q .; then
    warn "web.config found — IIS-specific config; review for container compatibility"
fi

# Python
if find "$PROJECT_DIR" -maxdepth 3 -name "requirements.txt" -o -name "Pipfile" -o -name "pyproject.toml" | grep -q .; then
    DETECTED_TECH+=("python")
    pass "Python project detected"
fi

# Node.js
if find "$PROJECT_DIR" -maxdepth 3 -name "package.json" | grep -q .; then
    DETECTED_TECH+=("nodejs")
    pass "Node.js project detected (package.json)"
fi

if [[ ${#DETECTED_TECH[@]} -eq 0 ]]; then
    warn "No recognised build files found — manual technology identification required"
fi

# ---------- Containerisation blockers -----------------------------------------
section "Containerisation Blockers"

# Windows-specific APIs
WIN_PATTERNS=("System.Windows.Forms" "System.Drawing" "Microsoft.Win32.Registry"
              "System.Messaging" "System.ServiceProcess" "System.DirectoryServices"
              "System.Runtime.InteropServices.ComImport")
WIN_FOUND=false
for pat in "${WIN_PATTERNS[@]}"; do
    if grep -rq "$pat" "$PROJECT_DIR" --include="*.cs" --include="*.vb" 2>/dev/null; then
        fail "Windows-only API found: $pat"
        WIN_FOUND=true
    fi
done
$WIN_FOUND || pass "No Windows-only API patterns detected in source code"

# Absolute Windows paths
if grep -rq 'C:\\' "$PROJECT_DIR" --include="*.cs" --include="*.java" --include="*.xml" --include="*.config" 2>/dev/null; then
    fail "Hardcoded Windows paths (C:\\) found — externalise to config/env vars"
fi

# Registry usage
if grep -rq 'Registry\.' "$PROJECT_DIR" --include="*.cs" 2>/dev/null; then
    fail "Windows Registry access detected — must be replaced for Linux containers"
fi

# WCF service references
if grep -rq 'ServiceModel\|WCF\|basicHttpBinding\|netTcpBinding' \
   "$PROJECT_DIR" --include="*.cs" --include="*.config" --include="*.xml" 2>/dev/null; then
    warn "WCF detected — use CoreWCF (partial support) or migrate to gRPC/REST"
fi

# MSMQ
if grep -rq 'System.Messaging\|MessageQueue' "$PROJECT_DIR" --include="*.cs" 2>/dev/null; then
    fail "MSMQ detected — replace with RabbitMQ, Azure Service Bus, or Amazon SQS"
fi

# Local file system as state
for pattern in "new FileStream" "File.WriteAllText" "File.AppendAllText" "new StreamWriter"; do
    if grep -rq "$pattern" "$PROJECT_DIR" --include="*.cs" --include="*.java" 2>/dev/null; then
        warn "Local file writes detected: '$pattern' — consider object storage for cloud"
    fi
done

# ---------- Configuration & Secrets ------------------------------------------
section "Configuration & Secrets"

# Hardcoded connection strings
if grep -rqi 'Data Source=\|Server=.*Database=\|ConnectionString' \
   "$PROJECT_DIR" --include="*.cs" --include="*.java" --include="*.xml" \
   --include="*.config" --include="*.properties" 2>/dev/null; then
    warn "Possible connection strings in code/config — move to environment variables or vault"
fi

# Hardcoded credentials
CRED_PATTERNS=("password=" "passwd=" "pwd=" "apikey=" "api_key=" "secret=" "token=")
for cpat in "${CRED_PATTERNS[@]}"; do
    if grep -rqi "$cpat" "$PROJECT_DIR" \
       --include="*.cs" --include="*.java" --include="*.xml" \
       --include="*.properties" --include="*.config" --include="*.json" \
       --include="*.yml" --include="*.yaml" 2>/dev/null | grep -v "//\|#\|placeholder\|example\|sample" | grep -q .; then
        fail "Possible hardcoded credentials matching '$cpat' — must be externalized"
    fi
done

# .env files
if find "$PROJECT_DIR" -maxdepth 3 -name ".env" | grep -q .; then
    warn ".env file found — ensure it is in .dockerignore and NOT copied into image"
fi

# ---------- Logging -----------------------------------------------------------
section "Logging (12-Factor)"

LOG4J_CONF=$(find "$PROJECT_DIR" -maxdepth 5 \
    -name "log4j*.properties" -o -name "log4j*.xml" \
    -o -name "logback*.xml" -o -name "log4net*.config" 2>/dev/null | head -5)
if [[ -n "$LOG4J_CONF" ]]; then
    warn "File-based logging config found: $(echo "$LOG4J_CONF" | tr '\n' ' ')"
    info "In containers, redirect all output to stdout/stderr (console appender)"
else
    pass "No file-based logging configuration detected"
fi

# ---------- Existing containerisation artefacts --------------------------------
section "Existing Container Artefacts"

if [[ -f "$PROJECT_DIR/Dockerfile" ]]; then
    pass "Dockerfile already present"
    # Check for root user
    if ! grep -q '^USER ' "$PROJECT_DIR/Dockerfile"; then
        warn "Dockerfile has no USER directive — container will run as root"
    fi
    if grep -q 'ADD http' "$PROJECT_DIR/Dockerfile"; then
        warn "Dockerfile uses ADD with URL — prefer RUN curl/wget for better layer caching"
    fi
fi

if [[ -f "$PROJECT_DIR/docker-compose.yml" ]] || [[ -f "$PROJECT_DIR/docker-compose.yaml" ]]; then
    pass "docker-compose.yml already present"
fi

if [[ -f "$PROJECT_DIR/.dockerignore" ]]; then
    pass ".dockerignore present — check it excludes .git, node_modules, build artifacts"
else
    warn ".dockerignore not found — create one to reduce image size and avoid secret leaks"
fi

# ---------- Summary -----------------------------------------------------------
echo ""
echo "======================================================"
echo -e "${BOLD} Assessment Summary${NC}"
echo "======================================================"
echo -e "  ${GREEN}✅ Pass:${NC}  $PASS_COUNT"
echo -e "  ${YELLOW}⚠️  Warn:${NC}  $WARN_COUNT"
echo -e "  ${RED}❌ Fail:${NC}  $FAIL_COUNT"
echo ""
echo -n " Technologies: "
printf '%s  ' "${DETECTED_TECH[@]:-unknown}"
echo ""

TOTAL=$((PASS_COUNT + WARN_COUNT + FAIL_COUNT))
if [[ "$FAIL_COUNT" -eq 0 && "$WARN_COUNT" -le 3 ]]; then
    echo -e " ${GREEN}VERDICT: Green — ready to containerise${NC}"
elif [[ "$FAIL_COUNT" -eq 0 ]]; then
    echo -e " ${YELLOW}VERDICT: Yellow — containerisable with minor remediation${NC}"
elif [[ "$FAIL_COUNT" -le 3 ]]; then
    echo -e " ${YELLOW}VERDICT: Orange — blockers found; remediate before containerising${NC}"
else
    echo -e " ${RED}VERDICT: Red — significant blockers; plan a migration sprint first${NC}"
fi
echo ""
echo " Next steps:"
echo "   1. Address all ❌ failures before writing Dockerfiles"
echo "   2. Review ⚠️  warnings and plan remediation"
echo "   3. Run: bash scripts/detect-versions.sh  (check host versions)"
echo "   4. Choose Dockerfile from: dockerfiles/<technology>/"
echo "======================================================"
