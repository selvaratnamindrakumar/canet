#!/usr/bin/env bash
# =============================================================================
# dotnet-compatibility.sh
# Checks a .NET / C# version against Linux container readiness.
# Usage: bash dotnet-compatibility.sh <version>
#        e.g.  bash dotnet-compatibility.sh 4.8     (.NET Framework)
#              bash dotnet-compatibility.sh 8.0     (.NET 8)
#              bash dotnet-compatibility.sh core3.1 (.NET Core 3.1)
# =============================================================================

set -euo pipefail

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
    # Auto-detect
    if command -v dotnet &>/dev/null; then
        VERSION=$(dotnet --version 2>/dev/null | cut -d'.' -f1-2)
        echo "Auto-detected .NET version: $VERSION"
    else
        echo "ERROR: 'dotnet' CLI not found and no version argument supplied."
        echo "Usage: $0 <version>  e.g. 4.8 | 6.0 | 8.0"
        exit 1
    fi
fi

# Normalise: strip leading "core" if present
VERSION=$(echo "$VERSION" | sed 's/^[Cc]ore//')

# ---------- Version database --------------------------------------------------
RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}✅ PASS${NC}  $*"; }
warn() { echo -e "  ${YELLOW}⚠️  WARN${NC}  $*"; }
fail() { echo -e "  ${RED}❌ FAIL${NC}  $*"; }
info() { echo -e "  ${BLUE}ℹ️  INFO${NC}  $*"; }

SCORE=0; MAX=0
check() {
    MAX=$((MAX+1))
    local status="$1"; shift
    case "$status" in
        pass) SCORE=$((SCORE+1)); pass "$@" ;;
        warn) SCORE=$((SCORE+1)); warn "$@" ;;
        fail) fail "$@" ;;
    esac
}

# Determine framework vs modern
IS_FX=false
case "$VERSION" in
    2.0|2.5|3.0|3.5|4.0|4.5|4.5.1|4.5.2|4.6|4.6.1|4.6.2|4.7|4.7.1|4.7.2|4.8|4.8.1)
        IS_FX=true ;;
esac

echo ""
echo "======================================================"
if $IS_FX; then
    echo " .NET Compatibility Report — .NET Framework $VERSION"
else
    echo " .NET Compatibility Report — .NET $VERSION"
fi
echo "======================================================"

# ---------- .NET Framework checks --------------------------------------------
if $IS_FX; then
    echo ""
    echo "--- .NET Framework: Linux Container Feasibility ---"
    check fail ".NET Framework $VERSION does NOT support Linux containers natively"
    info "Windows Server Core containers are the only container option"
    info "  Recommended image: mcr.microsoft.com/dotnet/framework/runtime:$VERSION-windowsservercore-ltsc2022"
    info "  ASP.NET image:     mcr.microsoft.com/dotnet/framework/aspnet:$VERSION-windowsservercore-ltsc2022"

    echo ""
    echo "--- EOL Status ---"
    case "$VERSION" in
        2.0|2.5) check fail ".NET Framework $VERSION EOL 2011 — immediate upgrade required" ;;
        3.0|3.5) check warn ".NET Framework 3.5 — bundled with Windows, but 3.0 is EOL" ;;
        4.0)     check fail ".NET Framework 4.0 EOL 2016 — upgrade to 4.8" ;;
        4.5*)    check fail ".NET Framework 4.5.x EOL 2016 — upgrade to 4.8" ;;
        4.6*)    check warn ".NET Framework 4.6.x — approaching EOL; upgrade to 4.8" ;;
        4.7*)    check warn ".NET Framework 4.7.x — supported to 2027; consider migrating to .NET 8" ;;
        4.8|4.8.1) check pass ".NET Framework 4.8 — supported until 2029 (ships with Windows 11)" ;;
    esac

    echo ""
    echo "--- Migration Path to Linux Containers ---"
    info "Step 1: Assess incompatible features (WebForms, WCF server, COM, MSMQ, Registry)"
    info "Step 2: Run the .NET Upgrade Assistant: dotnet tool install --global upgrade-assistant"
    info "Step 3: Target .NET 8 (LTS) on Linux"
    info "Step 4: Replace OS-specific APIs with cross-platform alternatives"
    info ""
    info "Upgrade Assistant: https://github.com/dotnet/upgrade-assistant"

    echo ""
    echo "--- Windows Container Sizing ---"
    info "Windows Server Core image size: ~4 GB (expect 2-5 min pull time)"
    info "Requires Windows nodes in Kubernetes (nodeSelector: kubernetes.io/os: windows)"
    info "Windows containers do NOT run on Linux container hosts"

