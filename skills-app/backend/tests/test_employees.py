"""Tests for /employees endpoints."""
import pytest


def test_list_employees(client):
    """GET /employees returns 200 and a non-empty list."""
    response = client.get("/employees/")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_get_employee(client):
    """GET /employees/1 returns 200 with the correct employee."""
    response = client.get("/employees/1")
    assert response.status_code == 200
    data = response.json()
    assert data["id"] == 1
    assert data["employee_code"] == "EMP001"


def test_get_employee_not_found(client):
    """GET /employees/999 returns 404."""
    response = client.get("/employees/999")
    assert response.status_code == 404


def test_create_employee(client):
    """POST /employees returns 201 and the created employee."""
    payload = {
        "employee_code": "EMP099",
        "first_name": "Test",
        "last_name": "User",
        "email": "testuser@test.com",
        "department_id": 1,
        "job_title": "Tester",
        "is_active": True,
    }
    response = client.post("/employees/", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert data["employee_code"] == "EMP099"
    assert data["email"] == "testuser@test.com"


def test_update_employee(client):
    """PUT /employees/1 returns 200 with updated data."""
    payload = {"job_title": "Staff Engineer"}
    response = client.put("/employees/1", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["job_title"] == "Staff Engineer"


def test_get_employee_skills(client):
    """GET /employees/1/skills returns 200 and a list of skills."""
    response = client.get("/employees/1/skills")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_add_employee_skill(client):
    """POST /employees/2/skills adds a skill rating and returns 200."""
    payload = {
        "skill_id": 2,
        "experience_level_id": 3,
        "notes": "Competent",
    }
    response = client.post("/employees/2/skills", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["skill_id"] == 2
    assert data["experience_level_id"] == 3


def test_delete_employee_skill(client):
    """DELETE /employees/2/skills/2 removes the skill rating and returns 204."""
    # First ensure the rating exists
    client.post("/employees/2/skills", json={"skill_id": 2, "experience_level_id": 2})
    response = client.delete("/employees/2/skills/2")
    assert response.status_code == 204
