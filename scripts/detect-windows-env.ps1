# =============================================================================
# detect-windows-env.ps1
# Detects runtime versions on a Windows Server (WS2008 R2 / WS2012 / WS2022)
# or Windows 7/10 development machine for the BF system inventory.
#
# Run this on EACH of:
#   - ATOM dev workstation (Windows 7 VM)
#   - Non-prod target environment VM
#   - Prod target environment VM (if accessible)
#
# Usage: powershell -ExecutionPolicy Bypass -File detect-windows-env.ps1
#        powershell -ExecutionPolicy Bypass -File detect-windows-env.ps1 -Json
# =============================================================================

param(
    [switch]$Json,
    [string]$OutputFile = ""
)

$ErrorActionPreference = "SilentlyContinue"
$results = [ordered]@{}

function Get-RegValue {
    param($Path, $Name)
    try { (Get-ItemProperty -Path $Path -Name $Name -ErrorAction Stop).$Name }
    catch { $null }
}

Write-Host ""
Write-Host "============================================================"
Write-Host " BF System — Windows Environment Detection"
Write-Host " Host: $env:COMPUTERNAME"
Write-Host " Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "============================================================"

# ---------- Operating System -------------------------------------------------
Write-Host "`n=== Operating System ==="
$os = Get-WmiObject -Class Win32_OperatingSystem
$results["os_name"]    = $os.Caption
$results["os_version"] = $os.Version
$results["os_build"]   = $os.BuildNumber
$results["os_sp"]      = $os.ServicePackMajorVersion
$results["os_arch"]    = $os.OSArchitecture
Write-Host "  OS:       $($os.Caption)"
Write-Host "  Version:  $($os.Version)"
Write-Host "  Build:    $($os.BuildNumber)"
Write-Host "  SP:       SP$($os.ServicePackMajorVersion)"
Write-Host "  Arch:     $($os.OSArchitecture)"

# Windows Server 2008 R2 check
if ($os.BuildNumber -eq "7601") {
    Write-Host "  WARNING:  Windows Server 2008 R2 / Windows 7 detected — EOL January 2020" -ForegroundColor Yellow
}

# ---------- .NET Framework ---------------------------------------------------
Write-Host "`n=== .NET Framework ==="
$fxVersions = @()
$fxPath = "HKLM:\SOFTWARE\Microsoft\NET Framework Setup\NDP"
if (Test-Path $fxPath) {
    Get-ChildItem $fxPath -Recurse |
    Where-Object { $_.PSChildName -eq "Full" -or $_.PSChildName -match "^v\d" } |
    ForEach-Object {
        $ver = Get-RegValue $_.PSPath "Version"
        $rel = Get-RegValue $_.PSPath "Release"
        if ($ver) {
            $fxVersions += "$($_.PSPath -replace '.*\\','' ) $ver (Release: $rel)"
            Write-Host "  $($_.PSPath -replace '.*\\','') : $ver"
        }
    }
}
$results["dotnet_framework"] = $fxVersions -join "; "

# .NET Framework 4.x release to version mapping
$rel = Get-RegValue "HKLM:\SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full" "Release"
if ($rel) {
    $fxVer = switch ($rel) {
        { $_ -ge 528040 } { "4.8"   }
        { $_ -ge 461808 } { "4.7.2" }
        { $_ -ge 461308 } { "4.7.1" }
        { $_ -ge 460798 } { "4.7"   }
        { $_ -ge 394802 } { "4.6.2" }
        { $_ -ge 394254 } { "4.6.1" }
        { $_ -ge 393295 } { "4.6"   }
        { $_ -ge 379893 } { "4.5.2" }
        { $_ -ge 378675 } { "4.5.1" }
        { $_ -ge 378389 } { "4.5"   }
        default            { "4.0 or earlier" }
    }
    Write-Host "  Highest .NET Fx:  $fxVer (release key: $rel)" -ForegroundColor Green
    $results["dotnet_framework_highest"] = $fxVer
}

