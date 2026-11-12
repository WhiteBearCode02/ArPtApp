[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)][ValidateSet('EXP02', 'EXP03')][string]$ModelVariant,
    [string]$ModelPath,
    [string]$AdbPath = 'adb',
    [string]$DeviceSerial,
    [switch]$ValidateLocalOnly
)
# One reviewed transport/path/hash implementation; no caller-selectable package, code or URL.
$ErrorActionPreference = 'Stop'
$options = @{ ModelVariant = $ModelVariant; ComparisonApp = $true; AdbPath = $AdbPath }
if ($ModelPath) { $options.ModelPath = $ModelPath }
if ($DeviceSerial) { $options.DeviceSerial = $DeviceSerial }
if ($ValidateLocalOnly) { $options.ValidateLocalOnly = $true }
if ($WhatIfPreference) { $options.WhatIf = $true }
if ($PSBoundParameters.ContainsKey('Confirm')) { $options.Confirm = $PSBoundParameters.Confirm }
& (Join-Path $PSScriptRoot 'provision-exp03-model.ps1') @options
