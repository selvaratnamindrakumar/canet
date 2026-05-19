# Employee Skills Tracker

A full-stack application for tracking employee skills and experience levels, visualised as a colour-coded matrix.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     Browser                         │
│   index.html  ·  employees.html  ·  employee_detail │
│            (Vanilla JS / ES modules)                │
└────────────────────────┬────────────────────────────┘
                         │ HTTP / REST
                         ▼
┌─────────────────────────────────────────────────────┐
│              FastAPI Backend  :8000                 │
│  /employees  ·  /skills  ·  /skills/matrix          │
│              SQLAlchemy ORM                         │
└────────────────────────┬────────────────────────────┘
                         │ SQL
                         ▼
┌─────────────────────────────────────────────────────┐
│           PostgreSQL 16  :5432  (skills_db)         │
│  departments · employees · skills · employee_skills  │
└─────────────────────────────────────────────────────┘
```

## Quick Start with Docker Compose

```bash
# 1. Clone / navigate to this directory
cd skills-app

# 2. Start PostgreSQL and the API
docker compose up --build

# 3. Open the frontend
cd frontend && python -m http.server 3000
# Then visit http://localhost:3000
```

Docker Compose automatically creates the database schema and loads sample data on first start.

## Manual Setup

### 1. Database

```bash
# Install and start PostgreSQL, then:
createdb skills_db
psql -d skills_db -f database/01_schema.sql
psql -d skills_db -f database/02_sample_data.sql
```

### 2. Backend

```bash
cd backend
python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env            # Edit DATABASE_URL if needed
uvicorn app.main:app --reload
```

API available at **http://localhost:8000** — interactive docs at **http://localhost:8000/docs**.

### 3. Frontend

```bash
cd frontend
python -m http.server 3000
# Visit http://localhost:3000
```

## Experience Level Colour Matrix

| Level   | Colour | Hex       | Meaning                                 |
|---------|--------|-----------|-----------------------------------------|
| None    | White  | `#FFFFFF` | No experience recorded                  |
| Initial | Orange | `#FF8C00` | Aware of / just started learning skill  |
| OK      | Yellow | `#FFD700` | Competent, can work independently       |
| Good    | Green  | `#28A745` | Expert level, able to mentor others     |

## API Endpoints

| Method | Path                               | Description                          |
|--------|------------------------------------|--------------------------------------|
| GET    | `/`                                | Health check / welcome               |
| GET    | `/employees/`                      | List all active employees            |
| GET    | `/employees/{id}`                  | Get employee detail                  |
| POST   | `/employees/`                      | Create employee                      |
| PUT    | `/employees/{id}`                  | Update employee                      |
| GET    | `/employees/{id}/skills`           | Get employee's skill ratings         |
| POST   | `/employees/{id}/skills`           | Add / update a skill rating (upsert) |
| DELETE | `/employees/{id}/skills/{skill_id}`| Remove a skill rating                |
| GET    | `/skills/`                         | List skills (optional ?category_id)  |
| GET    | `/skills/categories`               | List skill categories                |
| GET    | `/skills/levels`                   | List experience levels               |
| GET    | `/skills/matrix`                   | Full skills matrix                   |
| POST   | `/skills/search`                   | Search employees by skills           |

## Running Tests

```bash
cd backend
pytest tests/ -v
```

Tests use an SQLite in-memory database — no PostgreSQL required.
