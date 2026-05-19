# AWS Migration Guide — Employee Skills Tracking App

This guide covers migrating the Employee Skills Tracking application from a local
Docker Compose stack (PostgreSQL + FastAPI + static HTML) to a fully managed AWS
infrastructure with zero schema changes.

---

## Architecture Overview

```
                         ┌─────────────────────────────────────────────────────┐
                         │                      AWS Cloud                       │
                         │                                                      │
  ┌─────────┐  HTTPS     │  ┌──────────────┐   ┌──────────────────────────┐   │
  │         │──────────────▶│  CloudFront  │──▶│   S3 Bucket (frontend)   │   │
  │ Browser │            │  │ Distribution │   │  index.html / js / css   │   │
  │         │            │  └──────────────┘   └──────────────────────────┘   │
  │         │  HTTPS     │                                                      │
  │         │──────────────▶┌──────────────┐   ┌──────────────────────────┐   │
  └─────────┘  (REST API)│  │ API Gateway  │──▶│  Lambda Function         │   │
                         │  │  HTTP API    │   │  (FastAPI + Mangum)      │   │
                         │  └──────────────┘   └────────────┬─────────────┘   │
                         │                                   │                  │
                         │              ┌────────────────────▼──────────────┐  │
                         │              │         Private Subnets (VPC)     │  │
                         │              │                                    │  │
                         │              │  ┌─────────────────────────────┐  │  │
                         │              │  │   RDS PostgreSQL 16         │  │  │
                         │              │  │   db.t3.micro / gp3 20 GB   │  │  │
                         │              │  │   Port 5432 (lambda_sg only)│  │  │
                         │              │  └─────────────────────────────┘  │  │
                         │              └────────────────────────────────────┘  │
                         │                                                      │
                         │  ┌──────────────────────────────────────────────┐   │
                         │  │  AWS Secrets Manager  (DB credentials)       │   │
                         │  └──────────────────────────────────────────────┘   │
                         └─────────────────────────────────────────────────────┘
```

---

## Technology Choices

| Component       | Local (current)             | AWS (target)                        | Reason                                                                 |
|-----------------|-----------------------------|-------------------------------------|------------------------------------------------------------------------|
| Database        | PostgreSQL (Docker)         | Amazon RDS for PostgreSQL 16        | Drop-in replacement; no schema changes; managed backups & Multi-AZ HA |
| Backend API     | FastAPI (uvicorn, local)    | AWS Lambda + API Gateway HTTP API   | No idle cost; auto-scales; HR tool has low sustained concurrency       |
| WSGI/ASGI shim  | —                           | Mangum adapter                      | Bridges ASGI (FastAPI) to Lambda event/context interface               |
| Frontend        | Local file server / nginx   | Amazon S3 + CloudFront              | Static files only; global CDN; no servers to manage                   |
| Secrets         | .env file                   | AWS Secrets Manager                 | Encrypted; audited; automatic rotation support                         |
| Networking      | Docker bridge network       | VPC (public + private subnets)      | RDS isolated in private subnets; Lambda VPC-enabled                    |
| IaC             | docker-compose.yml          | Terraform (AWS provider ~5.x)       | Reproducible, version-controlled infrastructure                        |

> **Alternative — Aurora PostgreSQL Serverless v2**: Better choice if load is highly
> variable (e.g. all queries happen during a 2-hour review session then zero for 12 h).
> Aurora scales ACUs to near-zero and back in seconds. Cost can be lower at very low
> usage, but minimum ACU charge may exceed RDS t3.micro for consistently light use.
> Switch by changing `aws_db_instance` to `aws_rds_cluster` + `aws_rds_cluster_instance`
> with `engine_mode = "provisioned"` and `serverlessv2_scaling_configuration`.

> **Alternative — ECS Fargate**: Better choice if the API has sustained high traffic,
> requires WebSocket connections, long-running background tasks, or you want to avoid
> Lambda cold starts entirely. Use the existing `Dockerfile` in `backend/`. Costs more
> at idle (minimum task charge) but removes the 15-minute Lambda timeout limit.

---

## Prerequisites

| Tool        | Minimum version | Install                                   |
|-------------|-----------------|-------------------------------------------|
| AWS CLI     | 2.x             | https://docs.aws.amazon.com/cli/          |
| Terraform   | 1.6+            | https://developer.hashicorp.com/terraform |
| Python      | 3.11            | https://www.python.org/                   |
| PostgreSQL   | 14+ (client)    | `apt install postgresql-client` / brew    |
| zip         | any             | standard on Linux/macOS                   |

Configure AWS credentials before starting:

```bash
aws configure
# or use AWS SSO: aws sso login --profile my-profile
```

---

## Step-by-Step Migration

### 1. Database Migration (RDS Setup + Data Transfer)

#### 1a. Provision RDS with Terraform

```bash
cd aws-migration/terraform
cp terraform.tfvars.example terraform.tfvars   # edit with your db_password
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

After apply, note the outputs:

```bash
terraform output rds_endpoint      # e.g. skills-app-dev.xxxx.eu-west-1.rds.amazonaws.com
terraform output api_gateway_url
terraform output cloudfront_domain
terraform output s3_bucket_name
```

#### 1b. Initialise the Schema on RDS

The RDS instance is in a private subnet. Use a temporary EC2 bastion **or** run the
following from a machine in the same VPC (e.g. a Cloud9 environment or via AWS SSM):

```bash
export PGPASSWORD=<your-db-password>
psql -h <rds_endpoint> -U skillsadmin -d skills_db \
  -f ../../database/01_schema.sql
