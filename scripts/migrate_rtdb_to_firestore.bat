@echo off
setlocal
echo ======================================================================
echo    Hostel Buddy: Migrate Users from Realtime Database to Firestore   
echo ======================================================================
echo.

cd /d "%~dp0"

if not exist "serviceAccountKey.json" (
    echo [ERROR] serviceAccountKey.json is missing in %~dp0
    echo.
    echo Please follow these steps to download the service account key:
    echo   1. Visit: https://console.firebase.google.com/project/hostel-buddy-ar12/settings/serviceaccounts/adminsdk
    echo   2. Click "Generate new private key"
    echo   3. Save the downloaded JSON file into the "scripts" folder as "serviceAccountKey.json"
    echo   4. Run this script again: migrate_rtdb_to_firestore.bat
    echo.
    pause
    exit /b 1
)

node migrate_rtdb_to_firestore.js %*
pause
