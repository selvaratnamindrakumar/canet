#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# CaNet — Load / Performance Test Script
#
# Usage:
#   ./load-test.sh [concurrency] [requests] [mock_url] [app_url]
#
# Defaults:
#   concurrency  = 10
#   requests     = 100
#   mock_url     = http://localhost:8081
#   app_url      = http://localhost:8080
#
# Dependencies:
#   Required : curl
#   Optional : ab  (Apache Benchmark — brew install httpd / apt-get install apache2-utils)
#              python3 (for pretty-printing JSON stats; falls back to raw text)
#
# Examples:
#   ./load-test.sh                        # defaults
#   ./load-test.sh 20 200                 # 20 concurrent, 200 total requests
#   ./load-test.sh 10 100 http://mock:8081 http://app:8080   # Docker hostnames
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

CONCURRENCY=${1:-10}
REQUESTS=${2:-100}
MOCK="${3:-http://localhost:8081}"
APP="${4:-http://localhost:8080}"

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

header() { printf "\n${CYAN}${BOLD}═══ %s ═══${NC}\n" "$1"; }
ok()     { printf "  ${GREEN}✓${NC}  %s\n" "$1"; }
warn()   { printf "  ${YELLOW}⚠${NC}  %s\n" "$1"; }
err()    { printf "  ${RED}✗${NC}  %s\n" "$1"; }
info()   { printf "  ·  %s\n" "$1"; }

# ── Service reachability check ────────────────────────────────────────────────
check() {
    local url=$1 label=$2
    if curl -sf -o /dev/null --max-time 4 "$url" 2>/dev/null; then
        ok "$label is reachable  ($url)"
        return 0
    else
        err "$label is NOT reachable at $url"
        return 1
    fi
}

# ── Benchmark runner ──────────────────────────────────────────────────────────
# Uses ab when available; falls back to sequential curl with awk timing.
bench() {
    local url=$1 label=$2
    printf "\n  ${BOLD}%-40s${NC}\n" "$label"
    printf "  URL: %s\n" "$url"

    if command -v ab &>/dev/null; then
        ab -n "$REQUESTS" -c "$CONCURRENCY" -q "$url" 2>&1 | \
            grep -E "^(Requests per second|Time per request|Failed requests|Complete requests|Transfer rate)" | \
            sed "s/^/    /"
    else
        # Sequential fallback using curl + awk for timing
        local successes=0 failures=0
        local times_file
        times_file=$(mktemp)

        for _ in $(seq 1 "$REQUESTS"); do
            t=$(curl -sf -o /dev/null -w "%{time_total}" --max-time 15 "$url" 2>/dev/null || echo "")
            if [[ -z "$t" ]]; then
                ((failures++))
            else
                echo "$t" >> "$times_file"
                ((successes++))
            fi
        done

        if [[ -s "$times_file" ]]; then
            awk '{
                sum += $1; n++
                if (min=="" || $1<min) min=$1
                if ($1>max) max=$1
            } END {
                printf "    Requests:        %d OK / %d failed\n", n, '"$failures"'
                printf "    Avg latency:     %.3f s\n", sum/n
                printf "    Min latency:     %.3f s\n", min
                printf "    Max latency:     %.3f s\n", max
            }' "$times_file"
        fi
        rm -f "$times_file"
    fi
}

# ── Preflight ─────────────────────────────────────────────────────────────────
header "CaNet Load Test — Preflight"
printf "  Concurrency  : %s requests in parallel\n" "$CONCURRENCY"
printf "  Total req    : %s per scenario\n" "$REQUESTS"
printf "  Mock server  : %s\n" "$MOCK"
printf "  Main app     : %s\n" "$APP"

MOCK_OK=false; APP_OK=false
check "$MOCK/handsetdetails/35674108" "Mock server" && MOCK_OK=true || true
check "$APP/actuator/health"          "Main app"    && APP_OK=true  || true

if ! $MOCK_OK && ! $APP_OK; then
    printf "\n${RED}Neither service is running. Start them first:${NC}\n"
    printf "  Terminal 1 (mock):  cd mock-server    && mvn spring-boot:run\n"
    printf "  Terminal 2 (app):   cd spring-boot-app && mvn spring-boot:run -Dspring-boot.run.profiles=dev\n"
    printf "  OR use Docker:      docker compose up --build\n"
    exit 1
fi

if ! command -v ab &>/dev/null; then
    warn "ab (Apache Benchmark) not found — running sequential curl (no concurrency)"
    warn "Install: brew install httpd   OR   sudo apt-get install apache2-utils"
    CONCURRENCY=1
fi

