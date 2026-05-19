from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app import models, schemas

router = APIRouter(prefix="/employees", tags=["employees"])


@router.get("/", response_model=List[schemas.EmployeeOut])
def list_employees(db: Session = Depends(get_db)):
    """List all active employees with their department."""
    employees = (
        db.query(models.Employee)
        .options(joinedload(models.Employee.department))
        .filter(models.Employee.is_active == True)
        .order_by(models.Employee.last_name, models.Employee.first_name)
        .all()
    )
    return employees


@router.get("/{employee_id}", response_model=schemas.EmployeeOut)
def get_employee(employee_id: int, db: Session = Depends(get_db)):
    """Get a single employee by ID."""
    employee = (
        db.query(models.Employee)
        .options(joinedload(models.Employee.department))
        .filter(models.Employee.id == employee_id)
        .first()
    )
    if not employee:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Employee not found")
    return employee


@router.post("/", response_model=schemas.EmployeeOut, status_code=status.HTTP_201_CREATED)
def create_employee(payload: schemas.EmployeeCreate, db: Session = Depends(get_db)):
    """Create a new employee."""
    # Check for duplicate email / employee_code
    existing = db.query(models.Employee).filter(
        (models.Employee.email == payload.email) |
        (models.Employee.employee_code == payload.employee_code)
    ).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="An employee with this email or employee code already exists"
        )

    employee = models.Employee(**payload.model_dump())
    db.add(employee)
    db.commit()
    db.refresh(employee)

    # Reload with relationships
    return get_employee(employee.id, db)


@router.put("/{employee_id}", response_model=schemas.EmployeeOut)
def update_employee(
    employee_id: int,
    payload: schemas.EmployeeUpdate,
    db: Session = Depends(get_db)
):
    """Update an employee record."""
    employee = db.query(models.Employee).filter(models.Employee.id == employee_id).first()
    if not employee:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Employee not found")

    update_data = payload.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(employee, field, value)

    db.commit()
    db.refresh(employee)
    return get_employee(employee_id, db)


@router.get("/{employee_id}/skills", response_model=List[schemas.EmployeeSkillOut])
def get_employee_skills(employee_id: int, db: Session = Depends(get_db)):
    """Get all skill ratings for an employee."""
    employee = db.query(models.Employee).filter(models.Employee.id == employee_id).first()
    if not employee:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Employee not found")

    employee_skills = (
        db.query(models.EmployeeSkill)
        .options(
            joinedload(models.EmployeeSkill.skill).joinedload(models.Skill.category),
            joinedload(models.EmployeeSkill.experience_level),
        )
        .filter(models.EmployeeSkill.employee_id == employee_id)
        .all()
    )
    return employee_skills


@router.post("/{employee_id}/skills", response_model=schemas.EmployeeSkillOut)
def add_or_update_employee_skill(
    employee_id: int,
    payload: schemas.EmployeeSkillUpdate,
    db: Session = Depends(get_db)
):
    """Add or update a skill rating for an employee (upsert)."""
    employee = db.query(models.Employee).filter(models.Employee.id == employee_id).first()
    if not employee:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Employee not found")

    skill = db.query(models.Skill).filter(models.Skill.id == payload.skill_id).first()
    if not skill:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Skill not found")

    level = db.query(models.ExperienceLevel).filter(
        models.ExperienceLevel.id == payload.experience_level_id
    ).first()
    if not level:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Experience level not found")

    # Upsert
    existing = (
        db.query(models.EmployeeSkill)
        .filter(
            models.EmployeeSkill.employee_id == employee_id,
            models.EmployeeSkill.skill_id == payload.skill_id,
        )
        .first()
    )

    if existing:
        existing.experience_level_id = payload.experience_level_id
        existing.notes = payload.notes
        db.commit()
        db.refresh(existing)
        record = existing
    else:
        record = models.EmployeeSkill(
            employee_id=employee_id,
            skill_id=payload.skill_id,
            experience_level_id=payload.experience_level_id,
            notes=payload.notes,
        )
        db.add(record)
        db.commit()
        db.refresh(record)

    # Reload with relationships
    return (
        db.query(models.EmployeeSkill)
        .options(
            joinedload(models.EmployeeSkill.skill).joinedload(models.Skill.category),
            joinedload(models.EmployeeSkill.experience_level),
        )
        .filter(models.EmployeeSkill.id == record.id)
        .first()
    )


@router.delete("/{employee_id}/skills/{skill_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_employee_skill(employee_id: int, skill_id: int, db: Session = Depends(get_db)):
    """Remove a skill rating from an employee."""
    record = (
        db.query(models.EmployeeSkill)
        .filter(
            models.EmployeeSkill.employee_id == employee_id,
            models.EmployeeSkill.skill_id == skill_id,
        )
        .first()
    )
    if not record:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Skill rating not found")

    db.delete(record)
    db.commit()
