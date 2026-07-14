import json
import os
import time
import urllib.parse
from decimal import Decimal

import boto3

s3 = boto3.client('s3')
ddb = boto3.resource('dynamodb')
cw = boto3.client('cloudwatch')

TABLE = ddb.Table(os.environ['TABLE_NAME'])
QUARANTINE_BUCKET = os.environ['QUARANTINE_BUCKET']
NAMESPACE = 'FileIngestionPipeline'


def emit(name, value=1):
    cw.put_metric_data(
        Namespace=NAMESPACE,
        MetricData=[{'MetricName': name, 'Value': value, 'Unit': 'Count'}],
    )


def quarantine(source_bucket, source_key, body, reason):
    dest_key = f"{source_key}.error"
    payload = {
        'source_bucket': source_bucket,
        'source_key': source_key,
        'reason': reason,
        'body': body,
    }
    s3.put_object(
        Bucket=QUARANTINE_BUCKET,
        Key=dest_key,
        Body=json.dumps(payload, default=str).encode('utf-8'),
    )
    emit('RecordsQuarantined')


def handler(event, context):
    for rec in event['Records']:
        bucket = rec['s3']['bucket']['name']
        key = urllib.parse.unquote_plus(rec['s3']['object']['key'])
        print(f"Processing s3://{bucket}/{key}")

        try:
            obj = s3.get_object(Bucket=bucket, Key=key)
            raw = obj['Body'].read()
        except Exception as e:
            quarantine(bucket, key, '', f'failed to read object: {e}')
            emit('ProcessingErrors')
            continue

        try:
            # DynamoDB's boto3 resource rejects native Python floats, so
            # numeric fields must come in as Decimal instead.
            records = json.loads(raw, parse_float=Decimal)
            if isinstance(records, dict):
                records = [records]
        except Exception as e:
            quarantine(bucket, key, raw.decode('utf-8', 'replace'), f'invalid json: {e}')
            emit('ProcessingErrors')
            continue

        valid_count = 0
        for r in records:
            rid = r.get('id') if isinstance(r, dict) else None
            if not rid or not isinstance(rid, str):
                quarantine(bucket, key, r, 'missing or invalid id field')
                continue

            item = dict(r)
            if isinstance(item.get('type'), str):
                item['type'] = item['type'].lower()
            item['ingested_at'] = int(time.time())

            try:
                TABLE.put_item(Item=item)
                valid_count += 1
                print(f"Stored DynamoDB item id={rid}")
            except Exception as e:
                quarantine(bucket, key, r, f'dynamodb write failed: {e}')

        emit('FilesProcessed')
        emit('RecordsValid', valid_count)

    return {'statusCode': 200}
