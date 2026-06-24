#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 02-create-dynamodb.sh  —  Create the DynamoDB handset-reference table
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
source "$(dirname "$0")/01-setup-env.sh"

echo "=== Creating DynamoDB Table: $DYNAMO_TABLE ==="

STATUS=$(aws dynamodb describe-table \
    --table-name "$DYNAMO_TABLE" \
    --region "$AWS_REGION" \
    --query "Table.TableStatus" \
    --output text 2>/dev/null || echo "NOT_FOUND")

if [[ "$STATUS" == "ACTIVE" ]]; then
    echo "Table $DYNAMO_TABLE already exists and is ACTIVE — skipping creation."
else
    echo "Creating table..."
    aws dynamodb create-table \
        --table-name "$DYNAMO_TABLE" \
        --attribute-definitions AttributeName=cgi,AttributeType=S \
        --key-schema AttributeName=cgi,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST \
        --region "$AWS_REGION" \
        --tags Key=Project,Value=AccumuloMigrationPOC Key=Environment,Value="$ENVIRONMENT"

    echo "Waiting for table to become ACTIVE..."
    aws dynamodb wait table-exists \
        --table-name "$DYNAMO_TABLE" \
        --region "$AWS_REGION"

    echo "Table created:"
    aws dynamodb describe-table \
        --table-name "$DYNAMO_TABLE" \
        --region "$AWS_REGION" \
        --query "Table.{Name:TableName,Status:TableStatus,BillingMode:BillingModeSummary.BillingMode}" \
        --output table
fi

echo ""
export DYNAMO_TABLE_ARN=$(aws dynamodb describe-table \
    --table-name "$DYNAMO_TABLE" \
    --region "$AWS_REGION" \
    --query "Table.TableArn" --output text)
echo "DynamoDB Table ARN: $DYNAMO_TABLE_ARN"
echo "=== DynamoDB ready ==="
