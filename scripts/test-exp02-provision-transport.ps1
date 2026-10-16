param([Parameter(Mandatory=$true)][string]$PythonPath)
$ErrorActionPreference = 'Stop'

# Load ONLY the existing process runner AST, never the provisioning script body.
# This test has no adb/device/network dependency and uses no private media/model.
$transportRepository = Split-Path -Parent $PSScriptRoot
$transportScript = Join-Path $PSScriptRoot 'provision-exp02-model.ps1'
$parseTokens = $null
$parseErrors = $null
$transportAst = [System.Management.Automation.Language.Parser]::ParseFile($transportScript, [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw 'Provisioning script parse failed' }
$runner = $transportAst.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-PrivateAdb' }, $true)
if (-not $runner) { throw 'Missing binary process runner' }
. ([ScriptBlock]::Create($runner.Extent.Text))
$hashRunner = $transportAst.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-PrivateFileSha256' }, $true)
if (-not $hashRunner) { throw 'Missing read-only hash helper' }
. ([ScriptBlock]::Create($hashRunner.Extent.Text))
$script:resolvedAdb = (Resolve-Path -LiteralPath $PythonPath).Path
$receiver = 'scripts/fixtures/exp02-stdin-receiver.py'
$temporaryDirectory = Join-Path $transportRepository ('build/exp02-transport-test-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($temporaryDirectory) | Out-Null
$syntheticFile = Join-Path $temporaryDirectory 'synthetic.bin'
Push-Location $transportRepository
try {
    foreach ($size in @(1, 511, 512, 513, 65535, 65536, 65537, 9894030)) {
        $bytes = New-Object byte[] $size
        # Contains all bytes, NUL, LF, CR, Ctrl-Z and high bytes, including at EOF.
        $prefix = [Math]::Min(256, $size)
        for ($index = 0; $index -lt $prefix; $index++) { $bytes[$index] = [byte]$index }
        for ($filled = $prefix; $filled -lt $size; $filled *= 2) {
            [Array]::Copy($bytes, 0, $bytes, $filled, [Math]::Min($filled, $size - $filled))
        }
        [IO.File]::WriteAllBytes($syntheticFile, $bytes)
        $expectedHash = (Get-FileHash -LiteralPath $syntheticFile -Algorithm SHA256).Hash.ToLowerInvariant()
        $savedWhatIf = $WhatIfPreference
        try { $WhatIfPreference = $true; $dryRunHash = Get-PrivateFileSha256 -LiteralPath $syntheticFile }
        finally { $WhatIfPreference = $savedWhatIf }
        if ($dryRunHash -cne $expectedHash) { throw "Read-only dry-run hash mismatch for $size bytes" }
        $received = Invoke-PrivateAdb -AdbArguments @('-u', $receiver) -InputFile $syntheticFile
        if ($received -cne "$size $expectedHash") { throw "Binary transport mismatch for $size bytes: $received" }
        Write-Output "PASS: synthetic binary transport $size bytes, exact SHA256 and dry-run hash"
    }
} finally {
    Pop-Location
    # Only this test's two exact, freshly created targets; never recursive cleanup.
    if ([IO.File]::Exists($syntheticFile)) { [IO.File]::Delete($syntheticFile) }
    [IO.Directory]::Delete($temporaryDirectory)
}
