#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 03-create-rds-aurora.sh  —  Create Aurora PostgreSQL Serverless v2
# Prerequisites: VPC, subnets, and a DB subnet group must already exist.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
source "$(dirname "$0")/01-setup-env.sh"

# ── Required inputs ───────────────────────────────────────────────────────────
AURORA_PASSWORD="${AURORA_PASSWORD:?Set AURORA_PASSWORD before running this script}"
DB_SUBNET_GROUP="${DB_SUBNET_GROUP:?Set DB_SUBNET_GROUP (RDS subnet group name)}"
VPC_SG_ID="${VPC_SG_ID:?Set VPC_SG_ID (security group that allows app -> 5432)}"

echo "=== Creating Aurora PostgreSQL Serverless v2 ==="
echo "Cluster: $AURORA_DB_CLUSTER"
echo "DB:      $AURORA_DB_NAME"
echo "User:    $AURORA_USER"
echo ""

# ── Create cluster ────────────────────────────────────────────────────────────
CLUSTER_STATUS=$(aws rds describe-db-clusters \
    --db-cluster-identifier "$AURORA_DB_CLUSTER" \
    --region "$AWS_REGION" \
    --query "DBClusters[0].Status" \
    --output text 2>/dev/null || echo "NOT_FOUND")

if [[ "$CLUSTER_STATUS" == "available" ]]; then
    echo "Aurora cluster $AURORA_DB_CLUSTER already exists — skipping cluster creation."
else
    echo "Creating Aurora cluster..."
    aws rds create-db-cluster \
        --db-cluster-identifier "$AURORA_DB_CLUSTER" \
        --engine aurora-postgresql \
        --engine-version "15.4" \
        --database-name "$AURORA_DB_NAME" \
        --master-username "$AURORA_USER" \
        --master-user-password "$AURORA_PASSWORD" \
        --serverless-v2-scaling-configuration MinCapacity=0.5,MaxCapacity=4 \
        --db-subnet-group-name "$DB_SUBNET_GROUP" \
        --vpc-security-group-ids "$VPC_SG_ID" \
        --storage-encrypted \
        --no-deletion-protection \
        --region "$AWS_REGION" \
        --tags Key=Project,Value=AccumuloMigrationPOC Key=Environment,Value="$ENVIRONMENT"

    echo "Creating Aurora writer instance (db.serverless)..."
    aws rds create-db-instance \
        --db-instance-identifier "${AURORA_DB_CLUSTER}-writer" \
        --db-cluster-identifier "$AURORA_DB_CLUSTER" \
        --engine aurora-postgresql \
        --db-instance-class db.serverless \
        --region "$AWS_REGION"

    echo "Waiting for cluster to become available (may take 5-10 minutes)..."
    aws rds wait db-cluster-available \
        --db-cluster-identifier "$AURORA_DB_CLUSTER" \
        --region "$AWS_REGION"
fi

# ── Get endpoint ──────────────────────────────────────────────────────────────
export AURORA_ENDPOINT=$(aws rds describe-db-clusters \
    --db-cluster-identifier "$AURORA_DB_CLUSTER" \
    --region "$AWS_REGION" \
    --query "DBClusters[0].Endpoint" \
    --output text)

echo ""
echo "=== Aurora PostgreSQL ready ==="
echo "Endpoint:  $AURORA_ENDPOINT"
echo "Port:      5432"
echo "Database:  $AURORA_DB_NAME"
echo "Username:  $AURORA_USER"
echo ""
echo "Set these environment variables for the Spring Boot app:"
echo "  export AURORA_ENDPOINT=$AURORA_ENDPOINT"
echo "  export AURORA_USER=$AURORA_USER"
echo "  export AURORA_PASSWORD='<your-password>'"
