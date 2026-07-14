import json
import os
import sys
from decimal import Decimal

import boto3
import pytest
from moto import mock_aws

ARRIVALS_BUCKET = "sandbox-arrivals-000000000000-us-east-1"
QUARANTINE_BUCKET = "sandbox-quarantine-000000000000-us-east-1"
TABLE_NAME = "sandbox-records"

SRC_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "process_arrivals")


@pytest.fixture
def app_module(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-1")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "testing")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "testing")
    monkeypatch.setenv("TABLE_NAME", TABLE_NAME)
    monkeypatch.setenv("QUARANTINE_BUCKET", QUARANTINE_BUCKET)

    with mock_aws():
        s3 = boto3.client("s3", region_name="us-east-1")
        s3.create_bucket(Bucket=ARRIVALS_BUCKET)
        s3.create_bucket(Bucket=QUARANTINE_BUCKET)

        ddb_client = boto3.client("dynamodb", region_name="us-east-1")
        ddb_client.create_table(
            TableName=TABLE_NAME,
            AttributeDefinitions=[{"AttributeName": "id", "AttributeType": "S"}],
            KeySchema=[{"AttributeName": "id", "KeyType": "HASH"}],
            BillingMode="PAY_PER_REQUEST",
        )
        table = boto3.resource("dynamodb", region_name="us-east-1").Table(TABLE_NAME)

        sys.path.insert(0, SRC_DIR)
        sys.modules.pop("app", None)
        import app  # noqa: E402 - must import inside the mocked AWS context

        yield s3, table, app

        sys.modules.pop("app", None)
        sys.path.remove(SRC_DIR)


def _s3_event(key):
    return {"Records": [{"s3": {"bucket": {"name": ARRIVALS_BUCKET}, "object": {"key": key}}}]}


def test_valid_records_with_decimal_amounts_are_stored(app_module):
    s3, table, app = app_module
    body = json.dumps([
        {"id": "order-1001", "type": "ORDER", "amount": 42.50, "customer": "acme-co"},
        {"id": "order-1002", "type": "ORDER", "amount": 17.00, "customer": "wonka-inc"},
    ])
    s3.put_object(Bucket=ARRIVALS_BUCKET, Key="sample-valid.json", Body=body)

    app.handler(_s3_event("sample-valid.json"), None)

    item1 = table.get_item(Key={"id": "order-1001"})["Item"]
    item2 = table.get_item(Key={"id": "order-1002"})["Item"]
    assert item1["amount"] == Decimal("42.50")
    assert item1["type"] == "order"
    assert item2["amount"] == Decimal("17.00")

    quarantined = s3.list_objects_v2(Bucket=QUARANTINE_BUCKET).get("Contents", [])
    assert quarantined == []


def test_record_missing_id_is_quarantined_not_stored(app_module):
    s3, table, app = app_module
    body = json.dumps([{"type": "ORDER", "amount": -1, "customer": "missing-id"}])
    s3.put_object(Bucket=ARRIVALS_BUCKET, Key="sample-invalid.json", Body=body)

    app.handler(_s3_event("sample-invalid.json"), None)

    assert "Item" not in table.get_item(Key={"id": "missing-id"})
    quarantined = s3.list_objects_v2(Bucket=QUARANTINE_BUCKET).get("Contents", [])
    assert any(o["Key"] == "sample-invalid.json.error" for o in quarantined)
