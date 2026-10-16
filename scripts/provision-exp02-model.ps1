[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ModelPath,
    [string]$AdbPath = 'adb',
    [string]$DeviceSerial,
    [string]$PackageName = 'com.example.arptapp',
    [string]$GoldenBundleDirectory,
    [string]$ReferenceFileName = 'reference.json'
)

# DEBUG-only private provisioning. Never use PowerShell's text pipeline for model/RGB bytes.
# No APK/assets mutation, root, shared storage, downloads, or production verification flag.
$ErrorActionPreference = 'Stop'
function Get-PrivateFileSha256 {
    param([Parameter(Mandatory=$true)][string]$LiteralPath)
    # Pure read-only hashing also works under -WhatIf. Windows PowerShell's
    # Get-FileHash internally uses a WhatIf-sensitive property pipeline.
    $hashStream = [System.IO.File]::OpenRead($LiteralPath)
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($hashStream)).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose(); $hashStream.Dispose() }
}
if ([string]::IsNullOrWhiteSpace($ModelPath)) {
    # Resolve script-relative defaults after param binding, not in the caller's scope.
    if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) { throw 'Pass the validated local -ModelPath explicitly; script directory is unavailable.' }
    $ModelPath = Join-Path $PSScriptRoot '../app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite'
}
$canonicalName = 'airptcoach_yolo26n_exp02_fp32.tflite'
$canonicalBytes = 9894030L
$canonicalHash = '464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749'
if ($PackageName -notmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$') { throw 'Invalid package name.' }
if ($ReferenceFileName -notmatch '^[a-z0-9][a-z0-9_-]{0,63}\.json$') { throw 'Invalid local reference filename.' }
$model = Get-Item -LiteralPath $ModelPath
if ($model.PSIsContainer -or $model.Name -cne $canonicalName -or $model.Length -ne $canonicalBytes) { throw 'Canonical model filename/size mismatch.' }
if ((Get-PrivateFileSha256 -LiteralPath $model.FullName) -cne $canonicalHash) { throw 'Canonical model SHA256 mismatch.' }
$adbCommand = Get-Command $AdbPath -ErrorAction Stop
if ($adbCommand.CommandType -ne 'Application' -or [System.IO.Path]::GetExtension($adbCommand.Source) -ine '.exe') { throw 'Use the actual Android platform-tools adb.exe, not a shell wrapper.' }
$script:resolvedAdb = $adbCommand.Source

function Invoke-PrivateAdb {
    param([string[]]$AdbArguments, [string]$InputFile)
    # Every argument here is a validated identifier/path or fixed token, never a shell command.
    foreach ($argument in $AdbArguments) {
        if ($argument -notmatch '^[A-Za-z0-9_./:=+-]+$') { throw 'Unsafe ADB argument.' }
    }
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $script:resolvedAdb
    $info.Arguments = $AdbArguments -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardInput = [bool]$InputFile
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    $inputStream = $null
    $started = $false
    $deadline = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        if (-not $process.Start()) { throw 'Could not start ADB.' }
        $started = $true
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if ($InputFile) {
            $inputStream = [System.IO.File]::OpenRead($InputFile)
            # Keep binary I/O on the raw stream through flush and EOF. Do not mix
            # StreamWriter.Close() with direct BaseStream writes on Windows/.NET.
            $binaryStdin = $process.StandardInput.BaseStream
            $copy = $inputStream.CopyToAsync($binaryStdin)
            $remaining = [Math]::Max(1, 120000 - [int]$deadline.ElapsedMilliseconds)
            if (-not $copy.Wait($remaining)) { throw 'ADB binary transfer timed out after 120 seconds.' }
            # Windows PowerShell exposes Task<VoidTaskResult> to the pipeline unless suppressed.
            [void]$copy.GetAwaiter().GetResult()
            $binaryStdin.Flush()
            $binaryStdin.Close()
        }
        $remaining = [Math]::Max(1, 120000 - [int]$deadline.ElapsedMilliseconds)
        if ($deadline.ElapsedMilliseconds -ge 120000 -or -not $process.WaitForExit($remaining)) { throw 'ADB command timed out after 120 seconds.' }
        $output = $stdout.GetAwaiter().GetResult()
        $errorOutput = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "ADB failed (exit $($process.ExitCode)). Check device authorization, platform-tools, and DEBUG run-as permission. Native error text is not printed." }
        return $output.Trim()
    } finally {
        if ($inputStream) { $inputStream.Dispose() }
        if ($started -and -not $process.HasExited) { $process.Kill() }
        $process.Dispose()
    }
}

