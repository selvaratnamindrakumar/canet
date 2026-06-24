#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 04-create-rds-oracle.sh  —  Create RDS Oracle SE2 (optional third backend)
#
# Cost note: RDS Oracle SE2 db.t3.small is ~$150-200/month (licence-included).
# For the POC evaluation only — delete after comparison is complete.
#
# Prerequisites: same VPC/subnet group as Aurora.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
source "$(dirname "$0")/01-setup-env.sh"

ORACLE_PASSWORD="${ORACLE_PASSWORD:?Set ORACLE_PASSWORD before running}"
DB_SUBNET_GROUP="${DB_SUBNET_GROUP:?Set DB_SUBNET_GROUP}"
VPC_SG_ID="${VPC_SG_ID:?Set VPC_SG_ID}"

echo "=== Creating RDS Oracle SE2 ==="
echo "Instance: $ORACLE_DB_IDENTIFIER"
echo "Service:  $ORACLE_SERVICE"
echo "User:     $ORACLE_USER"
echo ""
echo "WARNING: This will incur ~\$5-7/day in AWS charges."
read -r -p "Continue? (yes/no): " CONFIRM
[[ "$CONFIRM" != "yes" ]] && { echo "Aborted."; exit 0; }

ORACLE_STATUS=$(aws rds describe-db-instances \
    --db-instance-identifier "$ORACLE_DB_IDENTIFIER" \
    --region "$AWS_REGION" \
    --query "DBInstances[0].DBInstanceStatus" \
    --output text 2>/dev/null || echo "NOT_FOUND")

if [[ "$ORACLE_STATUS" == "available" ]]; then
    echo "Oracle instance $ORACLE_DB_IDENTIFIER already exists — skipping."
else
    echo "Creating RDS Oracle SE2 instance (this takes 10-15 minutes)..."
    aws rds create-db-instance \
        --db-instance-identifier "$ORACLE_DB_IDENTIFIER" \
        --db-instance-class db.t3.small \
        --engine oracle-se2 \
        --engine-version "19.0.0.0.ru-2024-04.rur-2024-04.r1" \
        --license-model license-included \
        --db-name "$ORACLE_SERVICE" \
        --master-username "$ORACLE_USER" \
        --master-user-password "$ORACLE_PASSWORD" \
        --allocated-storage 20 \
        --storage-type gp3 \
        --storage-encrypted \
        --no-multi-az \
        --no-publicly-accessible \
        --db-subnet-group-name "$DB_SUBNET_GROUP" \
        --vpc-security-group-ids "$VPC_SG_ID" \
        --region "$AWS_REGION" \
        --tags Key=Project,Value=AccumuloMigrationPOC Key=Environment,Value="$ENVIRONMENT"

    echo "Waiting for Oracle instance to become available..."
    aws rds wait db-instance-available \
        --db-instance-identifier "$ORACLE_DB_IDENTIFIER" \
        --region "$AWS_REGION"
fi

export ORACLE_ENDPOINT=$(aws rds describe-db-instances \
    --db-instance-identifier "$ORACLE_DB_IDENTIFIER" \
    --region "$AWS_REGION" \
    --query "DBInstances[0].Endpoint.Address" \
    --output text)

echo ""
echo "=== RDS Oracle SE2 ready ==="
echo "Endpoint: $ORACLE_ENDPOINT"
echo "Port:     1521"
echo "Service:  $ORACLE_SERVICE"
echo "Username: $ORACLE_USER"
echo ""
echo "Set these for the Spring Boot app:"
echo "  export ORACLE_ENDPOINT=$ORACLE_ENDPOINT"
echo "  export ORACLE_USER=$ORACLE_USER"
echo "  export ORACLE_PASSWORD='<your-password>'"
echo "  export ORACLE_SERVICE=$ORACLE_SERVICE"
echo ""
echo "Add 'oracle' to Spring profiles: --spring.profiles.active=aws,oracle"
echo ""
echo "REMINDER: Delete this instance after POC to avoid ongoing charges:"
echo "  aws rds delete-db-instance --db-instance-identifier $ORACLE_DB_IDENTIFIER --skip-final-snapshot"
