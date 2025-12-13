@echo off
REM Compilation script for Network Project (Windows)

echo ╔════════════════════════════════════════════╗
echo ║    Network Project Compilation Script      ║
echo ╚════════════════════════════════════════════╝
echo.

REM Create output directories
if not exist bin mkdir bin

REM Compile Java
echo [1/2] Compiling Java sources...
javac -d bin src\server\*.java src\client\*.java

if %ERRORLEVEL% NEQ 0 (
    echo       X Java compilation failed
    exit /b 1
)
echo       √ Java compilation successful

REM Compile C (requires MinGW or similar)
echo [2/2] Compiling C client...
gcc -Wall -Wextra -O2 -o bin\network_client.exe src\c_client\network_client.c -lws2_32

if %ERRORLEVEL% NEQ 0 (
    echo       X C compilation failed
    echo       Make sure MinGW/GCC is installed and in PATH
    exit /b 1
)
echo       √ C compilation successful

echo.
echo ╔════════════════════════════════════════════╗
echo ║         Compilation Complete!              ║
echo ╠════════════════════════════════════════════╣
echo ║ Java classes: bin\                         ║
echo ║ C executable: bin\network_client.exe       ║
echo ╚════════════════════════════════════════════╝
echo.
echo Run servers with:
echo   java -cp bin server.CombinedServer
echo.
echo Run clients with:
echo   java -cp bin client.JavaNetworkClient
echo   bin\network_client.exe
