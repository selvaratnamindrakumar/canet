#!/usr/bin/env bash
# =============================================================================
# assess-broker.sh
# Analyses a BF broker/marshaller source directory and produces a
# containerisation readiness report specific to the high/low-side architecture.
#
# Usage: bash assess-broker.sh <source-dir> [component-id]
#        bash assess-broker.sh /opt/bf-source/broker-001 BF-LOW-001
# =============================================================================

set -euo pipefail

SOURCE_DIR="${1:?Usage: $0 <source-dir> [component-id]}"
COMPONENT_ID="${2:-UNKNOWN}"
SOURCE_DIR=$(realpath "$SOURCE_DIR")

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
pass()    { echo -e "  ${GREEN}✅${NC}  $*"; ((PASS_COUNT++)); }
warn()    { echo -e "  ${YELLOW}⚠️ ${NC}  $*"; ((WARN_COUNT++)); }
fail()    { echo -e "  ${RED}❌${NC}  $*"; ((FAIL_COUNT++)); }
info()    { echo -e "  ${BLUE}ℹ️ ${NC}  $*"; }
section() { echo -e "\n${BOLD}--- $* ---${NC}"; }
PASS_COUNT=0; WARN_COUNT=0; FAIL_COUNT=0

echo ""
echo "======================================================"
echo -e "${BOLD} BF Broker/Marshaller Assessment: $COMPONENT_ID${NC}"
echo " Source: $SOURCE_DIR"
echo " Date:   $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================================"

# ---------- Technology detection ---------------------------------------------
section "Technology Detection"
TECH="unknown"
RUNTIME="unknown"

if find "$SOURCE_DIR" -maxdepth 4 -name "pom.xml" | grep -q .; then
    TECH="java-maven"; RUNTIME="standalone-or-tomcat"
    pass "Java/Maven project (pom.xml)"
    POM=$(find "$SOURCE_DIR" -maxdepth 4 -name "pom.xml" | head -1)
    if grep -q '<packaging>war</packaging>' "$POM" 2>/dev/null; then
        RUNTIME="tomcat-war"; info "Packaging: WAR → Tomcat deployment"
    elif grep -q '<packaging>jar</packaging>' "$POM" 2>/dev/null || ! grep -q '<packaging>' "$POM"; then
        RUNTIME="standalone-jar"; info "Packaging: JAR → standalone (service-wrapped)"
    fi
fi

if find "$SOURCE_DIR" -maxdepth 4 -name "*.csproj" | grep -q .; then
    TECH="csharp"
    CSPROJ=$(find "$SOURCE_DIR" -maxdepth 4 -name "*.csproj" | head -1)
    if grep -qi 'Web\|WebApplication\|WebService' "$CSPROJ" 2>/dev/null; then
        RUNTIME="iis"; pass "C# web project → IIS deployment"
    else
        RUNTIME="windows-service"; pass "C# project → likely Windows Service"
    fi
fi

if find "$SOURCE_DIR" -maxdepth 4 -name "*.vcxproj" -o -name "*.vcproj" | grep -q .; then
    TECH="cpp"; RUNTIME="windows-service"; pass "C++ project detected"
fi

# ---------- Service Wrapper Detection ----------------------------------------
section "Service Wrapper Detection"
WRAPPER_FOUND=false

# YAJSW
if find "$SOURCE_DIR" -maxdepth 6 -name "wrapper.conf" | grep -q .; then
    CONF=$(find "$SOURCE_DIR" -maxdepth 6 -name "wrapper.conf" | head -1)
    warn "YAJSW/JSW wrapper.conf found: $CONF"
    WRAPPER_FOUND=true
    info "Translate JVM args from wrapper.conf to Dockerfile ENTRYPOINT"
    info "Key fields to extract: wrapper.java.additional.*, wrapper.jvm.ms, wrapper.jvm.mx"
    if grep -q 'wrapper.java.additional\|wrapper.jvm' "$CONF" 2>/dev/null; then
        info "JVM args found in wrapper.conf:"
        grep -E 'wrapper\.(java\.additional|jvm\.(ms|mx))' "$CONF" | head -20 | while read -r line; do
            info "  $line"
        done
    fi
fi

# NSSM
if find "$SOURCE_DIR" -maxdepth 6 -name "nssm.exe" -o -name "install-service.bat" | grep -q .; then
    warn "NSSM service wrapper artefacts found"
    WRAPPER_FOUND=true
    info "Extract AppParameters, AppDirectory, AppEnvironmentExtra from NSSM config"
fi