# ---------- Java -------------------------------------------------------------
Write-Host "`n=== Java ==="
$javaExe = (Get-Command java -ErrorAction SilentlyContinue)?.Source
if ($javaExe) {
    $javaVer = & java -version 2>&1 | Select-Object -First 1
    Write-Host "  java: $javaVer"
    $results["java"] = "$javaVer"
    $results["java_path"] = $javaExe

    # Check JAVA_HOME
    $results["java_home"] = $env:JAVA_HOME
    Write-Host "  JAVA_HOME: $env:JAVA_HOME"

    # Find all installed JDKs
    $jdkPaths = @(
        "HKLM:\SOFTWARE\JavaSoft\Java Development Kit",
        "HKLM:\SOFTWARE\JavaSoft\Java Runtime Environment",
        "HKLM:\SOFTWARE\WOW6432Node\JavaSoft\Java Development Kit",
        "HKLM:\SOFTWARE\WOW6432Node\JavaSoft\Java Runtime Environment"
    )
    $jdkInstalls = @()
    foreach ($p in $jdkPaths) {
        if (Test-Path $p) {
            Get-ChildItem $p | ForEach-Object {
                $home = Get-RegValue $_.PSPath "JavaHome"
                $ver  = $_.PSChildName
                if ($home) {
                    $jdkInstalls += "$ver at $home"
                    Write-Host "  Installed: $ver → $home"
                }
            }
        }
    }
    $results["java_installs"] = $jdkInstalls -join "; "
} else {
    Write-Host "  Java: not found on PATH" -ForegroundColor Red
    $results["java"] = "not found"
}

# Maven
$mvnExe = (Get-Command mvn -ErrorAction SilentlyContinue)?.Source
if ($mvnExe) {
    $mvnVer = & mvn --version 2>&1 | Select-Object -First 1
    Write-Host "  Maven: $mvnVer"
    $results["maven"] = "$mvnVer"
} else { $results["maven"] = "not found" }

