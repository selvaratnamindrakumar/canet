# Database Setup

This directory contains the PostgreSQL schema and sample data for the Employee Skills Tracking application.

## Prerequisites

- PostgreSQL 14 or higher installed and running

### Install PostgreSQL

**Ubuntu/Debian:**
```bash
sudo apt update && sudo apt install postgresql postgresql-client
sudo systemctl start postgresql
```

**macOS (Homebrew):**
```bash
brew install postgresql@16
brew services start postgresql@16
```

**Docker:**
```bash
docker run -d --name postgres -e POSTGRES_PASSWORD=password -p 5432:5432 postgres:16-alpine
```

## Create the Database

```bash
createdb skills_db
```

Or, if you need to specify a user:
```bash
createdb -U postgres skills_db
```

## Run the Schema

```bash
psql -d skills_db -f 01_schema.sql
```

## Load Sample Data

```bash
psql -d skills_db -f 02_sample_data.sql
```

## Experience Levels

| Level   | Colour | Hex Code  | Description                        |
|---------|--------|-----------|------------------------------------|
| None    | White  | `#FFFFFF` | No experience with this skill      |
| Initial | Orange | `#FF8C00` | Just starting out / aware of skill |
| OK      | Yellow | `#FFD700` | Competent, can work independently  |
| Good    | Green  | `#28A745` | Expert level, can mentor others    |

## Validation Queries

After loading the data, run these to verify everything is set up correctly:

```sql
-- Count of employees per department
SELECT d.name AS department, COUNT(e.id) AS employee_count
FROM departments d
LEFT JOIN employees e ON e.department_id = d.id
GROUP BY d.name
ORDER BY d.name;

-- Skills matrix overview: employee + number of skills rated
SELECT e.first_name || ' ' || e.last_name AS employee,
       COUNT(es.id) AS skills_rated
FROM employees e
LEFT JOIN employee_skills es ON es.employee_id = e.id
GROUP BY e.id, e.first_name, e.last_name
ORDER BY skills_rated DESC;

-- Skills by experience level distribution
SELECT el.label, el.colour_hex, COUNT(es.id) AS count
FROM experience_levels el
LEFT JOIN employee_skills es ON es.experience_level_id = el.id
GROUP BY el.id, el.label, el.colour_hex, el.display_order
ORDER BY el.display_order;

-- Top skills across all employees
SELECT s.name AS skill, sc.name AS category, COUNT(es.id) AS num_employees
FROM skills s
JOIN skill_categories sc ON sc.id = s.category_id
LEFT JOIN employee_skills es ON es.skill_id = s.id
GROUP BY s.id, s.name, sc.name
ORDER BY num_employees DESC
LIMIT 10;
```
