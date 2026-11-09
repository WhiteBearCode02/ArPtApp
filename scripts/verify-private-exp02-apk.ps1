param([Parameter(Mandatory=$true)][string]$ApkPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath).Path)
try {
    $modelPath = 'assets/yolo_private/exp02.bin'
    $model = @($archive.Entries | Where-Object { $_.FullName -ceq $modelPath })
    if ($model.Count -ne 1 -or $model[0].Length -ne 9894030) { throw 'Exact private EXP02 asset missing or invalid.' }
    $stream = $model[0].Open()
    $hash = [System.Security.Cryptography.SHA256]::Create()
    try { $digest = ([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $hash.Dispose() }
    if ($digest -cne '464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749') { throw 'Wrong model hash.' }
    foreach ($name in @('ULTRALYTICS_AGPL-3.0.txt', 'private-exp02-apk-notice.txt')) {
        $entry = $archive.GetEntry("assets/yolo_private/$name")
        if (-not $entry) { throw "Missing notice: $name" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $actual = $reader.ReadToEnd().Replace("`r`n", "`n").TrimEnd() } finally { $reader.Dispose() }
        $local = if ($name -eq 'ULTRALYTICS_AGPL-3.0.txt') { "../licenses/$name" } else { "../docs/$name" }
        $expected = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $local)).Replace("`r`n", "`n").TrimEnd()
        if ($actual -cne $expected) { throw "Changed notice: $name" }
    }
    $forbidden = @($archive.Entries | Where-Object {
        $_.FullName -match '(?i)\.(tflite|pt|pth|onnx|rgb|f32|jpg|jpeg)$|local_validation/|reference_results\.json|exp03.*\.(bin|tflite)$|\.(env|jks|keystore)$'
    })
    if ($forbidden.Count) { throw 'Unexpected model/private material in personal APK.' }
    'PASS: OWNER-ONLY APK; exact EXP02 bytes and complete notices; no loose/EXP03 models or private validation fixtures.'
} finally { $archive.Dispose() }
