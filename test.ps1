# Runs the dependency-free logic tests in src/test/java. No JUnit: PetTests has its own main and
# exits non-zero on failure. The test sources are never packaged into the plugin jar (build.ps1 only
# compiles src/main/java).
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$paperApi = Join-Path $root 'lib\paper-api-26.1.2.build.69-stable.jar'
if (-not (Test-Path -LiteralPath $paperApi)) {
    throw "Missing Paper API jar: $paperApi"
}

# Same compile classpath as build.ps1: Paper API + the downloaded BetterModel APIs + any extra libs.
$libs = @($paperApi)
$compileLibs = Join-Path $root 'target\compile-libs'
if (Test-Path $compileLibs) { $libs += Get-ChildItem $compileLibs -Filter *.jar | ForEach-Object { $_.FullName } }
$ext = 'C:\Users\Kamil Bura\Desktop\Neuer Ordner (6)\libraries'
if (Test-Path $ext) { $libs += Get-ChildItem $ext -Recurse -Filter *.jar | ForEach-Object { $_.FullName } }
$cp = ($libs | Select-Object -Unique) -join ';'

$out = Join-Path $env:TEMP 'bp-test-classes'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

$mainSources = Get-ChildItem -Path (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -Path (Join-Path $root 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
$sources = $mainSources + $testSources

$log = Join-Path $env:TEMP 'bp-test-javac.log'
& javac --release 21 -encoding UTF-8 -nowarn -cp $cp -d $out @sources 2>$log
if ($LASTEXITCODE -ne 0) {
    Write-Output 'TEST COMPILE FAILED:'
    Get-Content $log | Where-Object { $_ -match ': (Fehler|error):' } | Select-Object -First 40
    exit 1
}

& java -cp "$out;$cp" de.kamil.betterpets.PetTests
exit $LASTEXITCODE
