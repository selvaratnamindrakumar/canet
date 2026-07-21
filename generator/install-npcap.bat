@echo off
:: ============================================================
:: install-npcap.bat — Install Npcap (WinPcap-compatible mode)
::
:: Run as Administrator before starting the CaNetGenerator service.
:: Npcap provides wpcap.dll which is required by Pcap4J for packet
:: capture.  Without it the generator fails with:
::   "The specified module could not be found: wpcap.dll"
::
:: Download Npcap from https://npcap.com/#download
:: Place npcap-*.exe in the same directory as this script.
::
:: Usage:
::   install-npcap.bat                   — auto-detect installer in .\
::   install-npcap.bat npcap-1.79.exe    — specify installer explicitly
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Find the installer ────────────────────────────────────────
set INSTALLER=%~1
if not "%INSTALLER%"=="" goto :check_file

:: Auto-detect — pick the first npcap-*.exe in the current directory
for %%F in (npcap-*.exe) do (
    if "!INSTALLER!"=="" set INSTALLER=%%F
)

if "!INSTALLER!"=="" (
    echo [ERROR] No Npcap installer found in %CD%
    echo         Download npcap-*.exe from https://npcap.com/#download
    echo         and place it alongside this script, or pass the path:
    echo           install-npcap.bat C:\path\to\npcap-1.79.exe
    exit /b 1
)

:check_file
if not exist "%INSTALLER%" (
    echo [ERROR] Installer not found: %INSTALLER%
    exit /b 1
)

:: ── Check if already installed ────────────────────────────────
if exist "%SystemRoot%\System32\wpcap.dll" (
    echo [INFO] wpcap.dll already present — Npcap is installed.
    echo        If you are seeing capture errors, reinstall manually:
    echo          %INSTALLER% /WinPcapCompatible /S
    goto :verify
)

:: ── Install silently ──────────────────────────────────────────
echo [INFO] Installing Npcap (WinPcap-compatible mode): %INSTALLER%
echo        This installs a kernel-level network driver.
echo        Running as Administrator is required.
echo.

"%INSTALLER%" /WinPcapCompatible /S

:: Wait for the installer to complete
timeout /t 5 /nobreak >nul

:verify
echo.
echo [INFO] Verifying installation...

set MISSING=0
for %%F in (wpcap.dll Packet.dll) do (
    if not exist "%SystemRoot%\System32\%%F" (
        echo [ERROR] Missing: %SystemRoot%\System32\%%F
        set MISSING=1
    ) else (
        echo [OK]    %SystemRoot%\System32\%%F
    )
)

if %MISSING%==1 (
    echo.
    echo [ERROR] Installation incomplete.
    echo         Re-run the Npcap installer manually and ensure
    echo         "WinPcap API-compatible Mode" is checked.
    exit /b 1
)

echo.
echo [OK] Npcap installed successfully.
echo      You can now start the CaNetGenerator service:
echo        sc start CaNetGenerator
echo.
echo      On first start (network.interface.name blank), the generator
echo      logs all available pcap interfaces and exits.
echo      Set the chosen interface name in:
echo        C:\canet\generator\config\application.properties
echo        network.interface.name=\\Device\\NPF_{XXXXXXXX-...}
echo      Then restart:  sc start CaNetGenerator
echo.
