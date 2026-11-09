[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ModelPath,
    [string]$AdbPath = 'adb',
    [string]$DeviceSerial,
    [switch]$ValidateLocalOnly
)
# Explicit final-app mode: fixed EXP02 and com.example.arptapp, same reviewed path/hash/atomic transport.
$ErrorActionPreference = 'Stop'
$options = @{ ModelVariant = 'EXP02'; ProductionApp = $true; AdbPath = $AdbPath }
if ($ModelPath) { $options.ModelPath = $ModelPath }
if ($DeviceSerial) { $options.DeviceSerial = $DeviceSerial }
if ($ValidateLocalOnly) { $options.ValidateLocalOnly = $true }
if ($WhatIfPreference) { $options.WhatIf = $true }
if ($PSBoundParameters.ContainsKey('Confirm')) { $options.Confirm = $PSBoundParameters.Confirm }
& (Join-Path $PSScriptRoot 'provision-exp03-model.ps1') @options
