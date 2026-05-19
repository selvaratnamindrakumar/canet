# Operations Runbook — Employee Skills Tracking App on AWS

This runbook covers day-to-day operations for the AWS deployment. Run all commands from the repository root unless otherwise noted.

---

## 1. Deploy from Scratch

### Prerequisites checklist

- [ ] AWS CLI v2 configured (`aws sts get-caller-identity` returns your account ID)
- [ ] Terraform >= 1.6 installed (`terraform version`)
- [ ] Python 3.11+ with pip installed
- [ ] `zip` utility available
- [ ] `pg_dump` / `pg_restore` installed (for data migration only)

### Step 1 — Create a Terraform variables file

```bash
cat > skills-app/aws-migration/terraform/terraform.tfvars <<EOF
aws_region        = "eu-west-1"
project_name      = "skills-app"
environment       = "dev"
db_name           = "skills_db"
db_username       = "skillsadmin"
db_password       = "REPLACE_WITH_STRONG_PASSWORD"
db_instance_class = "db.t3.micro"
lambda_memory_mb  = 512
EOF
```

**Never commit `terraform.tfvars` to version control** (it contains the DB password).

### Step 2 — Build the Lambda package

```bash
bash skills-app/aws-migration/scripts/build_lambda.sh
# Output: skills-app/aws-migration/lambda_package.zip
```

### Step 3 — Provision AWS infrastructure

```bash
cd skills-app/aws-migration/terraform
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

Note the outputs after apply:

```bash
terraform output
# rds_endpoint         = "skills-app-dev.xxxx.eu-west-1.rds.amazonaws.com"
# api_gateway_url      = "https://abc123.execute-api.eu-west-1.amazonaws.com"
# cloudfront_domain    = "d1234abcdef.cloudfront.net"
# s3_bucket_name       = "skills-app-dev-frontend-123456789012"
# lambda_function_name = "skills-app-dev-api"
# secrets_manager_arn  = "arn:aws:secretsmanager:..."
```

### Step 4 — Initialise the database schema

The RDS instance is in a private subnet. Connect via AWS Systems Manager Session Manager (no bastion needed):

```bash
# Start a port-forwarding session to RDS via SSM (requires an EC2 instance in the VPC
# with the SSM agent, or use Cloud9)
aws ssm start-session \
  --target <ec2-instance-id-in-vpc> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<rds_endpoint>"],"portNumber":["5432"],"localPortNumber":["5433"]}'

# In a separate terminal, apply the schema via the forwarded port:
export PGPASSWORD=<your-db-password>
psql -h 127.0.0.1 -p 5433 -U skillsadmin -d skills_db \
  -f skills-app/database/01_schema.sql
```

### Step 5 — Migrate existing data (if upgrading from local)

```bash
export LOCAL_DB_URL=postgresql://postgres:password@localhost:5432/skills_db
export RDS_ENDPOINT=$(cd skills-app/aws-migration/terraform && terraform output -raw rds_endpoint)
export RDS_PASSWORD=<your-db-password>

bash skills-app/aws-migration/scripts/migrate_database.sh
```

### Step 6 — Deploy the frontend

```bash
# Update api.js with the real API Gateway URL
API_URL=$(cd skills-app/aws-migration/terraform && terraform output -raw api_gateway_url)
bash skills-app/aws-migration/scripts/update_frontend_api_url.sh "$API_URL"

# Sync to S3 and invalidate CloudFront
S3_BUCKET=$(cd skills-app/aws-migration/terraform && terraform output -raw s3_bucket_name)
CF_ID=$(cd skills-app/aws-migration/terraform && terraform output -raw cloudfront_distribution_id)
bash skills-app/aws-migration/scripts/deploy_frontend.sh "$S3_BUCKET" "$CF_ID"
```

### Step 7 — Verify the deployment

```bash
API_URL=$(cd skills-app/aws-migration/terraform && terraform output -raw api_gateway_url)
CF_DOMAIN=$(cd skills-app/aws-migration/terraform && terraform output -raw cloudfront_domain)

