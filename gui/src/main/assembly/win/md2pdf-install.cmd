@echo off
rem Delayed expansion is disabled explicitly, not merely left off: with it on, cmd.exe
rem re-parses ! in every expanded value, so an install path or a %USERPROFILE% containing
rem one would be silently corrupted. A bare `setlocal` would inherit it from a parent
rem started as `cmd /V:ON`. Nothing here uses !VAR!, so there is nothing to give up.
setlocal DisableDelayedExpansion
rem MarkdownToPdf installer for Windows.
rem
rem   md2pdf-install.cmd [target-directory]

set "SCRIPTDIR=%~dp0"
set "APP_NAME=MarkdownToPdf"
set "SRC=%SCRIPTDIR%%APP_NAME%"

if "%~1"=="" (
  set "DEST=%LOCALAPPDATA%\Programs\%APP_NAME%"
) else (
  set "DEST=%~f1"
)
set "RAW_DEST="
if not "%~1"=="" set "RAW_DEST=%~1"

:normalize_destination
rem Preserve a drive root (C:\), but remove every other trailing separator before the
rem ancestor walk. This also handles callers that pass a path ending in multiple '\\'s.
if "%DEST:~3%"=="" goto destination_normalized
if not "%DEST:~-1%"=="\" goto destination_normalized
set "DEST=%DEST:~0,-1%"
goto normalize_destination

:destination_normalized
if "%MD2PDF_DEBUG_PATHS%"=="1" echo [DEBUG] RAW_DEST=%RAW_DEST%
if "%MD2PDF_DEBUG_PATHS%"=="1" echo [DEBUG] NORMALIZED_DEST=%DEST%

if not exist "%SRC%\" (
  echo [ERROR] %APP_NAME% is not next to this script - run it from the unzipped archive.
  exit /b 1
)

rem Canonicalize both paths, then walk each path toward its root. Refuse every overlap:
rem installing onto the source, into a source child, or into an ancestor of the source.
for %%I in ("%SRC%") do set "SRC_REAL=%%~fI"
for %%I in ("%DEST%") do set "DEST_REAL=%%~fI"

set "CURRENT=%DEST_REAL%"
:check_destination_ancestors
if /I "%CURRENT%"=="%SRC_REAL%" goto overlap
for %%I in ("%CURRENT%\..") do set "PARENT=%%~fI"
if /I "%PARENT%"=="%CURRENT%" goto check_source_ancestors
set "CURRENT=%PARENT%"
goto check_destination_ancestors

:check_source_ancestors
set "CURRENT=%SRC_REAL%"
:check_source_ancestors_loop
if /I "%CURRENT%"=="%DEST_REAL%" goto overlap
for %%I in ("%CURRENT%\..") do set "PARENT=%%~fI"
if /I "%PARENT%"=="%CURRENT%" goto paths_are_safe
set "CURRENT=%PARENT%"
goto check_source_ancestors_loop

:overlap
echo [ERROR] Source and destination overlap - refusing to remove or copy recursively.
exit /b 1

:paths_are_safe

if exist "%DEST%\" (
  if "%MD2PDF_REPLACE_EXISTING%"=="1" (
    echo [INSTALL] Replacing the existing installation at %DEST%
    rmdir /S /Q "%DEST%"
  ) else (
    echo [ERROR] An installation already exists at %DEST%.
    echo         Set MD2PDF_REPLACE_EXISTING=1 to replace it.
    exit /b 1
  )
)

echo [INSTALL] Installing to %DEST%
mkdir "%DEST%" 2>nul
xcopy /E /I /Q /Y "%SRC%" "%DEST%" >nul
if errorlevel 1 (
  echo [ERROR] Copy failed.
  exit /b 1
)

rem ── desktop shortcut ────────────────────────────────────────────
rem cmd cannot create a .lnk, so PowerShell does it. Success is decided by testing for
rem the file, never by the exit code: -ExecutionPolicy Bypass is silently ignored when
rem policy comes from Group Policy, and under AppLocker Constrained Language Mode
rem PowerShell runs the script but fails at New-Object -ComObject.
set "DESKTOP=%USERPROFILE%\Desktop"
if not exist "%DESKTOP%\" set "DESKTOP=%USERPROFILE%\OneDrive\Desktop"
set "LNK=%DESKTOP%\%APP_NAME%.lnk"
set "STUB=%DESKTOP%\%APP_NAME%.cmd"

rem Delete first, so the test below means "this run created a shortcut" rather than
rem "a file with this name exists" - a shortcut left by an earlier install would point
rem at a path that has since moved and would satisfy the test while being broken.
if exist "%LNK%" del /Q "%LNK%"
if exist "%STUB%" del /Q "%STUB%"

echo [INSTALL] Creating the desktop shortcut ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%DEST%\createShortcut.ps1" >nul 2>&1

if exist "%LNK%" (
  echo [INSTALL] Shortcut created.
) else (
  echo [WARN]  Could not create a .lnk shortcut - writing a launcher script instead.
  rem Must be a generated stub, not a copy of run.cmd: run.cmd finds everything through
  rem %%~dp0, its own directory, so a copy on the Desktop resolves that to the Desktop
  rem and finds neither runtime nor jar.
  > "%STUB%" echo @echo off
  >> "%STUB%" echo call "%DEST%\run.cmd"
  if exist "%STUB%" (
    echo [INSTALL] Launcher script created at %STUB%
  ) else (
    echo [ERROR] Could not create a shortcut or a launcher script.
    exit /b 1
  )
)

echo.
echo [INSTALL] Installation complete.
echo [INSTALL] Run with: %DEST%\run.cmd
exit /b 0
