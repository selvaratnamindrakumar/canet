@echo off
REM =============================================================================
REM docker-build.bat — build the smb-csv-processor Docker image with the version
REM extracted from pom.xml.
REM
REM Usage:
REM   docker-build.bat              -- build and tag as both <version> and latest
REM   docker-build.bat --no-latest  -- tag as <version> only
REM   docker-build.bat --push       -- push to registry after build
REM =============================================================================
setlocal EnableDelayedExpansion

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

set TAG_LATEST=true
set PUSH=false

REM Parse arguments
:parse_args
if "%~1"=="" goto :done_args
if /i "%~1"=="--no-latest" set TAG_LATEST=false
if /i "%~1"=="--push"      set PUSH=true
shift
goto :parse_args
:done_args

REM ---------------------------------------------------------------------------
REM Extract version from pom.xml using Maven (preferred) or Python fallback
REM ---------------------------------------------------------------------------
where mvn >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2^>nul') do (
        set APP_VERSION=%%v
    )
) else (
    where python >nul 2>&1
    if %ERRORLEVEL% == 0 (
        for /f "tokens=*" %%v in ('python -c "import xml.etree.ElementTree as ET; ns={\"m\":\"http://maven.apache.org/POM/4.0.0\"}; root=ET.parse(\"pom.xml\").getroot(); print(root.find(\"m:version\",ns).text)"') do (
            set APP_VERSION=%%v
        )
    ) else (
        echo ERROR: mvn or python is required to read the version from pom.xml
        exit /b 1
    )
)

if "!APP_VERSION!"=="" (
    echo ERROR: Could not determine APP_VERSION from pom.xml
    exit /b 1
)

set IMAGE_NAME=smb-csv-processor

echo Building Docker image: %IMAGE_NAME%:!APP_VERSION!
docker build --build-arg APP_VERSION=!APP_VERSION! -t %IMAGE_NAME%:!APP_VERSION! .
if %ERRORLEVEL% neq 0 (
    echo ERROR: docker build failed
    exit /b 1
)

if "%TAG_LATEST%"=="true" (
    echo Tagging as: %IMAGE_NAME%:latest
    docker tag %IMAGE_NAME%:!APP_VERSION! %IMAGE_NAME%:latest
)

if "%PUSH%"=="true" (
    echo Pushing: %IMAGE_NAME%:!APP_VERSION!
    docker push %IMAGE_NAME%:!APP_VERSION!
    if "%TAG_LATEST%"=="true" (
        docker push %IMAGE_NAME%:latest
    )
)

echo.
echo Done. Image: %IMAGE_NAME%:!APP_VERSION!
endlocal
