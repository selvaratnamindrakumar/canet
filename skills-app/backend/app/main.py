import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import employees, skills

# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Employee Skills API",
    description="REST API for tracking employee skills and experience levels",
    version="1.0.0",
)

# CORS — allow all origins for development convenience
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(employees.router)
app.include_router(skills.router)


@app.get("/", tags=["root"])
def root():
    return {"message": "Employee Skills API", "docs": "/docs"}
