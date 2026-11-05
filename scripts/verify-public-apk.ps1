param([Parameter(Mandatory=$true)][string]$ApkPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifactPath = (Resolve-Path -LiteralPath $ApkPath).Path
$archive = [System.IO.Compression.ZipFile]::OpenRead($artifactPath)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
    $blocked = @($entries | Where-Object { $_ -match '(?i)\.(tflite|pt|pth|onnx|rgb)$|\.(input|output)\.f32$|(^|/)reference(\.json|_results\.json)$|local_validation/|^assets/.*yolo' })
    if ($blocked.Count -gt 0) { throw 'Unreviewed model asset found in APK; publication blocked.' }
    if ($entries -notcontains 'assets/pose_landmarker_lite.task') { throw 'Required MediaPipe task is missing.' }
    # AAPT2 can shorten release resource paths (res/raw/... -> res/gD.txt). Verify exact NOTICE
    # bytes, not a debug-only pathname, so neither optimization nor a missing notice is ignored.
    $notice = Get-Item -LiteralPath (Join-Path $PSScriptRoot '../app/src/main/res/raw/opencv_resize_notice.txt')
    $expectedNoticeHash = (Get-FileHash -LiteralPath $notice.FullName -Algorithm SHA256).Hash
    $noticeFound = $false
    foreach ($entry in $archive.Entries) {
        if ($entry.Length -ne $notice.Length) { continue }
        $stream = $entry.Open()
        $hasher = [System.Security.Cryptography.SHA256]::Create()
        try {
            $actualHash = ([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-', '')
            if ($actualHash -eq $expectedNoticeHash) { $noticeFound = $true; break }
        } finally { $stream.Dispose(); $hasher.Dispose() }
    }
    if (-not $noticeFound) { throw 'Required OpenCV resize source-license notice is missing or changed; publication blocked.' }
    'PASS: no private fixtures/loose weights; MediaPipe task and exact OpenCV source notice retained.'
} finally { $archive.Dispose() }
