$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root 'target\classes'
$jarFile = Join-Path $root 'target\better-pets-26.1.2-plugin.jar'
$paperApi = Join-Path $root 'lib\paper-api-26.1.2.build.69-stable.jar'
$externalLibraryRoot = 'C:\Users\Kamil Bura\Desktop\Neuer Ordner (6)\libraries'

if (-not (Test-Path -LiteralPath $paperApi)) {
    throw "Missing Paper API jar: $paperApi"
}

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}

New-Item -ItemType Directory -Force $classes | Out-Null
New-Item -ItemType Directory -Force (Split-Path -Parent $jarFile) | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
if ($sources.Count -eq 0) {
    throw 'No Java source files found.'
}

$classpathJars = @($paperApi)
if (Test-Path -LiteralPath $externalLibraryRoot) {
    $classpathJars += Get-ChildItem -Path $externalLibraryRoot -Recurse -Filter '*.jar' | ForEach-Object { $_.FullName }
}

$classpath = ($classpathJars | Select-Object -Unique) -join [IO.Path]::PathSeparator

& javac --release 21 -encoding UTF-8 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Copy-Item -Path (Join-Path $root 'src\main\resources\*') -Destination $classes -Recurse -Force

$jarCommand = Get-Command jar.exe -ErrorAction SilentlyContinue
if ($null -eq $jarCommand) {
    $fallbackJar = 'C:\Program Files\Java\jdk-25\bin\jar.exe'
    if (-not (Test-Path -LiteralPath $fallbackJar)) {
        throw 'jar.exe was not found.'
    }
    $jar = $fallbackJar
} else {
    $jar = $jarCommand.Source
}

if (Test-Path -LiteralPath $jarFile) {
    Remove-Item -LiteralPath $jarFile -Force
}

& $jar --create --file $jarFile -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Write-Host "Built $jarFile"
