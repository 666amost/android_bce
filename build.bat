@echo off
cd /d "%~dp0"
call gradlew.bat build -x test
pause

