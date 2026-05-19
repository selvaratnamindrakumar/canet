"""
pytest fixtures — use SQLite in-memory so tests run without a real PostgreSQL instance.
"""
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app import models

SQLITE_URL = "sqlite:///:memory:"

engine = create_engine(
    SQLITE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


@pytest.fixture(scope="session", autouse=True)
def setup_database():
    """Create all tables once for the test session."""
    Base.metadata.create_all(bind=engine)
    _seed_data()
    yield
    Base.metadata.drop_all(bind=engine)


def _seed_data():
    db = TestingSessionLocal()
    try:
        # Experience levels (required by foreign keys)
        levels = [
            models.ExperienceLevel(id=1, code="NONE",    label="None",    colour_hex="#FFFFFF", display_order=1),
            models.ExperienceLevel(id=2, code="INITIAL", label="Initial", colour_hex="#FF8C00", display_order=2),
            models.ExperienceLevel(id=3, code="OK",      label="OK",      colour_hex="#FFD700", display_order=3),
            models.ExperienceLevel(id=4, code="GOOD",    label="Good",    colour_hex="#28A745", display_order=4),
        ]
        db.add_all(levels)
        db.flush()

        # Department
        dept = models.Department(id=1, name="Engineering")
        db.add(dept)
        db.flush()

        # Employees
        employees = [
            models.Employee(
                id=1,
                employee_code="EMP001",
                first_name="Alice",
                last_name="Johnson",
                email="alice@test.com",
                department_id=1,
                job_title="Senior Engineer",
                is_active=True,
            ),
            models.Employee(
                id=2,
                employee_code="EMP002",
                first_name="Bob",
                last_name="Smith",
                email="bob@test.com",
                department_id=1,
                job_title="Engineer",
                is_active=True,
            ),
        ]
        db.add_all(employees)
        db.flush()

        # Skill categories
        categories = [
            models.SkillCategory(id=1, name="Programming Languages", description="Languages"),
            models.SkillCategory(id=2, name="Databases", description="DB tech"),
        ]
        db.add_all(categories)
        db.flush()

        # Skills
        skills = [
            models.Skill(id=1, category_id=1, name="Python",     description="Python lang"),
            models.Skill(id=2, category_id=1, name="JavaScript", description="JS lang"),
            models.Skill(id=3, category_id=2, name="PostgreSQL", description="Postgres DB"),
        ]
        db.add_all(skills)
        db.flush()

        # Employee skill ratings
        ratings = [
            models.EmployeeSkill(employee_id=1, skill_id=1, experience_level_id=4, notes="Expert"),
            models.EmployeeSkill(employee_id=1, skill_id=3, experience_level_id=3, notes=None),
        ]
        db.add_all(ratings)
        db.commit()
    finally:
        db.close()


@pytest.fixture()
def client():
    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()