# ── Mock server benchmarks ────────────────────────────────────────────────────
if $MOCK_OK; then
    header "Mock Server Benchmarks"

    bench "$MOCK/handsetdetails/35674108" \
          "Scenario 1: Single TAC lookup"

    bench "$MOCK/handsetdetails?tac=35674108,35282402,35428109,86345601,35567812" \
          "Scenario 2: Multi-TAC — 5 TACs"

    bench "$MOCK/handsetdetails?tac=35674108,35282402,35428109,86345601,35567812,86456723,35678934,35789045,35890156,35012367,35345678,35456789,35267801,86678901,86789012,86890123,35901234,86012345,86123456,35234567" \
          "Scenario 3: Multi-TAC — all 20 known TACs"

    bench "$MOCK/loadtest/bulk?count=100" \
          "Scenario 4: Bulk — 100 generated records"

    bench "$MOCK/loadtest/bulk?count=500" \
          "Scenario 5: Bulk — 500 generated records"

    bench "$MOCK/loadtest/search?count=20" \
          "Scenario 6: Generated search envelope — 20 records"

    bench "$MOCK/loadtest/slow?tac=35674108,35282402&delayMs=200" \
          "Scenario 7: Slow response — 200 ms artificial delay"

    bench "$MOCK/handsetdetails?manufacturer=Samsung" \
          "Scenario 8: Manufacturer filter (Samsung)"

    # Print stats from the mock server
    printf "\n  ${BOLD}Mock server request stats:${NC}\n"
    if command -v python3 &>/dev/null; then
        curl -sf "$MOCK/loadtest/stats" | python3 -c "
import json, sys
d = json.load(sys.stdin)
for k, v in d.items():
    print(f'    {k}: {v}')
" 2>/dev/null || curl -sf "$MOCK/loadtest/stats" | tr ',' '\n' | sed 's/[{}"  ]//g;s/^/    /'
    else
        curl -sf "$MOCK/loadtest/stats" | tr ',' '\n' | sed 's/[{}"  ]//g;s/^/    /'
    fi
fi

# ── Main app benchmarks ───────────────────────────────────────────────────────
if $APP_OK; then
    header "Main App Benchmarks"

    bench "$APP/?mode=handset&entries=35674108,35282402,35428109,86345601,35567812" \
          "Scenario 1: Handset search — 5 TACs"

    bench "$APP/?mode=handset&entries=35674108,35282402,35428109,86345601,35567812,86456723,35678934,35789045,35890156,35012367" \
          "Scenario 2: Handset search — 10 TACs"

    bench "$APP/?mode=cell&entries=353456789012345,354567890123456,352678012345678,866789012345678,867890123456789" \
          "Scenario 3: Cell IMEI search — 5 IMEIs"

    bench "$APP/actuator/health" \
          "Scenario 4: Health check (baseline)"

    bench "$APP/api/data/export.csv?mode=handset&entries=35674108,35282402,35428109,86345601,35567812" \
          "Scenario 5: CSV export — 5 TACs"
fi

# ── TAC / IMEI input helpers ──────────────────────────────────────────────────
if $MOCK_OK; then
    header "Test Data — Copy into the UI"

    printf "\n  ${BOLD}Handset mode — generated TACs (paste into textarea):${NC}\n"
    curl -sf "$MOCK/loadtest/tacs?count=20" | sed 's/^/  /'

    printf "\n\n  ${BOLD}Cell mode — generated IMEIs (paste into textarea):${NC}\n"
    curl -sf "$MOCK/loadtest/imeis?count=10" | sed 's/^/  /'
    printf "\n"
fi

# ── JUnit load test reminder ──────────────────────────────────────────────────
header "Run JUnit Concurrent Load Test"
printf "  Runs 20 threads × 25 requests = 500 total (in-process, no external server needed):\n\n"
printf "  ${YELLOW}cd spring-boot-app && mvn test -Dtest=ConcurrentSearchLoadTest -Dsurefire.failIfNoSpecifiedTests=false${NC}\n\n"
printf "  Output includes a ╔══ CaNet Load Test Report ══╗ block with:\n"
printf "    · Throughput (req/s)  · Avg / Min / Max latency\n"
printf "    · Success count       · Error count\n"

# ── Summary ───────────────────────────────────────────────────────────────────
header "Done"
printf "  ${BOLD}To reset mock server stats:${NC}  curl -X POST %s/loadtest/reset\n" "$MOCK"
printf "  ${BOLD}To increase delay:${NC}           MOCK_RESPONSE_DELAY_MS=500 mvn spring-boot:run  (mock server)\n"
printf "  ${BOLD}To raise concurrency:${NC}        ./load-test.sh 50 500\n\n"