if ($DeviceSerial -and $DeviceSerial -notmatch '^[A-Za-z0-9_.:-]+$') { throw 'Invalid device serial.' }
if (-not $DeviceSerial) {
    $devices = Invoke-PrivateAdb -AdbArguments @('devices')
    $onlineDevices = @($devices -split '\r?\n' | Where-Object { $_ -match '^([A-Za-z0-9_.:-]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($onlineDevices.Count -ne 1) { throw 'Exactly one authorized online device is required; otherwise pass -DeviceSerial explicitly.' }
    $DeviceSerial = $onlineDevices[0]
}
$script:deviceArguments = @('-s', $DeviceSerial)
if ((Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('get-state'))) -ne 'device') { throw 'Device is not online.' }
$privateRoot = Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'pwd'))
if ($privateRoot -notmatch '^/data/(user/[0-9]+|data)/[a-z][a-z0-9_.]+$') { throw 'Unexpected run-as private directory. Refusing provisioning.' }

$uploads = @([PSCustomObject]@{ Source = $model.FullName; Target = "no_backup/yolo/$canonicalName"; ExpectedHash = $canonicalHash; ExpectedBytes = $canonicalBytes })
if ($GoldenBundleDirectory) {
    $bundle = (Resolve-Path -LiteralPath $GoldenBundleDirectory).Path
    $referenceFile = Join-Path $bundle $ReferenceFileName
    if ((Get-Item -LiteralPath $referenceFile).Length -gt 2097152L) { throw 'Reference manifest exceeds safety limit.' }
    $reference = Get-Content -LiteralPath $referenceFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($reference.schemaVersion -ne 1 -or $reference.model.sha256 -cne $canonicalHash -or $reference.modelInferenceExecuted -ne $true) { throw 'Actual-model reference provenance mismatch.' }
    if (@($reference.samples).Count -lt 4 -or @($reference.samples).Count -gt 64) { throw 'Expected 4..64 actual-model samples.' }
    $seen = @{}
    foreach ($sample in $reference.samples) {
        if ($sample.rgbFile -notmatch '^[a-z0-9][a-z0-9_-]{0,63}\.rgb$' -or $seen.ContainsKey($sample.rgbFile)) { throw 'Unsafe/duplicate RGB filename.' }
        $seen[$sample.rgbFile] = $true
        $rgbFile = Get-Item -LiteralPath (Join-Path $bundle $sample.rgbFile)
        $expectedBytes = [long]$sample.width * [long]$sample.height * 3
        if ($sample.width -lt 1 -or $sample.width -gt 8192 -or $sample.height -lt 1 -or $sample.height -gt 8192 -or $expectedBytes -gt 50331648L -or $rgbFile.Length -ne $expectedBytes) { throw 'RGB dimensions/length mismatch.' }
        if ((Get-PrivateFileSha256 -LiteralPath $rgbFile.FullName) -cne $sample.rgbSha256) { throw 'RGB SHA256 mismatch.' }
        $uploads += [PSCustomObject]@{ Source = $rgbFile.FullName; Target = "no_backup/yolo/actual-golden/$($sample.rgbFile)"; ExpectedHash = $sample.rgbSha256; ExpectedBytes = $expectedBytes }
        # Comparison needs the original Python normalized input, not just a tensor hash.
        # These are private test fixtures, never application assets or workout recordings.
        if ($sample.inputTensorByteOrder -cne 'LITTLE_ENDIAN' -or
            $sample.inputTensorFile -cne "$($sample.id).input.f32" -or
            $sample.inputTensorFile -notmatch '^[a-z0-9][a-z0-9_-]{0,63}\.input\.f32$' -or
            $sample.inputTensorSha256 -notmatch '^[a-f0-9]{64}$') { throw 'Unsafe/missing input tensor fixture.' }
        $inputTensorFile = Get-Item -LiteralPath (Join-Path $bundle $sample.inputTensorFile)
        $inputTensorBytes = 4L * 3 * 640 * 640
        if ($inputTensorFile.Length -ne $inputTensorBytes -or
            (Get-PrivateFileSha256 -LiteralPath $inputTensorFile.FullName) -cne $sample.inputTensorSha256) { throw 'Input tensor fixture integrity mismatch.' }
        $uploads += [PSCustomObject]@{ Source = $inputTensorFile.FullName; Target = "no_backup/yolo/actual-golden/$($sample.inputTensorFile)"; ExpectedHash = $sample.inputTensorSha256; ExpectedBytes = $inputTensorBytes }
    }
    # Manifest is committed last: an interrupted upload never publishes a partial new bundle.
    $referenceInfo = Get-Item -LiteralPath $referenceFile
    $uploads += [PSCustomObject]@{ Source = $referenceFile; Target = 'no_backup/yolo/actual-golden/reference.json'; ExpectedHash = (Get-PrivateFileSha256 -LiteralPath $referenceFile); ExpectedBytes = $referenceInfo.Length }
}

