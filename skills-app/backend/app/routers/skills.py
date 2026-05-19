from typing import List, Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app import models, schemas

router = APIRouter(prefix="/skills", tags=["skills"])


@router.get("/", response_model=List[schemas.SkillOut])
def list_skills(
    category_id: Optional[int] = Query(None, description="Filter by category ID"),
    db: Session = Depends(get_db),
):
    """List all skills, optionally filtered by category."""
    query = db.query(models.Skill).options(joinedload(models.Skill.category))
    if category_id is not None:
        query = query.filter(models.Skill.category_id == category_id)
    return query.order_by(models.Skill.name).all()


@router.get("/categories", response_model=List[schemas.SkillCategoryOut])
def list_categories(db: Session = Depends(get_db)):
    """List all skill categories."""
    return db.query(models.SkillCategory).order_by(models.SkillCategory.name).all()


@router.get("/levels", response_model=List[schemas.ExperienceLevelOut])
def list_levels(db: Session = Depends(get_db)):
    """List all experience levels ordered by display_order."""
    return (
        db.query(models.ExperienceLevel)
        .order_by(models.ExperienceLevel.display_order)
        .all()
    )


@router.get("/matrix", response_model=List[schemas.SkillsMatrixRow])
def get_skills_matrix(
    department_id: Optional[int] = Query(None, description="Filter by department ID"),
    skill_ids: Optional[str] = Query(None, description="Comma-separated skill IDs to include"),
    min_level_id: Optional[int] = Query(None, description="Minimum experience level ID"),
    db: Session = Depends(get_db),
):
    """
    Return the full skills matrix (all employees x all skills).
    Supports optional filtering by department_id, skill_ids, and min_level_id.
    """
    # Parse skill_ids
    parsed_skill_ids: Optional[List[int]] = None
    if skill_ids:
        try:
            parsed_skill_ids = [int(s.strip()) for s in skill_ids.split(",") if s.strip()]
        except ValueError:
            parsed_skill_ids = None

    # Query employees
    emp_query = (
        db.query(models.Employee)
        .options(joinedload(models.Employee.department))
        .filter(models.Employee.is_active == True)
    )
    if department_id is not None:
        emp_query = emp_query.filter(models.Employee.department_id == department_id)

    # If filtering by skills / level, narrow employees down
    if parsed_skill_ids or min_level_id is not None:
        subq = db.query(models.EmployeeSkill.employee_id)
        if parsed_skill_ids:
            subq = subq.filter(models.EmployeeSkill.skill_id.in_(parsed_skill_ids))
        if min_level_id is not None:
            subq = subq.filter(models.EmployeeSkill.experience_level_id >= min_level_id)
        subq = subq.distinct()
        emp_query = emp_query.filter(models.Employee.id.in_(subq))

    employees = emp_query.order_by(models.Employee.last_name, models.Employee.first_name).all()

    # For each employee, load their skills
    rows: List[schemas.SkillsMatrixRow] = []
    for emp in employees:
        skills_query = (
            db.query(models.EmployeeSkill)
            .options(
                joinedload(models.EmployeeSkill.skill).joinedload(models.Skill.category),
                joinedload(models.EmployeeSkill.experience_level),
            )
            .filter(models.EmployeeSkill.employee_id == emp.id)
        )
        if parsed_skill_ids:
            skills_query = skills_query.filter(
                models.EmployeeSkill.skill_id.in_(parsed_skill_ids)
            )
        if min_level_id is not None:
            skills_query = skills_query.filter(
                models.EmployeeSkill.experience_level_id >= min_level_id
            )
        emp_skills = skills_query.all()
        rows.append(schemas.SkillsMatrixRow(employee=emp, skills=emp_skills))

    return rows


@router.post("/search", response_model=List[schemas.SkillsMatrixRow])
def search_employees(payload: schemas.SearchFilter, db: Session = Depends(get_db)):
    """
    Search employees by required skills and minimum experience level.
    Returns employees who have all specified skills at or above min_level_id.
    """
    emp_query = (
        db.query(models.Employee)
        .options(joinedload(models.Employee.department))
        .filter(models.Employee.is_active == True)
    )

    if payload.skill_ids:
        # Employee must have each required skill at or above min_level_id
        for skill_id in payload.skill_ids:
            subq = (
                db.query(models.EmployeeSkill.employee_id)
                .filter(
                    models.EmployeeSkill.skill_id == skill_id,
                    models.EmployeeSkill.experience_level_id >= payload.min_level_id,
                )
                .distinct()
            )
            emp_query = emp_query.filter(models.Employee.id.in_(subq))

    employees = emp_query.order_by(models.Employee.last_name, models.Employee.first_name).all()

    rows: List[schemas.SkillsMatrixRow] = []
    for emp in employees:
        emp_skills = (
            db.query(models.EmployeeSkill)
            .options(
                joinedload(models.EmployeeSkill.skill).joinedload(models.Skill.category),
                joinedload(models.EmployeeSkill.experience_level),
            )
            .filter(models.EmployeeSkill.employee_id == emp.id)
            .all()
        )
        rows.append(schemas.SkillsMatrixRow(employee=emp, skills=emp_skills))

    return rows
