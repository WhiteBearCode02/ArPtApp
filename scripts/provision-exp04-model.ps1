[CmdletBinding(SupportsShouldProcess = $true)]
param([string]$ModelPath, [string]$AdbPath = 'adb', [string]$DeviceSerial, [switch]$ValidateLocalOnly)
$ErrorActionPreference = 'Stop'
$canonicalName = 'airptcoach_yolo26n_exp04_7class_fp32.tflite'
$canonicalBytes = 9898949L
$canonicalHash = '3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d'
$packageName = 'com.example.arptapp.exp04' # fixed isolated DEBUG sandbox; no production override
if ([string]::IsNullOrWhiteSpace($ModelPath)) { $ModelPath = Join-Path $PSScriptRoot "../app/src/main/assets/$canonicalName" }

# Import ONLY the reviewed timeout-bounded raw-stream/hash helpers, never EXP02 provisioning code.
$tokens = $null; $parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'provision-exp02-model.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Shared binary transport syntax invalid.' }
foreach ($name in @('Get-PrivateFileSha256', 'Invoke-PrivateAdb')) {
    $function = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $true)
    if (-not $function) { throw 'Reviewed binary helper missing.' }
    . ([ScriptBlock]::Create($function.Extent.Text))
}
$model = Get-Item -LiteralPath $ModelPath
if ($model.PSIsContainer -or $model.Name -cne $canonicalName -or $model.Length -ne $canonicalBytes) { throw 'EXP04 canonical filename/size mismatch.' }
for ($item = $model; $null -ne $item;) {
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Model source cannot contain links/junctions.' }
    $item = if ($item -is [IO.FileInfo]) { $item.Directory } else { $item.Parent }
}
if ((Get-PrivateFileSha256 -LiteralPath $model.FullName) -cne $canonicalHash) { throw 'EXP04 SHA256 mismatch.' }
Write-Output "EXP04 local identity PASS: $canonicalBytes bytes SHA256=$canonicalHash"
if ($ValidateLocalOnly) { return } # no adb or device operation
if (-not $DeviceSerial -or $DeviceSerial -notmatch '^[A-Za-z0-9_.:-]+$') { throw 'Pass one explicit authorized -DeviceSerial.' }
$adb = Get-Command $AdbPath -ErrorAction Stop
if ($adb.CommandType -ne 'Application' -or [IO.Path]::GetExtension($adb.Source) -ine '.exe') { throw 'Use actual platform-tools adb.exe.' }
$script:resolvedAdb = $adb.Source
$devices = Invoke-PrivateAdb -AdbArguments @('devices')
$online = @($devices -split '\r?\n' | Where-Object { $_ -match '^([A-Za-z0-9_.:-]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($online.Count -ne 1 -or $online[0] -cne $DeviceSerial) { throw 'Exactly one authorized device matching -DeviceSerial is required.' }
$prefix = @('-s', $DeviceSerial)
$privateRoot = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'pwd'))
if ($privateRoot -cnotmatch ('^/data/(user/[0-9]+|data)/' + [Regex]::Escape($packageName) + '$')) { throw 'Exact DEBUG run-as root not verified.' }
function Assert-Exp04PrivatePath([string]$RelativePath) {
    if ($RelativePath -notmatch '^no_backup(/yolo(/[a-z0-9_.-]+)?)?$') { throw 'Unsafe private relative path.' }
    $resolved = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'readlink', '-f', $RelativePath))
    if ($resolved -cne "$privateRoot/$RelativePath") { throw 'Private symlink/path escape rejected.' }
}
Assert-Exp04PrivatePath 'no_backup'
if (-not $PSCmdlet.ShouldProcess("DEBUG $packageName private storage", 'Stop this comparison app and provision EXP04 only')) { return }
Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'am', 'force-stop', $packageName)) | Out-Null
Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'mkdir', '-p', 'no_backup')) | Out-Null
Assert-Exp04PrivatePath 'no_backup/yolo'
Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'mkdir', '-p', 'no_backup/yolo')) | Out-Null
$target = "no_backup/yolo/$canonicalName"
Assert-Exp04PrivatePath $target
$existing = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'find', 'no_backup/yolo', '-maxdepth', '1', '-name', $canonicalName))
if ($existing) {
    $kind = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'ls', '-ld', $target))
    if (-not $kind.StartsWith('-')) { throw 'Model target must be a regular file.' }
}
$temporary = "$target.upload-$([Guid]::NewGuid().ToString('N'))"
try {
    Assert-Exp04PrivatePath $temporary
    if ((Get-Item -LiteralPath $model.FullName).Length -ne $canonicalBytes -or (Get-PrivateFileSha256 -LiteralPath $model.FullName) -cne $canonicalHash) { throw 'Local model changed after preflight.' }
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-in', 'run-as', $packageName, 'dd', "of=$temporary", 'bs=65536', 'iflag=count_bytes', "count=$canonicalBytes", 'conv=fsync', 'status=none')) -InputFile $model.FullName | Out-Null
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'chmod', '600', $temporary)) | Out-Null
    $size = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'wc', '-c', $temporary))
    $hash = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'sha256sum', $temporary))
    if (($size -split '\s+')[0] -cne [string]$canonicalBytes -or ($hash -split '\s+')[0] -cne $canonicalHash) { throw 'Staged identity mismatch; final file unchanged.' }
    Assert-Exp04PrivatePath $target
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'mv', $temporary, $target)) | Out-Null
    $finalSize = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'wc', '-c', $target))
    $finalHash = Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'sha256sum', $target))
    if (($finalSize -split '\s+')[0] -cne [string]$canonicalBytes -or ($finalHash -split '\s+')[0] -cne $canonicalHash) { throw 'Final EXP04 identity mismatch.' }
} finally {
    # Exact fresh temporary file only; never deletes an EXP02/03 model, workout or directory.
    Invoke-PrivateAdb -AdbArguments ($prefix + @('exec-out', 'run-as', $packageName, 'rm', '-f', $temporary)) | Out-Null
}
Write-Output 'EXP04 private model identity PASS. Contract remains NOT_VERIFIED; no activation or promotion.'