# ---------- .NET Core / .NET 5+ checks ----------------------------------------
else
    echo ""
    echo "--- Linux Container Support ---"
    case "$VERSION" in
        2.1|2.2|3.0) check fail ".NET $VERSION is EOL — upgrade to .NET 8 immediately" ;;
        3.1)         check warn ".NET Core 3.1 is EOL — migrate to .NET 8" ;;
        5.0)         check fail ".NET 5 is EOL — migrate to .NET 8" ;;
        6.0)         check warn ".NET 6 EOL Nov 2024 — migrate to .NET 8" ;;
        7.0)         check fail ".NET 7 is EOL — migrate to .NET 8" ;;
        8.0|8)       check pass ".NET 8 LTS — fully supported, Linux-native, EOL Nov 2026" ;;
        9.0|9)       check warn ".NET 9 STS — supported until May 2026; consider .NET 8 LTS for stability" ;;
        *)           check warn ".NET $VERSION — check https://endoflife.date/dotnet for status" ;;
    esac

    echo ""
    echo "--- Recommended Container Images ---"
    case "$VERSION" in
        8*|8.0) IMG_SDK="mcr.microsoft.com/dotnet/sdk:8.0-alpine"
                IMG_RT="mcr.microsoft.com/dotnet/aspnet:8.0-alpine" ;;
        9*|9.0) IMG_SDK="mcr.microsoft.com/dotnet/sdk:9.0-alpine"
                IMG_RT="mcr.microsoft.com/dotnet/aspnet:9.0-alpine" ;;
        6*|6.0) IMG_SDK="mcr.microsoft.com/dotnet/sdk:6.0-alpine"
                IMG_RT="mcr.microsoft.com/dotnet/aspnet:6.0-alpine" ;;
        *)      IMG_SDK="mcr.microsoft.com/dotnet/sdk:8.0-alpine  (upgrade recommended)"
                IMG_RT="mcr.microsoft.com/dotnet/aspnet:8.0-alpine" ;;
    esac
    info "Build image (SDK): $IMG_SDK"
    info "Runtime image:     $IMG_RT"

    echo ""
    echo "--- Multi-stage Dockerfile pattern ---"
    cat <<'EOF'
  FROM mcr.microsoft.com/dotnet/sdk:8.0-alpine AS build
  WORKDIR /src
  COPY *.csproj ./
  RUN dotnet restore
  COPY . ./
  RUN dotnet publish -c Release -o /app --no-restore

  FROM mcr.microsoft.com/dotnet/aspnet:8.0-alpine AS runtime
  WORKDIR /app
  COPY --from=build /app .
  RUN addgroup -S appgroup && adduser -S appuser -G appgroup
  USER appuser
  ENTRYPOINT ["dotnet", "YourApp.dll"]
EOF

    echo ""
    echo "--- Oracle connectivity on Linux ---"
    check pass "Oracle.ManagedDataAccess.Core NuGet works on Linux (pure managed)"
    info "Package: Oracle.ManagedDataAccess.Core (nuget.org)"
    info "No native Instant Client required — pure .NET, Linux-compatible"
    info "Connection string: Data Source=(DESCRIPTION=...);User Id=...;Password=...;"
fi

# ---------- Score -------------------------------------------------------------
echo ""
echo "======================================================"
echo " Score: $SCORE / $MAX checks"
if $IS_FX; then
    echo -e " ${YELLOW}Windows container only — Linux migration requires refactoring${NC}"
elif [[ "$SCORE" -eq "$MAX" ]]; then
    echo -e " ${GREEN}Linux container ready${NC}"
else
    echo -e " ${YELLOW}Review warnings above before containerising${NC}"
fi
echo "======================================================"