# Windows Service installer
if find "$SOURCE_DIR" -maxdepth 4 -name "*.cs" | xargs grep -l "ServiceBase\|ServiceInstaller" 2>/dev/null | grep -q .; then
    warn "Windows Service code pattern detected (ServiceBase/ServiceInstaller)"
    WRAPPER_FOUND=true
    info "Adapt Main() to support foreground/console mode for container"
fi

$WRAPPER_FOUND || pass "No service wrapper artefacts detected"

# ---------- Interface Contract Analysis ---------------------------------------
section "Interface Contract Analysis (Critical)"

# Listen ports
LISTEN_PORTS=()
for pattern in "server.port" "connector port" "listen.port" "bind.port" \
               "Endpoint\|BasicHttpBinding\|WebServiceHost" \
               "ServerSocket\|HttpServer\|new.*Server"; do
    FOUND=$(grep -rn "$pattern" "$SOURCE_DIR" \
        --include="*.java" --include="*.cs" --include="*.xml" \
        --include="*.properties" --include="*.config" \
        2>/dev/null | head -5)
    if [[ -n "$FOUND" ]]; then
        info "Possible port/listener config: $FOUND"
    fi
done

# Upstream connections
if grep -rqn "http[s]\?://" "$SOURCE_DIR" \
   --include="*.java" --include="*.cs" \
   --include="*.properties" --include="*.config" --include="*.xml" 2>/dev/null; then
    warn "Hardcoded URLs found — must be externalised to env vars for containers"
    grep -rn "http[s]\?://" "$SOURCE_DIR" \
        --include="*.properties" --include="*.config" --include="*.xml" 2>/dev/null \
        | grep -v '//\|#\|xmlns\|schema\|example' | head -10 | while read -r line; do
        info "  $line"
    done
fi

# TLS / SSL config
if grep -rqn "SSLContext\|TrustManager\|KeyStore\|SslStream\|X509Certificate\|https.protocols" \
   "$SOURCE_DIR" --include="*.java" --include="*.cs" 2>/dev/null; then
    warn "SSL/TLS code detected — verify TLS version compatibility with high-side"
    info "  Check: does high-side expect TLS 1.0/1.1? (common on WS2008 R2)"
    info "  If yes, explicitly enable legacy TLS in JVM/CLR config"
fi

# Message format / XML schema
if find "$SOURCE_DIR" -maxdepth 6 -name "*.xsd" | grep -q .; then
    XSD_COUNT=$(find "$SOURCE_DIR" -maxdepth 6 -name "*.xsd" | wc -l)
    warn "$XSD_COUNT XML schema(s) found — verify schema version compatibility with high-side"
    find "$SOURCE_DIR" -maxdepth 6 -name "*.xsd" | head -5 | while read -r f; do info "  $f"; done
fi

if find "$SOURCE_DIR" -maxdepth 6 -name "*.wsdl" | grep -q .; then
    warn "WSDL found — SOAP web service; ensure endpoint URL is configurable"
fi

# ---------- Windows-Specific Blockers ----------------------------------------
section "Windows-Specific Blockers"

WIN_BLOCKER=false

# Registry
if grep -rqn "Microsoft.Win32.Registry\|RegistryKey\|Registry.LocalMachine\|Registry.CurrentUser" \
   "$SOURCE_DIR" --include="*.cs" 2>/dev/null; then
    fail "Windows Registry access detected — BLOCKER for Linux containers"
    fail "  Replace with: IConfiguration / appsettings.json / env vars"
    WIN_BLOCKER=true
fi

# COM
if grep -rqn "ComImport\|Marshal.GetActiveObject\|CreateObject\|ProgId\|Guid(" \
   "$SOURCE_DIR" --include="*.cs" 2>/dev/null; then
    fail "COM interop detected — BLOCKER for Linux containers"
    fail "  Requires Windows container or complete refactoring"
    WIN_BLOCKER=true
fi

# MSMQ
if grep -rqn "System.Messaging\|MessageQueue\|MSMQ" \
   "$SOURCE_DIR" --include="*.cs" --include="*.config" 2>/dev/null; then
    fail "MSMQ detected — requires Windows; replace with RabbitMQ/Azure SB for Linux"
    WIN_BLOCKER=true
fi

# Windows auth
if grep -rqn "WindowsIdentity\|WindowsPrincipal\|Negotiate\|NTLM\|Kerberos" \
   "$SOURCE_DIR" --include="*.cs" --include="*.config" 2>/dev/null; then
    warn "Windows Authentication detected — may need proxy/negotiate workaround in container"
fi

# P/Invoke
if grep -rqn "DllImport\|P/Invoke\|extern.*static" \
   "$SOURCE_DIR" --include="*.cs" 2>/dev/null; then
    fail "P/Invoke (native DLL calls) detected — BLOCKER for Linux unless DLL has Linux equivalent"
    WIN_BLOCKER=true
