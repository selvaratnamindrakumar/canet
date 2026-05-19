# Backend — Employee Skills API

FastAPI REST API for the Employee Skills tracking application.

## Prerequisites

- Python 3.11 or higher
- PostgreSQL 14 or higher (running and accessible)

## Setup

```bash
# 1. Create and activate a virtual environment
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate

# 2. Install dependencies
pip install -r requirements.txt

# 3. Configure environment variables
cp .env.example .env
# Edit .env and set DATABASE_URL to match your PostgreSQL connection
```

### .env example

```
DATABASE_URL=postgresql://postgres:password@localhost:5432/skills_db
APP_ENV=development
LOG_LEVEL=INFO
```

## Run the API

```bash
uvicorn app.main:app --reload
```

The API will be available at **http://localhost:8000**.

## API Documentation

Interactive Swagger UI: **http://localhost:8000/docs**

ReDoc: **http://localhost:8000/redoc**

## Run Tests

Tests use an SQLite in-memory database — no PostgreSQL required.

```bash
pytest tests/ -v
```

## Project Structure

```
backend/
├── app/
│   ├── main.py          # FastAPI app, CORS, router registration
│   ├── config.py        # Settings (pydantic-settings)
│   ├── database.py      # SQLAlchemy engine, session, Base
│   ├── models.py        # ORM models
│   ├── schemas.py       # Pydantic request/response schemas
│   └── routers/
│       ├── employees.py # /employees endpoints
│       └── skills.py    # /skills endpoints (matrix, search, levels)
└── tests/
    ├── conftest.py      # pytest fixtures, in-memory DB setup
    ├── test_employees.py
    └── test_skills.py
```
