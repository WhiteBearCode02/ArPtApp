[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ModelPath,
    [string]$AdbPath = 'adb',
    [string]$DeviceSerial,
    [switch]$ValidateLocalOnly,
    [ValidateSet('EXP02', 'EXP03')][string]$ModelVariant = 'EXP03',
    [switch]$ComparisonApp,
    [switch]$ProductionApp
)
$ErrorActionPreference = 'Stop'
$ModelVariant = $ModelVariant.ToUpperInvariant() # ValidateSet is case-insensitive; branch/package mapping must agree.
if ($ComparisonApp -and $ProductionApp) { throw 'Choose exactly one explicit app mode.' }
if ($ProductionApp -and $ModelVariant -cne 'EXP02') { throw 'The selected final app accepts only EXP02 provisioning.' }
$canonicalName = 'airptcoach_yolo26n_exp03_final_fp32.tflite'
$canonicalBytes = 9894032L
$canonicalHash = '3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167'
if ($ModelVariant -ceq 'EXP02') {
    if (-not $ComparisonApp -and -not $ProductionApp) { throw 'EXP02 requires an explicit comparison or final-app mode.' }
    $canonicalName = 'airptcoach_yolo26n_exp02_fp32.tflite'
    $canonicalBytes = 9894030L
    $canonicalHash = '464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749'
}
$packageName = if ($ComparisonApp) { 'com.example.arptapp.' + $ModelVariant.ToLowerInvariant() } else { 'com.example.arptapp' }
if ([string]::IsNullOrWhiteSpace($ModelPath)) { $ModelPath = Join-Path $PSScriptRoot "../app/src/main/assets/$canonicalName" }

# Reuse ONLY the reviewed binary/hash helpers, not the EXP02 script's body/identity/provenance.
# Fixed, repository-owned source; no downloaded or parameter-selected code is evaluated.
$tokens = $null; $parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PSScriptRoot 'provision-exp02-model.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Shared transport syntax invalid.' }
foreach ($name in @('Get-PrivateFileSha256', 'Invoke-PrivateAdb')) {
    $function = $ast.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $true)
    if (-not $function) { throw 'Reviewed binary transport helper missing.' }
    . ([ScriptBlock]::Create($function.Extent.Text))
}
$model = Get-Item -LiteralPath $ModelPath
if ($model.PSIsContainer -or $model.Name -cne $canonicalName -or $model.Length -ne $canonicalBytes) { throw 'Selected model filename/size mismatch.' }
for ($item = $model; $null -ne $item; $item = $item.Parent) {
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Model source cannot use symlinks/junctions.' }
    # FileInfo has Directory rather than Parent.
    if ($item -is [IO.FileInfo]) { $item = $item.Directory; if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Model directory is a reparse point.' } }
}
if ((Get-PrivateFileSha256 -LiteralPath $model.FullName) -cne $canonicalHash) { throw 'Selected model SHA256 mismatch.' }
Write-Output "$ModelVariant local identity PASS: $canonicalBytes bytes SHA256=$canonicalHash"
if ($ValidateLocalOnly) { return } # No adb, device mutation or inference in this mode.
$adb = Get-Command $AdbPath -ErrorAction Stop
if ($adb.CommandType -ne 'Application' -or [IO.Path]::GetExtension($adb.Source) -ine '.exe') { throw 'Use the actual platform-tools adb.exe.' }
$script:resolvedAdb = $adb.Source
if ($DeviceSerial -and $DeviceSerial -notmatch '^[A-Za-z0-9_.:-]+$') { throw 'Unsafe serial.' }
$devices = Invoke-PrivateAdb -AdbArguments @('devices')
$online = @($devices -split '\r?\n' | Where-Object { $_ -match '^([A-Za-z0-9_.:-]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($online.Count -ne 1 -or ($DeviceSerial -and $DeviceSerial -cne $online[0])) { throw 'Exactly one authorized device, matching the requested serial, is required.' }
$serial = $online[0]
$prefix = @('-s', $serial)
$privateRoot = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'pwd'))
if ($privateRoot -cnotmatch ('^/data/(user/[0-9]+|data)/' + [Regex]::Escape($packageName) + '$')) { throw 'Exact DEBUG run-as private root not verified.' }

function Assert-Exp03PrivatePath([string]$RelativePath) {
    # Every relative target is fixed/flat; compare readlink output before mkdir/dd/mv to reject links.
    if ($RelativePath -notmatch '^no_backup(/yolo(/[a-z0-9_.-]+)?)?$') { throw 'Unsafe private relative path.' }
    $resolved = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'readlink', '-f', $RelativePath))
    if ($resolved -cne "$privateRoot/$RelativePath") { throw 'Private symlink/path traversal rejected.' }
}
Assert-Exp03PrivatePath 'no_backup'
if (-not $PSCmdlet.ShouldProcess("DEBUG $packageName private noBackup", "Provision $ModelVariant without clearing data")) { return }
Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'am', 'force-stop', $packageName)) | Out-Null
Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'mkdir', '-p', 'no_backup')) | Out-Null
Assert-Exp03PrivatePath 'no_backup/yolo'
Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'mkdir', '-p', 'no_backup/yolo')) | Out-Null
$target = "no_backup/yolo/$canonicalName"
Assert-Exp03PrivatePath $target
$existing = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'find', 'no_backup/yolo', '-maxdepth', '1', '-name', $canonicalName))
if ($existing) {
    $kind = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'ls', '-ld', $target))
    if (-not $kind.StartsWith('-')) { throw 'Final model target must be a regular file, not a directory/link.' }
}
$temporary = "$target.upload-$([Guid]::NewGuid().ToString('N'))"
try {
    Assert-Exp03PrivatePath $temporary
    $local = Get-Item -LiteralPath $model.FullName
    if ($local.Length -ne $canonicalBytes -or (Get-PrivateFileSha256 -LiteralPath $local.FullName) -cne $canonicalHash) { throw 'Local model changed after preflight.' }
    # Exact count/raw stream is inherited from EXP02's tested transport. No PowerShell text piping.
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-in', 'run-as', $packageName, 'dd', "of=$temporary", 'bs=65536', 'iflag=count_bytes', "count=$canonicalBytes", 'conv=fsync', 'status=none')) -InputFile $local.FullName | Out-Null
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'chmod', '600', $temporary)) | Out-Null
    $size = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'wc', '-c', $temporary))
    $hash = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'sha256sum', $temporary))
    if (($size -split '\s+')[0] -cne [string]$canonicalBytes -or ($hash -split '\s+')[0] -cne $canonicalHash) { throw 'Staged EXP03 size/SHA mismatch; final file unchanged.' }
    Assert-Exp03PrivatePath $target
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'mv', $temporary, $target)) | Out-Null
    $finalSize = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'wc', '-c', $target))
    $finalHash = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'sha256sum', $target))
    if (($finalSize -split '\s+')[0] -cne [string]$canonicalBytes -or ($finalHash -split '\s+')[0] -cne $canonicalHash) { throw 'Final EXP03 size/SHA mismatch.' }
} finally {
    # Only this newly generated exact temporary filename, never a model, directory or user record.
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'rm', '-f', $temporary)) | Out-Null
}
Write-Output "$ModelVariant device identity PASS: $canonicalBytes bytes SHA256=$canonicalHash"
Write-Output "Private destination: $privateRoot/$target"
Write-Output 'Reviewed contract is unchanged. Provisioning does not activate inference or promote YOLO to Primary.'
