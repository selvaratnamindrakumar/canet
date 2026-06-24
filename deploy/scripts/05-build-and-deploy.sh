#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 05-build-and-deploy.sh
#
# Mode A (local JAR against real AWS):
#   Builds the JAR and runs it locally connected to sandbox DynamoDB + Aurora.
#   Quickest option for Thursday demo — no Elastic Beanstalk setup needed.
#
# Mode B (Elastic Beanstalk hosted):
#   Deploys the JAR to Elastic Beanstalk Java 17 SE platform.
#   Set DEPLOY_MODE=eb to use this mode.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
source "$(dirname "$0")/01-setup-env.sh"

DEPLOY_MODE="${DEPLOY_MODE:-local}"   # local | eb
MODULE_DIR="$(cd "$(dirname "$0")/../.." && pwd)/dynamo-aurora-poc"
SPRING_PROFILES="${SPRING_PROFILES:-aws}"   # e.g. "aws" or "aws,oracle"

# ── Required for all modes ─────────────────────────────────────────────────────
: "${AURORA_ENDPOINT:?Set AURORA_ENDPOINT (from script 03)}"
: "${AURORA_USER:?Set AURORA_USER}"
: "${AURORA_PASSWORD:?Set AURORA_PASSWORD}"

echo "=== Building dynamo-aurora-poc ==="
cd "$MODULE_DIR"
mvn clean package -DskipTests -q
JAR="target/dynamo-aurora-poc-0.0.1-SNAPSHOT.jar"
echo "Built: $JAR"
echo ""

# ── Mode A: Run locally against real AWS ─────────────────────────────────────
if [[ "$DEPLOY_MODE" == "local" ]]; then
    echo "=== Starting app locally against AWS sandbox ==="
    echo "Profiles:        $SPRING_PROFILES"
    echo "AWS Region:      $AWS_REGION"
    echo "DynamoDB table:  $DYNAMO_TABLE"
    echo "Aurora endpoint: $AURORA_ENDPOINT"
    [[ -n "${ORACLE_ENDPOINT:-}" ]] && echo "Oracle endpoint: $ORACLE_ENDPOINT"
    echo ""
    echo "App will be available at http://localhost:$APP_PORT"
    echo "Press Ctrl+C to stop."
    echo ""

    JAVA_OPTS_EXTRA=""
    [[ -n "${ORACLE_ENDPOINT:-}" ]] && \
        JAVA_OPTS_EXTRA="-Dspring.datasource.oracle.url=jdbc:oracle:thin:@//${ORACLE_ENDPOINT}:1521/${ORACLE_SERVICE:-ORCL} \
                         -Dspring.datasource.oracle.username=${ORACLE_USER} \
                         -Dspring.datasource.oracle.password=${ORACLE_PASSWORD}"

    java -jar "$JAR" \
        --spring.profiles.active="$SPRING_PROFILES" \
        --aws.region="$AWS_REGION" \
        --poc.dynamo.table-name="$DYNAMO_TABLE" \
        --spring.datasource.url="jdbc:postgresql://${AURORA_ENDPOINT}:5432/${AURORA_DB_NAME:-pocdb}" \
        --spring.datasource.username="$AURORA_USER" \
        --spring.datasource.password="$AURORA_PASSWORD" \
        $JAVA_OPTS_EXTRA

# ── Mode B: Elastic Beanstalk ─────────────────────────────────────────────────
elif [[ "$DEPLOY_MODE" == "eb" ]]; then
    : "${EB_APP_NAME:=dynamo-aurora-poc}"
    : "${EB_ENV_NAME:=neo-db-poc-sandbox}"

    echo "=== Deploying to Elastic Beanstalk ==="

    # Build the deployment bundle
    echo "Creating deployment bundle..."
    cp "$JAR" application.jar

    cat > Procfile <<EOF
