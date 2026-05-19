#!/usr/bin/env bash
# migrate_database.sh — Dump local PostgreSQL and restore to Amazon RDS.
#
# Usage (positional args take priority over env vars):
#   bash aws-migration/scripts/migrate_database.sh <LOCAL_DB_URL> <RDS_ENDPOINT>
#
# Or via environment variables:
#   export LOCAL_DB_URL=postgresql://postgres:password@localhost:5432/skills_db
#   export RDS_ENDPOINT=skills-app-dev.xxxx.eu-west-1.rds.amazonaws.com
#   export RDS_PASSWORD=<your-rds-password>       # required for pg_restore
#   export RDS_USERNAME=skillsadmin               # defaults to skillsadmin
#   export RDS_DB_NAME=skills_db                  # defaults to skills_db
#   bash aws-migration/scripts/migrate_database.sh
#
# Requirements:
#   - pg_dump and pg_restore (PostgreSQL client tools)
#   - Network access to both the local DB and the RDS endpoint
#     (for RDS in a private subnet, run this from within the VPC or via SSM)

set -euo pipefail

# -----------------------------------------------------------------------
# Argument handling
# -----------------------------------------------------------------------
LOCAL_DB_URL="${1:-${LOCAL_DB_URL:-}}"
RDS_ENDPOINT="${2:-${RDS_ENDPOINT:-}}"
RDS_USERNAME="${RDS_USERNAME:-skillsadmin}"
RDS_DB_NAME="${RDS_DB_NAME:-skills_db}"
RDS_PASSWORD="${RDS_PASSWORD:-}"

if [ -z "${LOCAL_DB_URL}" ]; then
  echo "ERROR: LOCAL_DB_URL is required." >&2
  echo "Usage: $0 <LOCAL_DB_URL> <RDS_ENDPOINT>" >&2
  exit 1
fi

if [ -z "${RDS_ENDPOINT}" ]; then
  echo "ERROR: RDS_ENDPOINT is required." >&2
  echo "Usage: $0 <LOCAL_DB_URL> <RDS_ENDPOINT>" >&2
  exit 1
fi

if [ -z "${RDS_PASSWORD}" ]; then
  echo "ERROR: RDS_PASSWORD environment variable is required for pg_restore." >&2
  exit 1
fi

# -----------------------------------------------------------------------
# Paths and temp files
# -----------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_FILE="${SCRIPT_DIR}/../skills_backup.dump"

# -----------------------------------------------------------------------
# Cleanup trap — remove dump file on exit (success or failure)
# -----------------------------------------------------------------------
cleanup() {
  local exit_code=$?
  echo ""
  echo "==> Cleaning up temporary dump file..."
  rm -f "${DUMP_FILE}"
  if [ "${exit_code}" -ne 0 ]; then
    echo "ERROR: Migration failed with exit code ${exit_code}." >&2
  fi
  exit "${exit_code}"
}
trap cleanup EXIT

# -----------------------------------------------------------------------
# Step 1: Dump local database (custom format for pg_restore compatibility)
# -----------------------------------------------------------------------
echo "==> Dumping local database from ${LOCAL_DB_URL} ..."
pg_dump \
  "${LOCAL_DB_URL}" \
  --format=custom \
  --file="${DUMP_FILE}" \
  --verbose \
  --no-acl \
  --no-owner

DUMP_SIZE=$(du -sh "${DUMP_FILE}" | cut -f1)
echo "==> Dump complete. File size: ${DUMP_SIZE}"

# -----------------------------------------------------------------------
# Step 2: Restore to RDS
# -----------------------------------------------------------------------
echo ""
echo "==> Restoring to RDS at ${RDS_ENDPOINT} (database: ${RDS_DB_NAME}) ..."
export PGPASSWORD="${RDS_PASSWORD}"

pg_restore \
  --host="${RDS_ENDPOINT}" \
  --port=5432 \
  --username="${RDS_USERNAME}" \
  --dbname="${RDS_DB_NAME}" \
  --format=custom \
  --verbose \
  --no-acl \
  --no-owner \
  --exit-on-error \
  "${DUMP_FILE}"

echo "==> Restore complete."

# -----------------------------------------------------------------------
# Step 3: Validation — row counts for all 6 tables
# -----------------------------------------------------------------------
echo ""
echo "==> Validating migration — row counts per table:"
echo "------------------------------------------------------------"

TABLES=(departments employees skill_categories skills experience_levels employee_skills)

for TABLE in "${TABLES[@]}"; do
  COUNT=$(
    psql \
      --host="${RDS_ENDPOINT}" \
      --port=5432 \
      --username="${RDS_USERNAME}" \
      --dbname="${RDS_DB_NAME}" \
      --tuples-only \
      --command="SELECT COUNT(*) FROM ${TABLE};" \
    | tr -d '[:space:]'
  )
  printf "  %-25s %s rows\n" "${TABLE}" "${COUNT}"
done

echo "------------------------------------------------------------"
echo ""
echo "Migration completed successfully."
echo "RDS endpoint: ${RDS_ENDPOINT}"
echo "Database:     ${RDS_DB_NAME}"
