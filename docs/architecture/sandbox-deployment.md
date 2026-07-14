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

## Why AWS SAM instead of a hand-written CloudFormation template

The pipeline was originally deployed as a single plain-CloudFormation
template with the Lambda source inlined via `ZipFile`. That approach hit two
real problems in testing:

1. **Editing the Lambda meant editing YAML.** Every code fix required
   patching an inline string block, redeploying the whole stack, and hoping
   the diff was right — there was no way to unit test the handler in
   isolation first.
2. **A DynamoDB write bug shipped invisibly.** `json.loads()` parses JSON
   numbers as Python `float`, but boto3's DynamoDB resource only accepts
   `int`/`Decimal` for Number attributes. The Lambda's own `except Exception`
   around the `put_item` call quarantined every record and logged a
   `REPORT`/success line — so CloudWatch showed a healthy invocation while
   silently dropping every record into quarantine.

This directory now uses **[AWS SAM](https://docs.aws.amazon.com/serverless-application-model/)**
instead, which is a thin, official transform on top of CloudFormation (not a
different platform) and fixes both problems structurally:

* `CodeUri: src/process_arrivals/` points at a real `.py` file, so the
  handler can be unit tested locally with `pytest` (see below) before it's
  ever deployed, and diffs are normal Python diffs.
* The `Events: { Type: S3, ... }` block on the function generates the S3
  bucket notification *and* the Lambda invoke permission automatically via a
  managed custom resource — no manual `AWS::Lambda::Permission` +
  `DependsOn` ordering to get wrong.
* `sam build && sam deploy` is one command; there's no need for local backup
  scripts or copying template variants around.

The handler itself is also fixed: it now parses JSON with
`json.loads(raw, parse_float=Decimal)`, so numeric fields land in DynamoDB
correctly instead of being silently quarantined.

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
* CloudWatch: the Lambda's default log group, custom metrics in the
  `FileIngestionPipeline` namespace (`FilesProcessed`, `RecordsValid`,
  `RecordsQuarantined`, `ProcessingErrors`), and an alarm on the Lambda's
  built-in `Errors` metric that publishes to an SNS topic.

## Prerequisites

* [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
* Python 3.12 (for local testing; the Lambda runtime is pinned separately)
* AWS credentials for the sandbox account (e.g. an SSO profile)

## If you previously deployed the plain-CloudFormation stack

That template and this one use the same bucket-naming convention
(`<env>-arrivals-<account>-<region>`), so redeploying under the old stack
name can conflict with buckets the old stack still owns. Since this is a
sandbox, the simplest path is to delete the old stack first:

```bash
aws s3 rm s3://sandbox-arrivals-<account>-<region> --recursive
aws s3 rm s3://sandbox-quarantine-<account>-<region> --recursive
aws cloudformation delete-stack --stack-name file-ingestion-sandbox
aws cloudformation wait stack-delete-complete --stack-name file-ingestion-sandbox
```

Then deploy the SAM stack fresh with the instructions below (the same stack
name is fine to reuse once the old stack is gone).

## Test locally first (no AWS required)

```bash
cd infra/sam
pip install -r tests/requirements.txt
pytest tests/ -v
```

This runs the handler against a mocked S3/DynamoDB/CloudWatch (via `moto`)
using the same `sample-valid.json` / `sample-invalid.json` fixtures used
against real AWS, and asserts that:

* `order-1001` / `order-1002` are stored with `Decimal` amounts intact.
* the record missing `id` is quarantined and never written to DynamoDB.

You can also invoke the handler through the SAM CLI itself, which runs it in
a container using the real Lambda runtime:

```bash
sam build
sam local invoke ProcessArrivalsFunction --event events/s3-put-event.json \
  --env-vars <(echo '{"ProcessArrivalsFunction":{"TABLE_NAME":"sandbox-records","QUARANTINE_BUCKET":"sandbox-quarantine-000000000000-us-east-1"}}')
```

(`sam local invoke` still talks to real AWS for S3/DynamoDB/CloudWatch calls
unless you point it at local endpoints — the `pytest` route above is the
faster, fully-offline option.)

## Deploy to the sandbox account

```bash
cd infra/sam
sam build
sam deploy --guided \
  --stack-name file-ingestion-sandbox \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides EnvironmentName=sandbox AlarmEmail=you@example.com
```

`sam deploy --guided` walks through picking a region, an S3 bucket for
packaged artifacts, and saves the answers to `samconfig.toml` so subsequent
deploys are just `sam build && sam deploy`. `AlarmEmail` is optional — omit
it to skip the SNS email subscription.

## Try it against real AWS

```bash
ARRIVALS_BUCKET=$(aws cloudformation describe-stacks \
  --stack-name file-ingestion-sandbox \
  --query "Stacks[0].Outputs[?OutputKey=='ArrivalsBucketName'].OutputValue" \
  --output text)

# Valid file - both records should land in DynamoDB.
aws s3 cp ../../examples/sample-valid.json "s3://${ARRIVALS_BUCKET}/sample-valid.json"

# Mixed file - the record missing "id" should be quarantined.
aws s3 cp ../../examples/sample-invalid.json "s3://${ARRIVALS_BUCKET}/sample-invalid.json"
```

Check the results:

```bash
aws dynamodb scan --table-name sandbox-records

QUARANTINE_BUCKET=$(aws cloudformation describe-stacks \
  --stack-name file-ingestion-sandbox \
  --query "Stacks[0].Outputs[?OutputKey=='QuarantineBucketName'].OutputValue" \
  --output text)
aws s3 ls "s3://${QUARANTINE_BUCKET}/"

sam logs --stack-name file-ingestion-sandbox --name ProcessArrivalsFunction --tail
```

If you re-upload `sample-valid.json` under the same key it will overwrite
the object and re-trigger the Lambda, but `order-1001`/`order-1002` are
fixed DynamoDB partition keys, so re-running upserts those two items rather
than growing the table. Upload under a new key (e.g. with a timestamp) if
you want to test throughput rather than idempotency.

## Tear down

```bash
aws s3 rm "s3://${ARRIVALS_BUCKET}" --recursive
aws s3 rm "s3://${QUARANTINE_BUCKET}" --recursive
sam delete --stack-name file-ingestion-sandbox
```

S3 buckets must be emptied before the stack delete will succeed, since the
template does not set `DeletionPolicy: Retain` on them.
