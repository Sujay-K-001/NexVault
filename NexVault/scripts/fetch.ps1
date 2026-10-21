# Downloads one file over HTTPS. Libraries are verified against the SHA-1 published next to them on Maven Central.
# Fonts (-NoHash) have no published checksum, so -Magic checks the file signature (e.g. wOF2) to reject error pages.
param([Parameter(Mandatory=$true)][string]$Url, [Parameter(Mandatory=$true)][string]$Out, [switch]$NoHash, [string]$Magic = '')
$ErrorActionPreference = 'Stop'
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    if (-not $Url.StartsWith('https://')) { throw 'Only https:// downloads are allowed.' }
    $tmp = "$Out.part"
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $tmp
    if ($NoHash) {
        if ($Magic) {
            $bytes = [IO.File]::ReadAllBytes($tmp)
            $head = if ($bytes.Length -ge $Magic.Length) { [Text.Encoding]::ASCII.GetString($bytes, 0, $Magic.Length) } else { '' }
            if ($head -ne $Magic) { Remove-Item $tmp -Force; throw "Unexpected file type for $Url" }
        }
        Move-Item -Force $tmp $Out
        exit 0
    }
    $resp = Invoke-WebRequest -UseBasicParsing -Uri "$Url.sha1"
    $expected = $resp.Content
    if ($expected -is [byte[]]) { $expected = [Text.Encoding]::ASCII.GetString($expected) }
    $expected = $expected.Trim().Split(' ')[0].ToLower()
    $actual = (Get-FileHash -Algorithm SHA1 -Path $tmp).Hash.ToLower()
    if ($expected -ne $actual) { Remove-Item $tmp -Force; throw "Checksum mismatch for $Url" }
    Move-Item -Force $tmp $Out
} catch {
    Write-Host "Download failed: $($_.Exception.Message)"
    exit 1
}
