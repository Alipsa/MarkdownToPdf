@echo off
setlocal
set DIR=%~dp0%.
cd %DIR%

set JAVA_OPTS=-Xmx8g

rem MD2PDF_JAVA_HOME, if set to a JDK home containing bin\javaw.exe, overrides the
rem JDK on PATH. md2pdf-install.sh records the JDK it validated in md2pdf.env; an
rem MD2PDF_JAVA_HOME already set in the environment wins over it.
if "%MD2PDF_JAVA_HOME%"=="" (
  if exist "%DIR%\md2pdf.env" (
    for /f "usebackq tokens=1,* delims==" %%K in ("%DIR%\md2pdf.env") do (
      if /I "%%K"=="MD2PDF_JAVA_HOME" set MD2PDF_JAVA_HOME=%%~L
    )
  )
)

set JAVA_BIN=javaw
if not "%MD2PDF_JAVA_HOME%"=="" (
  if exist "%MD2PDF_JAVA_HOME%\bin\javaw.exe" set JAVA_BIN=%MD2PDF_JAVA_HOME%\bin\javaw.exe
)

SET COMMAND="dir /B /O-D MarkdownToPdf-*-with-dependencies.jar"
FOR /F "delims=" %%A IN ('%COMMAND%') DO (
    SET TEMPVAR=%%A
    GOTO :SetJar
)
:SetJar
set JAR=%TEMPVAR%
start "" "%JAVA_BIN%" %JAVA_OPTS% -jar .\%JAR%
