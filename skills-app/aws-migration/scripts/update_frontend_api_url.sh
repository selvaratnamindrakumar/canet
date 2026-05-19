#!/usr/bin/env bash
# update_frontend_api_url.sh — Replace the localhost API base URL in frontend/js/api.js
#                              with the real API Gateway URL.
#
# Usage:
#   bash aws-migration/scripts/update_frontend_api_url.sh <api-gateway-url>
#
# Or via environment variable:
#   export API_GATEWAY_URL=https://abc123.execute-api.eu-west-1.amazonaws.com
#   bash aws-migration/scripts/update_frontend_api_url.sh
#
# The script rewrites:
#   export const API_BASE = 'http://localhost:8000';
# to:
#   export const API_BASE = '<api-gateway-url>';

set -e

API_URL="${1:-${API_GATEWAY_URL:-}}"

if [ -z "${API_URL}" ]; then
  echo "Usage: $0 <api-gateway-url>" >&2
  echo "       or set API_GATEWAY_URL env var." >&2
  exit 1
fi

FRONTEND_DIR="$(dirname "$0")/../../frontend/js"
API_JS="${FRONTEND_DIR}/api.js"

if [ ! -f "${API_JS}" ]; then
  echo "ERROR: ${API_JS} not found." >&2
  exit 1
fi

sed -i "s|http://localhost:8000|${API_URL}|g" "${API_JS}"

echo "Updated api.js with API URL: ${API_URL}"