# Test API health
curl -s "$API_URL/" | python3 -m json.tool

# Test employee list endpoint
curl -s "$API_URL/employees/" | python3 -m json.tool

# Open frontend
echo "Frontend: https://$CF_DOMAIN"
```

---

## 2. Update the Lambda (New Backend Code)

Run these steps whenever the FastAPI application code changes.

```bash
# 1. Rebuild the ZIP
bash skills-app/aws-migration/scripts/build_lambda.sh

# 2. Get the Lambda function name
LAMBDA_NAME=$(cd skills-app/aws-migration/terraform && terraform output -raw lambda_function_name)

# 3. Upload new code
aws lambda update-function-code \
  --function-name "$LAMBDA_NAME" \
  --zip-file fileb://skills-app/aws-migration/lambda_package.zip

# 4. Wait for update to complete
aws lambda wait function-updated \
  --function-name "$LAMBDA_NAME"

# 5. Publish a new version (for alias-based rollback)
aws lambda publish-version \
  --function-name "$LAMBDA_NAME" \
  --description "Deploy $(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "Lambda updated successfully."
```

Alternatively, if using Terraform to manage the Lambda:

```bash
cd skills-app/aws-migration/terraform
terraform apply -target=aws_lambda_function.api
```

---

## 3. Update the Frontend

```bash
# 1. Make your changes to skills-app/frontend/

# 2. Deploy to S3 and invalidate cache
S3_BUCKET=$(cd skills-app/aws-migration/terraform && terraform output -raw s3_bucket_name)
CF_ID=$(cd skills-app/aws-migration/terraform && terraform output -raw cloudfront_distribution_id)

bash skills-app/aws-migration/scripts/deploy_frontend.sh "$S3_BUCKET" "$CF_ID"
```

CloudFront invalidation takes 30–60 seconds to propagate globally.

---

## 4. Connect to RDS

### Option A: AWS Systems Manager Session Manager (recommended — no bastion)

Requires an EC2 instance (or Cloud9) in the VPC with the SSM agent and `AmazonSSMManagedInstanceCore` IAM policy.

```bash
# Start port-forwarding tunnel in terminal 1
aws ssm start-session \
  --target <ec2-instance-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"$(cd skills-app/aws-migration/terraform && terraform output -raw rds_endpoint)\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"5433\"]}"

# Connect in terminal 2
export PGPASSWORD=<your-db-password>
psql -h 127.0.0.1 -p 5433 -U skillsadmin -d skills_db
```

### Option B: RDS Proxy (for application use)

Add an `aws_db_proxy` Terraform resource (see architecture.md) and point Lambda at the proxy endpoint. The proxy handles connection pooling and reconnects automatically.

### Option C: Temporary EC2 bastion

```bash
# Launch a micro instance in a public subnet (terminate after use!)
aws ec2 run-instances \
  --image-id ami-0694d931cee176e7d \
  --instance-type t3.nano \
  --subnet-id <public-subnet-id> \
  --security-group-ids <bastion-sg-id> \
  --key-name <your-key-pair> \
  --query "Instances[0].InstanceId" \
  --output text

# SSH in and connect to RDS from there
ssh -i ~/.ssh/<key>.pem ec2-user@<bastion-public-ip>
psql -h <rds_endpoint> -U skillsadmin -d skills_db
```

Always terminate temporary bastions after use.

---

## 5. Rotate DB Credentials (Secrets Manager)

### Manual rotation

```bash
# Generate a new password (example using openssl)
NEW_PASSWORD=$(openssl rand -base64 24)

# Update RDS master password
RDS_ID=$(cd skills-app/aws-migration/terraform && terraform output -raw rds_endpoint | cut -d. -f1)
aws rds modify-db-instance \
  --db-instance-identifier "${RDS_ID}" \
  --master-user-password "${NEW_PASSWORD}" \
  --apply-immediately