fi

$WIN_BLOCKER || pass "No Windows-specific blockers detected"

# ---------- Configuration Externalisation -------------------------------------
section "Configuration Externalisation"

# Hardcoded hostnames
if grep -rqn '"\(localhost\|127\.0\.0\.1\|[a-z][a-z0-9-]*\.[a-z][a-z]*\)"' \
   "$SOURCE_DIR" --include="*.java" --include="*.cs" 2>/dev/null | grep -v '//'; then
    warn "Possible hardcoded hostnames in source code — must be env var configurable"
fi

# Hardcoded paths
if grep -rqn '[C-Z]:\\\\' "$SOURCE_DIR" \
   --include="*.java" --include="*.cs" --include="*.xml" \
   --include="*.properties" --include="*.config" 2>/dev/null; then
    fail "Hardcoded Windows drive paths (C:\\, D:\\) found — must be externalised"
fi

# Credentials in config
for cpat in "password" "passwd" "secret" "apikey" "api_key"; do
    if grep -rqin "${cpat}=" "$SOURCE_DIR" \
       --include="*.properties" --include="*.config" --include="*.xml" 2>/dev/null \
       | grep -v '<!--\|//\|example\|placeholder' | grep -q .; then
        fail "Possible hardcoded credential ($cpat) in config file"
    fi
done

# ---------- Logging -----------------------------------------------------------
section "Logging (12-Factor)"

if find "$SOURCE_DIR" -maxdepth 6 \
   -name "log4j*.properties" -o -name "log4j*.xml" \
   -o -name "logback*.xml" -o -name "log4net*.config" \
   -o -name "NLog.config" 2>/dev/null | grep -q .; then
    warn "File-based logging config found — ensure console appender is configured for container"
    info "Containers capture stdout/stderr — file appenders will write to ephemeral storage"
fi

# ---------- Docker Readiness --------------------------------------------------
section "Existing Docker Artefacts"
[[ -f "$SOURCE_DIR/Dockerfile" ]]      && pass "Dockerfile already present" || info "No Dockerfile — use a template from dockerfiles/"
[[ -f "$SOURCE_DIR/.dockerignore" ]]   && pass ".dockerignore present"       || warn ".dockerignore missing — create to exclude .git, build/, *.war"
[[ -f "$SOURCE_DIR/docker-compose.yml" ]] && pass "docker-compose.yml present" || info "No docker-compose.yml"

# ---------- Recommended Dockerfile -------------------------------------------
section "Recommended Dockerfile"
case "$TECH" in
    java-maven)
        case "$RUNTIME" in
            tomcat-war) info "Use: dockerfiles/java/Dockerfile.java8-tomcat-broker" ;;
            *) info "Use: dockerfiles/java/Dockerfile.java8-broker" ;;
        esac ;;
    csharp)
        if $WIN_BLOCKER; then
            case "$RUNTIME" in
                iis)             info "Use: dockerfiles/dotnet/Dockerfile.netfx-broker-iis" ;;
                windows-service) info "Use: dockerfiles/dotnet/Dockerfile.netfx-broker-service" ;;
            esac
        else
            info "Consider: dockerfiles/dotnet/Dockerfile.dotnet-migration (if migration viable)"
        fi ;;
    cpp) info "Use: dockerfiles/cpp/Dockerfile.cpp-windows-service" ;;
    *)   info "Technology not auto-detected — manually select from dockerfiles/" ;;
esac

# ---------- Summary -----------------------------------------------------------
echo ""
echo "======================================================"
echo -e "${BOLD} Assessment Summary — $COMPONENT_ID${NC}"
echo "======================================================"
echo -e "  ${GREEN}✅ Pass:${NC} $PASS_COUNT   ${YELLOW}⚠️  Warn:${NC} $WARN_COUNT   ${RED}❌ Fail:${NC} $FAIL_COUNT"
echo "  Technology: $TECH  |  Runtime: $RUNTIME"
echo ""
if [[ "$FAIL_COUNT" -eq 0 && "$WARN_COUNT" -le 2 ]]; then
    echo -e "  ${GREEN}VERDICT: Ready to containerise${NC}"
elif [[ "$FAIL_COUNT" -eq 0 ]]; then
    echo -e "  ${YELLOW}VERDICT: Containerisable — review warnings first${NC}"
elif $WIN_BLOCKER; then
    echo -e "  ${YELLOW}VERDICT: Windows container required OR remediation needed${NC}"
else
    echo -e "  ${RED}VERDICT: Blockers found — remediate before containerising${NC}"
fi
echo ""
echo "  Next: Complete inventory/component-triage-checklist.md"
echo "        Confirm interface contracts with ADMS on-site team"
echo "======================================================"