if (-not $PSCmdlet.ShouldProcess("DEBUG private storage for $PackageName", 'Provision validated EXP02 model and optional actual Golden bundle')) { return }
# Stop only the selected, already run-as-validated DEBUG package before atomically replacing files.
# This avoids changing a model while its Interpreter is mapped/running. The app is not auto-started.
Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'am', 'force-stop', $PackageName)) | Out-Null
Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'mkdir', '-p', 'no_backup/yolo/actual-golden')) | Out-Null
foreach ($upload in $uploads) {
    $local = Get-Item -LiteralPath $upload.Source
    # Freeze the preflight identity, rather than accepting new bytes if a local file changes later.
    $expectedHash = $upload.ExpectedHash
    $expectedBytes = $upload.ExpectedBytes
    if ($local.Length -ne $expectedBytes -or (Get-PrivateFileSha256 -LiteralPath $local.FullName) -cne $expectedHash) { throw 'Local upload changed after preflight; refusing transfer.' }
    $temporary = "$($upload.Target).upload-$([Guid]::NewGuid().ToString('N'))"
    try {
        # Raw exec-in preserves NUL/Ctrl-Z/high bytes on Windows. Explicit byte count
        # lets dd flush its final partial block WITHOUT relying on raw transport EOF.
        # Unbounded dd lost a 142-byte tail; shell -T stopped at Ctrl-Z on this host.
        # The device-reported size and SHA remain mandatory before any final rename.
        Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-in', 'run-as', $PackageName, 'dd', "of=$temporary", 'bs=65536', 'iflag=count_bytes', "count=$expectedBytes", 'conv=fsync', 'status=none')) -InputFile $local.FullName | Out-Null
        Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'chmod', '600', $temporary)) | Out-Null
        $remoteSize = Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'wc', '-c', $temporary))
        $remoteHash = Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'sha256sum', $temporary))
        $actualBytes = ($remoteSize -split '\s+')[0]
        $actualHash = ($remoteHash -split '\s+')[0]
        if ($actualBytes -ne [string]$expectedBytes -or $actualHash -cne $expectedHash) {
            # Metadata only: never print model/media bytes or native device error text.
            throw "Private staged file integrity mismatch (MODEL_HASH_MISMATCH): expectedBytes=$expectedBytes actualBytes=$actualBytes expectedSHA256=$expectedHash actualSHA256=$actualHash. Final file was not replaced."
        }
        Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'mv', $temporary, $upload.Target)) | Out-Null
        $finalHash = Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'sha256sum', $upload.Target))
        $finalSize = Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'wc', '-c', $upload.Target))
        if (($finalHash -split '\s+')[0] -cne $expectedHash -or ($finalSize -split '\s+')[0] -ne [string]$expectedBytes) { throw 'Final private file integrity mismatch.' }
    } finally {
        # Only our exact temporary upload name is eligible; never delete the model or user files.
        Invoke-PrivateAdb -AdbArguments ($script:deviceArguments + @('exec-out', 'run-as', $PackageName, 'rm', '-f', $temporary)) | Out-Null
    }
}
Write-Output "Provisioned canonical EXP02 model: $canonicalBytes bytes, SHA256=$canonicalHash"
Write-Output "Private destination: $privateRoot/no_backup/yolo/$canonicalName"
Write-Output 'Contract state is unchanged. Provisioning does NOT activate prediction or promote YOLO to Primary.'