# Update the secret in Secrets Manager
SECRET_ARN=$(cd skills-app/aws-migration/terraform && terraform output -raw secrets_manager_arn)
aws secretsmanager get-secret-value --secret-id "$SECRET_ARN" \
  | python3 -c "import sys,json; s=json.load(sys.stdin); s['password']='${NEW_PASSWORD}'; print(json.dumps(s))" \
  > /tmp/new_secret.json

aws secretsmanager put-secret-value \
  --secret-id "$SECRET_ARN" \
  --secret-string file:///tmp/new_secret.json

rm /tmp/new_secret.json

echo "Credentials rotated. Lambda will pick up the new password on next cold start."
```

### Automatic rotation with Secrets Manager

To enable automatic rotation, attach a rotation Lambda:

```bash
aws secretsmanager rotate-secret \
  --secret-id "$SECRET_ARN" \
  --rotation-lambda-arn <rotation-lambda-arn> \
  --rotation-rules AutomaticallyAfterDays=30
```

AWS provides a managed rotation Lambda for RDS PostgreSQL — see the [Secrets Manager documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html).

---

## 6. Roll Back to a Previous Lambda Version

### Using Lambda versions

Each time you run `aws lambda publish-version`, a numbered version is created.

```bash
LAMBDA_NAME=$(cd skills-app/aws-migration/terraform && terraform output -raw lambda_function_name)

# List available versions
aws lambda list-versions-by-function \
  --function-name "$LAMBDA_NAME" \
  --query "Versions[*].{Version:Version,Description:Description,LastModified:LastModified}" \
  --output table

# Update the 'live' alias to point to a previous version
aws lambda update-alias \
  --function-name "$LAMBDA_NAME" \
  --name live \
  --function-version 3    # replace 3 with the desired version number

# Or configure weighted traffic split (canary deployment):
aws lambda update-alias \
  --function-name "$LAMBDA_NAME" \
  --name live \
  --function-version 5 \
  --routing-config AdditionalVersionWeights={"4"=0.1}
  # Sends 10% of traffic to version 4, 90% to version 5
```

### Using the $LATEST (immediate rollback)

If you haven't published versions, re-upload the previous ZIP:

```bash
git stash   # or git checkout <previous-commit>
bash skills-app/aws-migration/scripts/build_lambda.sh
aws lambda update-function-code \
  --function-name "$LAMBDA_NAME" \
  --zip-file fileb://skills-app/aws-migration/lambda_package.zip
```

---

## 7. Troubleshooting

### Lambda cold start issues

**Symptom**: First request after idle period takes 5–15 seconds.

**Causes and mitigations**:

| Cause | Fix |
|-------|-----|
| Python dependency loading | Reduce `lambda/requirements.txt` — remove unused packages; use Lambda layers for large deps (psycopg2, etc.) |
| VPC ENI attachment | This is the main cold-start contributor (~3–5 s). Enable **Provisioned Concurrency** to keep containers warm. |
| Secrets Manager call | Cache the secret value; the current handler does this naturally (env var is set once and reused). |
| Large ZIP file | Keep the package under 50 MB unzipped. Check with `du -sh skills-app/aws-migration/build/` |

Enable Provisioned Concurrency to eliminate cold starts:

```bash
aws lambda put-provisioned-concurrency-config \
  --function-name "$LAMBDA_NAME" \
  --qualifier live \
  --provisioned-concurrent-executions 2
