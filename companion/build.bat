@echo off
echo ========================================================
echo   REPLICA - Windows Companion Build Script
echo ========================================================

where x86_64-w64-mingw32-gcc >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Compiling with x86_64-w64-mingw32-gcc...
    x86_64-w64-mingw32-gcc -O2 -Wall src\main.c -o replica-companion.exe -lws2_32
    goto done
)

where gcc >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Compiling with gcc...
    gcc -O2 -Wall src\main.c -o replica-companion.exe -lws2_32
    goto done
)

where cl >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Compiling with MSVC cl...
    cl /O2 /W3 src\main.c /Fe:replica-companion.exe ws2_32.lib
    goto done
)

echo [ERROR] No suitable C compiler (MinGW gcc or MSVC cl) found on PATH.
exit /b 1

:done
echo [SUCCESS] replica-companion.exe built successfully.