```

Alternatively, if this is a fresh deployment you can let SQLAlchemy create the schema
on the first Lambda cold start (ensure `Base.metadata.create_all(bind=engine)` is
called in `app/database.py`).

#### 1c. Migrate Existing Data

Use the migration script (requires `pg_dump` / `pg_restore` client tools):

```bash
export LOCAL_DB_URL=postgresql://postgres:password@localhost:5432/skills_db
export RDS_ENDPOINT=<terraform output rds_endpoint>
export RDS_PASSWORD=<your-db-password>

bash aws-migration/scripts/migrate_database.sh
```

The script dumps the local database, restores it to RDS, then prints a row count
validation for every table.

---

### 2. Backend Deployment (Lambda Packaging)

#### 2a. Build the Lambda ZIP

```bash
bash aws-migration/scripts/build_lambda.sh
```

This creates `aws-migration/lambda_package.zip` (~20–40 MB).

#### 2b. Upload to Lambda

```bash
LAMBDA_NAME=$(cd aws-migration/terraform && terraform output -raw lambda_function_name)

aws lambda update-function-code \
  --function-name "$LAMBDA_NAME" \
  --zip-file fileb://aws-migration/lambda_package.zip
```

Or let Terraform manage the initial deploy — it references `lambda_package.zip`
directly in the `aws_lambda_function` resource. Run `terraform apply` after building
the package.

#### 2c. Verify the API

```bash
API_URL=$(cd aws-migration/terraform && terraform output -raw api_gateway_url)
curl "$API_URL/"
# {"message":"Employee Skills API","docs":"/docs"}
```

---

### 3. Frontend Deployment (S3 + CloudFront)

#### 3a. Update the API base URL in the frontend

```bash
API_URL=$(cd aws-migration/terraform && terraform output -raw api_gateway_url)
bash aws-migration/scripts/update_frontend_api_url.sh "$API_URL"
```

This replaces `http://localhost:8000` with the real API Gateway URL in
`frontend/js/api.js`.

#### 3b. Sync frontend to S3 and invalidate CloudFront

```bash
export S3_BUCKET=$(cd aws-migration/terraform && terraform output -raw s3_bucket_name)
export CLOUDFRONT_ID=$(cd aws-migration/terraform && terraform output -raw cloudfront_distribution_id 2>/dev/null || echo "")

bash aws-migration/scripts/deploy_frontend.sh "$S3_BUCKET" "$CLOUDFRONT_ID"
```

---

### 4. DNS / Environment Config

#### Custom domain (optional)

1. Register or transfer a domain in Route 53 (or use an existing hosted zone).
2. Request a certificate in **ACM (us-east-1 region)** for CloudFront — CloudFront
   only accepts certificates from us-east-1.
3. Add to the CloudFront distribution in Terraform:

```hcl
viewer_certificate {
  acm_certificate_arn      = aws_acm_certificate.cert.arn
  ssl_support_method       = "sni-only"
  minimum_protocol_version = "TLSv1.2_2021"
}
aliases = ["skills.example.com"]
```

4. Create a Route 53 ALIAS record pointing to the CloudFront domain.

#### CORS update

Once deployed, update `app/main.py` to restrict CORS origins to your CloudFront
domain instead of `*`:

```python
allow_origins=["https://d1234abcd.cloudfront.net", "https://skills.example.com"],
```

Rebuild and redeploy the Lambda package after this change.

---

## Cost Estimate (Small Team: 5–20 Employees, Light Use)

| Service                   | Config                                | Est. Monthly Cost (USD) |
|---------------------------|---------------------------------------|-------------------------|
| RDS PostgreSQL            | db.t3.micro, 20 GB gp3, single-AZ    | ~$15–18                 |
| Lambda                    | 512 MB, ~10k req/month, 1s avg        | < $1 (within free tier) |
| API Gateway HTTP API      | ~10k requests/month                   | < $0.02                 |
| S3 (frontend storage)     | < 10 MB, < 1k GET/month               | < $0.01                 |
| CloudFront                | < 1 GB transfer/month                 | < $0.10                 |
| Secrets Manager           | 1 secret                              | $0.40                   |
| NAT Gateway               | 1 AZ, minimal data                    | ~$32 (dominates!)       |
| **Total**                 |                                       | **~$48–52/month**       |

> **Cost saving tip**: The NAT Gateway (~$32/month) dominates costs for this small
> deployment. To avoid it, configure Lambda without VPC access and use RDS Proxy or
> an RDS publicly accessible instance (secured by security group + SSL). For production
> use, keep the NAT Gateway for proper network isolation.

---

## Rollback Plan

### Lambda rollback

Lambda versions are published automatically on each deploy. To roll back:

```bash
# List versions
aws lambda list-versions-by-function --function-name skills-app-dev-api

# Point the alias back to the previous version
aws lambda update-alias \
  --function-name skills-app-dev-api \
  --name live \
  --function-version 2   # replace with the version number to restore
```

### Database rollback

RDS automated backups are retained for 7 days by default. To restore:

```bash
# Restore to a point in time via AWS Console:
# RDS → Databases → skills-app-dev → Actions → Restore to point in time

# Or via CLI:
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier skills-app-dev \
  --target-db-instance-identifier skills-app-dev-restored \
  --restore-time 2024-01-15T12:00:00Z
```

### Frontend rollback

The old frontend files remain in the local repository. Re-run `deploy_frontend.sh`
from the previous git tag/commit.

### Full environment rollback

```bash
cd aws-migration/terraform
terraform destroy   # tears down all AWS resources
# Restart local stack:
cd ../../
docker-compose up -d
```