```

### RDS connection limit errors

**Symptom**: `FATAL: remaining connection slots are reserved` in Lambda logs.

**Fix**:

1. Check current connections:

```sql
SELECT count(*), state FROM pg_stat_activity GROUP BY state;
```

2. Add RDS Proxy (Terraform):

```hcl
resource "aws_db_proxy" "main" {
  name                   = "${local.name_prefix}-proxy"
  debug_logging          = false
  engine_family          = "POSTGRESQL"
  idle_client_timeout    = 1800
  require_tls            = true
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_security_group_ids = [aws_security_group.rds.id]
  vpc_subnet_ids         = aws_subnet.private[*].id

  auth {
    auth_scheme = "SECRETS"
    secret_arn  = aws_secretsmanager_secret.db_credentials.arn
  }
}
```

3. Alternatively, reduce the SQLAlchemy pool size in `backend/app/database.py`:

```python
engine = create_engine(
    DATABASE_URL,
    pool_size=2,
    max_overflow=1,
    pool_timeout=10,
    pool_pre_ping=True,
)
```

### CloudFront cache issues

**Symptom**: Updated frontend not showing after deployment.

**Steps**:

1. Confirm the S3 upload succeeded:

```bash
aws s3 ls "s3://${S3_BUCKET}/" --recursive | grep -E "\.(html|js|css)$"
```

2. Check the CloudFront invalidation status:

```bash
aws cloudfront list-invalidations \
  --distribution-id "$CF_ID" \
  --query "InvalidationList.Items[0].{Status:Status,CreateTime:CreateTime}"
```

3. Create a manual invalidation if needed:

```bash
aws cloudfront create-invalidation \
  --distribution-id "$CF_ID" \
  --paths "/*"
```

4. Bypass CloudFront for testing by fetching directly from S3 (via a signed URL or temporarily enabling public access for testing).

### Lambda cannot reach RDS

**Symptom**: `could not connect to server: Connection refused` in Lambda logs.

**Checklist**:

- [ ] Lambda is in the same VPC as RDS (`vpc_config.subnet_ids` in `aws_lambda_function`)
- [ ] Lambda SG (`lambda_sg`) has egress rule for port 5432 to VPC CIDR `10.0.0.0/16`
- [ ] RDS SG (`rds_sg`) has ingress rule for port 5432 from `lambda_sg`
- [ ] `DATABASE_URL` is correctly set (check `DB_SECRET_ARN` env var on the function)
- [ ] RDS instance is in `available` state: `aws rds describe-db-instances --db-instance-identifier ${RDS_ID} --query "DBInstances[0].DBInstanceStatus"`

### Lambda cannot reach Secrets Manager

**Symptom**: `botocore.exceptions.ClientError: An error occurred (AccessDeniedException)` on cold start.

**Checklist**:

- [ ] Lambda execution role has `secretsmanager:GetSecretValue` on the secret ARN
- [ ] `DB_SECRET_ARN` environment variable is set correctly on the Lambda function
- [ ] NAT Gateway is operational (Lambda needs outbound HTTPS to reach Secrets Manager endpoint)
- [ ] Alternative: create a VPC interface endpoint for Secrets Manager to avoid NAT Gateway

```bash
# Check Lambda env vars
aws lambda get-function-configuration \
  --function-name "$LAMBDA_NAME" \
  --query "Environment.Variables"
```

### API Gateway 5xx errors

**Symptom**: Clients receive HTTP 500/502/504.

```bash
# Fetch recent Lambda logs
LOG_GROUP="/aws/lambda/$LAMBDA_NAME"
aws logs tail "$LOG_GROUP" --since 1h --follow
```

Common causes:
- `502 Bad Gateway`: Lambda returned a malformed response (check Mangum version compatibility with API Gateway payload format version 2.0).
- `504 Gateway Timeout`: Lambda function exceeded 30-second timeout (check for slow DB queries with Performance Insights).
- `500 Internal Server Error`: Unhandled exception in FastAPI — check Lambda logs for the traceback.

---

## 8. Tear Down

To destroy all AWS resources (irreversible):

```bash
cd skills-app/aws-migration/terraform

# Empty the S3 bucket first (Terraform cannot delete non-empty buckets)
S3_BUCKET=$(terraform output -raw s3_bucket_name)
aws s3 rm "s3://${S3_BUCKET}/" --recursive

# Delete the RDS final snapshot if you don't want to keep it
# (omit this to preserve the backup)

terraform destroy
```

To restart the local stack after teardown:

```bash
cd skills-app
docker-compose up -d
```
