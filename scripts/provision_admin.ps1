param (
    [Parameter(Mandatory=$true, Position=0)]
    [string]$Email,

    [Parameter(Mandatory=$true, Position=1)]
    [ValidateSet("mess_admin", "laundry_admin", "food_truck_admin")]
    [string]$Role,

    [Parameter(Mandatory=$false, Position=2)]
    [string]$Name = "Hostel Buddy Administrator",

    [Parameter(Mandatory=$false)]
    [string]$Key
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$provisionJs = Join-Path $scriptDir "provision_admin.js"

$cmdArgs = @(
    $provisionJs,
    "--email=$Email",
    "--role=$Role",
    "--name=$Name"
)

if ($Key) {
    $cmdArgs += "--key=$Key"
}

Write-Host "Running provisioning script for $Email as $Role..." -ForegroundColor Cyan
node @cmdArgs
