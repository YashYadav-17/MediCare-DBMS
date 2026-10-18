@echo off
title MediCare Web Server Launcher (Port 8080)
echo ================================================================
echo   MediCare Hospital Management System - Web Server Launcher
echo ================================================================
echo.
echo [1/3] Compiling Java classes in src/...
javac -cp "lib\mysql-connector-java.jar;src" src\*.java
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed! Please check Java errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/3] Launching web browser to http://localhost:8080 ...
timeout /t 2 /nobreak >nul
start http://localhost:8080

echo [3/3] Starting MediCare WebServer on port 8080...
echo (Press Ctrl+C in this terminal window to stop the server)
echo.
java -cp "lib\mysql-connector-java.jar;src" WebServer
pause
