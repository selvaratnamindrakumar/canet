"""
handler.py — AWS Lambda entry point for the Employee Skills FastAPI application.

This module:
1. Loads database credentials from AWS Secrets Manager at cold start.
2. Sets DATABASE_URL as an environment variable so SQLAlchemy picks it up.
3. Wraps the FastAPI `app` with Mangum so Lambda event/context maps to ASGI.
"""

import json
import os
import sys

# Ensure the Lambda package root is on sys.path so `app` is importable.
sys.path.insert(0, os.path.dirname(__file__))

import boto3


def _load_db_secret() -> None:
    """Fetch DB credentials from Secrets Manager and write DATABASE_URL to env.

    Called once per cold start. Subsequent invocations reuse the already-set
    environment variable (the container / execution environment is reused).
    """
    secret_arn = os.environ.get("DB_SECRET_ARN")
    if not secret_arn:
        # Allow local testing without Secrets Manager by setting DATABASE_URL directly.
        return

    region = os.environ.get("AWS_REGION", os.environ.get("AWS_REGION_NAME", "eu-west-1"))

    client = boto3.client("secretsmanager", region_name=region)
    response = client.get_secret_value(SecretId=secret_arn)
    secret = json.loads(response["SecretString"])

    host = secret["host"]
    port = secret.get("port", 5432)
    dbname = secret["dbname"]
    username = secret["username"]
    password = secret["password"]

    # psycopg2 / asyncpg compatible URL
    os.environ["DATABASE_URL"] = (
        f"postgresql://{username}:{password}@{host}:{port}/{dbname}"
    )


# Cold-start: load credentials before importing the app (which opens the DB engine)
_load_db_secret()

# Import FastAPI application — must happen after DATABASE_URL is set
from app.main import app  # noqa: E402

from mangum import Mangum  # noqa: E402

# Mangum translates API Gateway HTTP API (payload format 2.0) events to ASGI.
# lifespan="off" avoids issues with startup/shutdown events in Lambda.
handler = Mangum(app, lifespan="off")
