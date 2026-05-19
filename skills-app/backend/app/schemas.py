from datetime import datetime, date
from typing import Optional, List
from pydantic import BaseModel, EmailStr


# ---------------------------------------------------------------------------
# Department
# ---------------------------------------------------------------------------

class DepartmentOut(BaseModel):
    id: int
    name: str
    created_at: datetime

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Experience Level
# ---------------------------------------------------------------------------

class ExperienceLevelOut(BaseModel):
    id: int
    code: str
    label: str
    colour_hex: str
    display_order: int

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Skill Category
# ---------------------------------------------------------------------------

class SkillCategoryOut(BaseModel):
    id: int
    name: str
    description: Optional[str] = None
    created_at: datetime

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Skill
# ---------------------------------------------------------------------------

class SkillOut(BaseModel):
    id: int
    name: str
    description: Optional[str] = None
    category_id: Optional[int] = None
    category: Optional[SkillCategoryOut] = None
    created_at: datetime

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Employee
# ---------------------------------------------------------------------------

class EmployeeBase(BaseModel):
    employee_code: str
    first_name: str
    last_name: str
    email: str
    department_id: Optional[int] = None
    job_title: Optional[str] = None
    hire_date: Optional[date] = None
    is_active: bool = True


class EmployeeCreate(EmployeeBase):
    pass


class EmployeeUpdate(BaseModel):
    employee_code: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    email: Optional[str] = None
    department_id: Optional[int] = None
    job_title: Optional[str] = None
    hire_date: Optional[date] = None
    is_active: Optional[bool] = None


class EmployeeOut(BaseModel):
    id: int
    employee_code: str
    first_name: str
    last_name: str
    email: str
    department_id: Optional[int] = None
    department: Optional[DepartmentOut] = None
    job_title: Optional[str] = None
    hire_date: Optional[date] = None
    is_active: bool
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Employee Skills
# ---------------------------------------------------------------------------

class EmployeeSkillOut(BaseModel):
    id: int
    employee_id: int
    skill_id: int
    skill: SkillOut
    experience_level_id: int
    experience_level: ExperienceLevelOut
    notes: Optional[str] = None
    assessed_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class EmployeeSkillUpdate(BaseModel):
    skill_id: int
    experience_level_id: int
    notes: Optional[str] = None


# ---------------------------------------------------------------------------
# Skills Matrix
# ---------------------------------------------------------------------------

class SkillsMatrixRow(BaseModel):
    employee: EmployeeOut
    skills: List[EmployeeSkillOut]

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Search Filter
# ---------------------------------------------------------------------------

class SearchFilter(BaseModel):
    skill_ids: List[int] = []
    min_level_id: int = 1
