"""Tests for /skills endpoints."""
import pytest


def test_list_skills(client):
    """GET /skills returns 200 and a list of skills."""
    response = client.get("/skills/")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_list_skills_filtered_by_category(client):
    """GET /skills?category_id=1 returns only skills from that category."""
    response = client.get("/skills/?category_id=1")
    assert response.status_code == 200
    data = response.json()
    assert all(s["category_id"] == 1 for s in data)


def test_list_categories(client):
    """GET /skills/categories returns 200 and a list of categories."""
    response = client.get("/skills/categories")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1
    assert any(c["name"] == "Programming Languages" for c in data)


def test_list_levels(client):
    """GET /skills/levels returns 200 with all 4 experience levels."""
    response = client.get("/skills/levels")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) == 4
    codes = {lvl["code"] for lvl in data}
    assert codes == {"NONE", "INITIAL", "OK", "GOOD"}


def test_skills_matrix(client):
    """GET /skills/matrix returns 200 with a list of rows containing 'employee' and 'skills' keys."""
    response = client.get("/skills/matrix")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1
    row = data[0]
    assert "employee" in row
    assert "skills" in row


def test_skills_matrix_department_filter(client):
    """GET /skills/matrix?department_id=1 returns only employees in that department."""
    response = client.get("/skills/matrix?department_id=1")
    assert response.status_code == 200
    data = response.json()
    for row in data:
        assert row["employee"]["department_id"] == 1


def test_search_employees(client):
    """POST /skills/search returns 200 with matching employees."""
    payload = {"skill_ids": [1], "min_level_id": 3}
    response = client.post("/skills/search", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    # Alice has Python at level 4 (GOOD) which satisfies min_level_id=3
    employee_ids = [row["employee"]["id"] for row in data]
    assert 1 in employee_ids


def test_search_employees_no_filter(client):
    """POST /skills/search with empty filter returns all active employees."""
    payload = {"skill_ids": [], "min_level_id": 1}
    response = client.post("/skills/search", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1
