param(
    [Parameter(Mandatory = $true)][string]$Exp02Apk,
    [Parameter(Mandatory = $true)][string]$Exp03Apk,
    [Parameter(Mandatory = $true)][string]$AaptPath
)
$ErrorActionPreference = 'Stop'
$aapt = Get-Command $AaptPath -ErrorAction Stop
if ($aapt.CommandType -ne 'Application' -or [IO.Path]::GetFileName($aapt.Source) -ine 'aapt.exe') { throw 'Use installed Android build-tools aapt.exe.' }
$cases = @(
    @{ Apk = $Exp02Apk; Package = 'com.example.arptapp.exp02'; Label = 'AIRPTCoach V1 - EXP02' },
    @{ Apk = $Exp03Apk; Package = 'com.example.arptapp.exp03'; Label = 'AIRPTCoach V2 - EXP03' }
)
foreach ($case in $cases) {
    $file = (Resolve-Path -LiteralPath $case.Apk).Path
    $badging = & $aapt.Source dump badging $file
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect actual APK badging.' }
    $text = $badging -join "`n"
    if ($text -notmatch ("(?m)^package: name='" + [Regex]::Escape($case.Package) + "'")) { throw 'Actual APK applicationId mismatch.' }
    if ($text -notmatch ("(?m)^application-label:'" + [Regex]::Escape($case.Label) + "'")) { throw 'Actual launcher label mismatch.' }
    if ($text -notmatch '(?m)^application-debuggable') { throw 'Comparison APK must be debuggable.' }
    # Inspect XML from the binary APK, not just the source manifest or output-metadata.json.
    $tree = & $aapt.Source dump xmltree $file AndroidManifest.xml
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect actual APK manifest.' }
    if (($tree -join "`n") -match 'android.intent.category.BROWSABLE|arptapp://') { throw 'Comparison app must not intercept production Auth links.' }
    & (Join-Path $PSScriptRoot 'verify-public-apk.ps1') -ApkPath $file
    Write-Output "PASS actual APK identity: $($case.Package) / $($case.Label) / DEBUG / no browser deep-link filters"
}
