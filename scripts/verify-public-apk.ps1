param([Parameter(Mandatory=$true)][string]$ApkPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifactPath = (Resolve-Path -LiteralPath $ApkPath).Path
$archive = [System.IO.Compression.ZipFile]::OpenRead($artifactPath)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
    $blocked = @($entries | Where-Object { $_ -match '(?i)^assets/.*\.(tflite|pt|pth|onnx)$|^assets/.*yolo' })
    if ($blocked.Count -gt 0) { throw 'Unreviewed model asset found in APK; publication blocked.' }
    if ($entries -notcontains 'assets/pose_landmarker_lite.task') { throw 'Required MediaPipe task is missing.' }
    'PASS: no loose unreviewed weights/YOLO assets; required MediaPipe task retained.'
} finally { $archive.Dispose() }
