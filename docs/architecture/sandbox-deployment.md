# Sandbox Deployment

This deploys a working version of the pipeline described in
[`next-stage-design.md`](./next-stage-design.md), scoped down for a low-cost
sandbox: **S3 arrivals bucket -> Lambda -> DynamoDB**, with a quarantine
bucket and CloudWatch metrics/alarms.

Aurora PostgreSQL is intentionally left out of the sandbox stack — it needs a
VPC, subnets, and several minutes to provision. DynamoDB is used for all
records in this stage. Wiring Aurora in later just means adding an
`AWS::RDS::DBCluster` (Serverless v2) plus a VPC and giving the Lambda a
Postgres driver and connection secret — the Lambda's routing logic
(`type: pure key/value` -> DynamoDB, everything else -> Aurora) can be added
without changing the S3/quarantine/CloudWatch parts of the stack.

## What gets created

* `<env>-arrivals-<account>-<region>` S3 bucket — event notification invokes
  the Lambda on every object upload; lifecycle rules transition to IA (30d)
  / Glacier (90d) and expire objects after 180 days.
* `<env>-quarantine-<account>-<region>` S3 bucket — failed records/files land
  here as `<original-key>.error`; expires after 90 days.
* `<env>-process-arrivals` Lambda (Python 3.12) — validates each record has
  an `id`, lowercases `type` if present, stamps `ingested_at`, writes to
  DynamoDB. Anything that fails validation or the DynamoDB write is
  quarantined instead of failing the whole file.
* `<env>-records` DynamoDB table — partition key `id` (String), on-demand
  billing.
* CloudWatch: a log group for the Lambda (14-day retention), custom metrics
  in the `FileIngestionPipeline` namespace (`FilesProcessed`, `RecordsValid`,
  `RecordsQuarantined`, `ProcessingErrors`), and an alarm on the Lambda's
  built-in `Errors` metric that publishes to an SNS topic.

## Deploy

```bash
cd infra/cloudformation
./deploy.sh file-ingestion-sandbox sandbox us-east-1
```

Or directly with the AWS CLI:

```bash
aws cloudformation deploy \
  --template-file infra/cloudformation/pipeline.yaml \
  --stack-name file-ingestion-sandbox \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnvironmentName=sandbox AlarmEmail=you@example.com
```

`AlarmEmail` is optional — omit it to skip the SNS email subscription.

## Try it out

```bash
ARRIVALS_BUCKET=$(aws cloudformation describe-stacks \
  --stack-name file-ingestion-sandbox \
  --query "Stacks[0].Outputs[?OutputKey=='ArrivalsBucketName'].OutputValue" \
  --output text)

# Valid file - both records should land in DynamoDB.
aws s3 cp examples/sample-valid.json "s3://${ARRIVALS_BUCKET}/sample-valid.json"

# Mixed file - one record is missing "id" and should be quarantined.
aws s3 cp examples/sample-invalid.json "s3://${ARRIVALS_BUCKET}/sample-invalid.json"
```

Check the results:

```bash
aws dynamodb scan --table-name sandbox-records

QUARANTINE_BUCKET=$(aws cloudformation describe-stacks \
  --stack-name file-ingestion-sandbox \
  --query "Stacks[0].Outputs[?OutputKey=='QuarantineBucketName'].OutputValue" \
  --output text)
aws s3 ls "s3://${QUARANTINE_BUCKET}/"

aws logs tail /aws/lambda/sandbox-process-arrivals --follow
```

## Tear down

```bash
aws s3 rm "s3://${ARRIVALS_BUCKET}" --recursive
aws s3 rm "s3://${QUARANTINE_BUCKET}" --recursive
aws cloudformation delete-stack --stack-name file-ingestion-sandbox
```

S3 buckets must be emptied before the stack delete will succeed, since the
template does not set `DeletionPolicy: Retain` on them.