web: java -jar application.jar --spring.profiles.active=${SPRING_PROFILES}
EOF

    zip -q deploy.zip application.jar Procfile
    rm application.jar Procfile
    echo "Bundle: deploy.zip"
    echo ""

    # Create EB app if it doesn't exist
    aws elasticbeanstalk describe-applications \
        --application-names "$EB_APP_NAME" \
        --region "$AWS_REGION" \
        --query "Applications[0].ApplicationName" \
        --output text 2>/dev/null | grep -q "$EB_APP_NAME" || \
    aws elasticbeanstalk create-application \
        --application-name "$EB_APP_NAME" \
        --region "$AWS_REGION"

    # Upload bundle to S3
    EB_BUCKET=$(aws elasticbeanstalk create-storage-location \
        --region "$AWS_REGION" \
        --query "S3Bucket" --output text)
    BUNDLE_KEY="${EB_APP_NAME}/deploy-$(date +%Y%m%d-%H%M%S).zip"

    aws s3 cp deploy.zip "s3://${EB_BUCKET}/${BUNDLE_KEY}" --region "$AWS_REGION"
    echo "Uploaded to s3://${EB_BUCKET}/${BUNDLE_KEY}"

    # Create application version
    VERSION_LABEL="v-$(date +%Y%m%d-%H%M%S)"
    aws elasticbeanstalk create-application-version \
        --application-name "$EB_APP_NAME" \
        --version-label "$VERSION_LABEL" \
        --source-bundle S3Bucket="$EB_BUCKET",S3Key="$BUNDLE_KEY" \
        --region "$AWS_REGION"

    # Create or update environment
    ENV_STATUS=$(aws elasticbeanstalk describe-environments \
        --environment-names "$EB_ENV_NAME" \
        --region "$AWS_REGION" \
        --query "Environments[0].Status" \
        --output text 2>/dev/null || echo "NOT_FOUND")

    if [[ "$ENV_STATUS" == "NOT_FOUND" || "$ENV_STATUS" == "Terminated" ]]; then
        echo "Creating Elastic Beanstalk environment..."
        aws elasticbeanstalk create-environment \
            --application-name "$EB_APP_NAME" \
            --environment-name "$EB_ENV_NAME" \
            --solution-stack-name "64bit Amazon Linux 2023 v4.3.1 running Corretto 17" \
            --version-label "$VERSION_LABEL" \
            --option-settings \
                Namespace=aws:autoscaling:launchconfiguration,OptionName=InstanceType,Value=t3.small \
                Namespace=aws:elasticbeanstalk:application:environment,OptionName=AWS_REGION,Value="$AWS_REGION" \
                Namespace=aws:elasticbeanstalk:application:environment,OptionName=AURORA_ENDPOINT,Value="$AURORA_ENDPOINT" \
                Namespace=aws:elasticbeanstalk:application:environment,OptionName=AURORA_USER,Value="$AURORA_USER" \
                Namespace=aws:elasticbeanstalk:application:environment,OptionName=AURORA_PASSWORD,Value="$AURORA_PASSWORD" \
                Namespace=aws:elasticbeanstalk:application:environment,OptionName=DYNAMO_TABLE,Value="$DYNAMO_TABLE" \
            --region "$AWS_REGION"
    else
        echo "Updating existing environment $EB_ENV_NAME..."
        aws elasticbeanstalk update-environment \
            --environment-name "$EB_ENV_NAME" \
            --version-label "$VERSION_LABEL" \
            --region "$AWS_REGION"
    fi

    echo "Waiting for environment to become ready..."
    aws elasticbeanstalk wait environment-updated \
        --environment-names "$EB_ENV_NAME" \
        --region "$AWS_REGION"

    EB_URL=$(aws elasticbeanstalk describe-environments \
        --environment-names "$EB_ENV_NAME" \
        --region "$AWS_REGION" \
        --query "Environments[0].CNAME" --output text)
    echo ""
    echo "=== Deployed to Elastic Beanstalk ==="
    echo "URL: http://$EB_URL"
    export BASE_URL="http://$EB_URL"
fi
