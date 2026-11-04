<#
.SYNOPSIS
    Migrates user profiles and roles from Firebase Realtime Database to Cloud Firestore.
.DESCRIPTION
    Safely transfers all legacy user profiles and roles from RTDB to Firestore without deleting
    or altering any Realtime Database data.
.PARAMETER DryRun
    Simulates the migration and prints what would be transferred without modifying Firestore.
.PARAMETER Force
    Overwrites existing Firestore user records if role differs.
.EXAMPLE
    .\migrate_rtdb_to_firestore.ps1 -DryRun
    .\migrate_rtdb_to_firestore.ps1
#>
param(
    [switch]$DryRun,
    [switch]$Force
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$keyPath = Join-Path $scriptDir "serviceAccountKey.json"

if (-not (Test-Path $keyPath)) {
    Write-Warning "serviceAccountKey.json was not found in $scriptDir"
    Write-Host "`nTo generate your private service account key:" -ForegroundColor Cyan
    Write-Host "  1. Open Firebase Console: https://console.firebase.google.com/project/hostel-buddy-ar12/settings/serviceaccounts/adminsdk"
    Write-Host "  2. Click 'Generate new private key'"
    Write-Host "  3. Save the downloaded file as '$keyPath'"
    Write-Host "  4. Re-run this PowerShell script."
    exit 1
}

$cliArgs = @()
if ($DryRun) { $cliArgs += "--dry-run" }
if ($Force)  { $cliArgs += "--force" }

node migrate_rtdb_to_firestore.js @cliArgs
