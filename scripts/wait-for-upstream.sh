#!/usr/bin/env bash
# =============================================================================
# wait-for-upstream.sh
# Waits for the upstream gateway/service to be reachable before starting
# the broker/marshaller process. Required because containers start faster
# than the upstream system (gateway, high-side connector) may be ready.
#
# Copy this into broker container images:
#   COPY scripts/wait-for-upstream.sh /usr/local/bin/
#   RUN chmod +x /usr/local/bin/wait-for-upstream.sh
#   ENTRYPOINT ["/usr/local/bin/wait-for-upstream.sh"]
#   CMD ["java", "-jar", "broker.jar"]
#
# Environment variables:
#   BROKER_UPSTREAM_HOST   — hostname of the upstream (required)
#   BROKER_UPSTREAM_PORT   — port of the upstream (required)
#   WAIT_MAX_SECONDS       — max wait time in seconds (default: 300)
#   WAIT_INTERVAL_SECONDS  — retry interval in seconds (default: 5)
#   SKIP_UPSTREAM_CHECK    — set to "true" to skip (e.g. for unit tests)
# =============================================================================

set -euo pipefail

UPSTREAM_HOST="${BROKER_UPSTREAM_HOST:-}"
UPSTREAM_PORT="${BROKER_UPSTREAM_PORT:-}"
MAX_WAIT="${WAIT_MAX_SECONDS:-300}"
INTERVAL="${WAIT_INTERVAL_SECONDS:-5}"
SKIP="${SKIP_UPSTREAM_CHECK:-false}"

if [[ "$SKIP" == "true" ]]; then
    echo "[wait-for-upstream] SKIP_UPSTREAM_CHECK=true — starting immediately"
    exec "$@"
fi

if [[ -z "$UPSTREAM_HOST" || -z "$UPSTREAM_PORT" ]]; then
    echo "[wait-for-upstream] WARNING: BROKER_UPSTREAM_HOST or BROKER_UPSTREAM_PORT not set"
    echo "[wait-for-upstream] Starting without upstream check"
    exec "$@"
fi

echo "[wait-for-upstream] Waiting for $UPSTREAM_HOST:$UPSTREAM_PORT (max ${MAX_WAIT}s)..."

elapsed=0
until nc -z "$UPSTREAM_HOST" "$UPSTREAM_PORT" 2>/dev/null; do
    if [[ $elapsed -ge $MAX_WAIT ]]; then
        echo "[wait-for-upstream] ERROR: $UPSTREAM_HOST:$UPSTREAM_PORT not available after ${MAX_WAIT}s"
        echo "[wait-for-upstream] Starting anyway — broker will handle reconnection"
        break
    fi
    echo "[wait-for-upstream] Not yet available (${elapsed}s elapsed). Retrying in ${INTERVAL}s..."
    sleep "$INTERVAL"
    elapsed=$((elapsed + INTERVAL))
done

if nc -z "$UPSTREAM_HOST" "$UPSTREAM_PORT" 2>/dev/null; then
    echo "[wait-for-upstream] $UPSTREAM_HOST:$UPSTREAM_PORT is available after ${elapsed}s"
fi

echo "[wait-for-upstream] Starting: $*"
exec "$@"
