@echo off
rem See md2pdf-install.cmd for why this is spelled out: a bare `setlocal` inherits delayed
rem expansion from a parent `cmd /V:ON`, and that would corrupt an install path containing !.
setlocal DisableDelayedExpansion
rem The quotes around the whole assignment are required, not style: without them cmd.exe
rem parses &, ^, ( and ) in the install path as syntax. C:\Users\A & B\... is a legal
rem Windows path and would break the launcher.
set "DIR=%~dp0"
start "" "%DIR%runtime\bin\javaw.exe" -Xmx8g -jar "%DIR%MarkdownToPdf.jar"
