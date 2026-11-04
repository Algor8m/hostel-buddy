@echo off
setlocal
echo ==============================================================
echo  Hostel Buddy - Admin Provisioning Runner
echo ==============================================================

if "%~1"=="" (
    echo Usage:
    echo   provision_admin.bat ^<email^> ^<role^> [name]
    echo.
    echo Example:
    echo   provision_admin.bat mess.admin@nmims.edu mess_admin "Mess Admin"
    echo   provision_admin.bat laundry.admin@nmims.edu laundry_admin "Laundry Admin"
    echo   provision_admin.bat cravings.admin@nmims.edu food_truck_admin "Food Truck Admin"
    echo.
    exit /b 1
)

set ADMIN_EMAIL=%~1
set ADMIN_ROLE=%~2
set ADMIN_NAME=%~3

if "%ADMIN_NAME%"=="" set ADMIN_NAME=Hostel Buddy Administrator

node "%~dp0provision_admin.js" --email="%ADMIN_EMAIL%" --role="%ADMIN_ROLE%" --name="%ADMIN_NAME%"