# ---------- IIS --------------------------------------------------------------
Write-Host "`n=== IIS ==="
try {
    $iisVer = (Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\InetStp" -ErrorAction Stop).VersionString
    $iisPath = (Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\InetStp").InstallPath
    Write-Host "  IIS:  $iisVer"
    Write-Host "  Path: $iisPath"
    $results["iis_version"] = $iisVer
    $results["iis_path"]    = $iisPath

    # List sites
    Import-Module WebAdministration -ErrorAction SilentlyContinue
    $sites = Get-ChildItem IIS:\Sites -ErrorAction SilentlyContinue
    if ($sites) {
        $sites | ForEach-Object {
            Write-Host "  Site: $($_.Name) — State: $($_.State) — $($_.Bindings.Collection)"
        }
    }
} catch {
    Write-Host "  IIS: not installed or not accessible"
    $results["iis_version"] = "not found"
}

# ---------- Apache Tomcat ----------------------------------------------------
Write-Host "`n=== Apache Tomcat ==="
$tomcatPaths = @(
    "C:\Program Files\Apache Software Foundation",
    "C:\Tomcat",
    "C:\apache-tomcat*",
    "D:\Tomcat"
)
$tomcatFound = @()
foreach ($basePath in $tomcatPaths) {
    Get-ChildItem $basePath -Filter "Tomcat*" -ErrorAction SilentlyContinue |
    ForEach-Object {
        $versionFile = Join-Path $_.FullName "RELEASE-NOTES"
        if (Test-Path $versionFile) {
            $ver = Get-Content $versionFile | Select-String "Apache Tomcat Version" | Select-Object -First 1
            Write-Host "  Found: $($_.FullName) — $ver"
            $tomcatFound += "$($_.FullName): $ver"
        }
    }
}

# Check Windows services for Tomcat
Get-Service | Where-Object { $_.DisplayName -like "*Tomcat*" -or $_.Name -like "*tomcat*" } |
ForEach-Object {
    Write-Host "  Service: $($_.Name) — $($_.DisplayName) — $($_.Status)"
    $tomcatFound += "Service: $($_.Name) ($($_.Status))"
}
$results["tomcat"] = if ($tomcatFound) { $tomcatFound -join "; " } else { "not found" }

# ---------- Windows Services (likely service-wrapped components) --------------
Write-Host "`n=== Relevant Windows Services ==="
$bfServices = Get-Service | Where-Object {
    $_.DisplayName -like "*BF*" -or
    $_.DisplayName -like "*Broker*" -or
    $_.DisplayName -like "*Marshal*" -or
    $_.DisplayName -like "*Gateway*" -or
    $_.Name -like "*BF*"
}
if ($bfServices) {
    $bfServices | ForEach-Object {
        Write-Host "  $($_.Name) | $($_.DisplayName) | $($_.Status)" -ForegroundColor Cyan
    }
    $results["bf_services"] = ($bfServices | ForEach-Object { "$($_.Name):$($_.Status)" }) -join "; "
} else {
    Write-Host "  No BF-related services found (check display name patterns)"
    $results["bf_services"] = "none matched"
}

# ---------- Oracle client ----------------------------------------------------
Write-Host "`n=== Oracle Client ==="
$oracleHome = $env:ORACLE_HOME
$results["oracle_home"] = $oracleHome ?? "not set"
Write-Host "  ORACLE_HOME: $($oracleHome ?? 'not set')"

$oracleReg = Get-RegValue "HKLM:\SOFTWARE\ORACLE\KEY_OraClient12Home1" "ORACLE_HOME"
if (-not $oracleReg) {
    # Try other common Oracle registry paths
    Get-ChildItem "HKLM:\SOFTWARE\ORACLE" -ErrorAction SilentlyContinue |
    ForEach-Object {
        $oh = Get-RegValue $_.PSPath "ORACLE_HOME"
        if ($oh) { Write-Host "  Oracle reg home: $oh"; $results["oracle_client_path"] = $oh }
    }
}

# Check for sqlplus
$sqlplusExe = (Get-Command sqlplus -ErrorAction SilentlyContinue)?.Source
if ($sqlplusExe) {
    $sqlplusVer = & sqlplus -V 2>&1 | Select-String "Release"
    Write-Host "  sqlplus: $sqlplusVer"
    $results["sqlplus"] = "$sqlplusVer"
} else { $results["sqlplus"] = "not found" }

# Search for ojdbc jars
Write-Host "`n=== Oracle JDBC Jars ==="
$ojdbcSearch = @("C:\", "D:\", $env:USERPROFILE)
foreach ($root in $ojdbcSearch) {
    Get-ChildItem $root -Filter "ojdbc*.jar" -Recurse -ErrorAction SilentlyContinue |
    Select-Object -First 5 |
    ForEach-Object {
        Write-Host "  Found: $($_.FullName)"
        $results["ojdbc_jar"] = $_.FullName
    }
}

# ---------- TLS / SSL --------------------------------------------------------
Write-Host "`n=== TLS Configuration ==="
# Check Schannel registry for enabled/disabled protocols
$schannelPath = "HKLM:\SYSTEM\CurrentControlSet\Control\SecurityProviders\SCHANNEL\Protocols"
@("TLS 1.0","TLS 1.1","TLS 1.2","TLS 1.3","SSL 3.0") | ForEach-Object {
    $proto = $_
    $clientEnabled = Get-RegValue "$schannelPath\$proto\Client" "Enabled"
    $serverEnabled = Get-RegValue "$schannelPath\$proto\Server" "Enabled"
    $status = "default (OS policy)"
    if ($null -ne $clientEnabled) { $status = "Client:$(if($clientEnabled -eq 1){'ON'}else{'OFF'}) Server:$(if($serverEnabled -eq 1){'ON'}else{'OFF'})" }
    Write-Host "  $proto : $status"
}

# ---------- Summary / Output -------------------------------------------------
Write-Host "`n============================================================"
Write-Host " Summary"
Write-Host "============================================================"
$results.GetEnumerator() | ForEach-Object {
    Write-Host ("  {0,-30} {1}" -f $_.Key, $_.Value)
}

if ($Json) {
    $jsonOut = $results | ConvertTo-Json -Depth 3
    if ($OutputFile) {
        $jsonOut | Out-File -FilePath $OutputFile -Encoding UTF8
        Write-Host "`nResults saved to: $OutputFile"
    } else {
        Write-Host "`n--- JSON ---"
        Write-Host $jsonOut
    }
}

Write-Host "`nCopy this output and record in inventory/app-inventory.json"
