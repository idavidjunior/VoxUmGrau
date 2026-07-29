# Vox UmGrau - Build Script
# Uso: .\build.ps1 [-Install]

param([switch]$Install)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkPath = "$projectDir\app\build\outputs\apk\debug\app-debug.apk"
$verFile = "$projectDir\version.properties"
$gradleFile = "$projectDir\app\build.gradle.kts"

Write-Host "=== VOX UM GRAU - Build ===" -ForegroundColor Cyan

# Verifica gradle wrapper
if (-not (Test-Path "$projectDir\gradlew.bat")) {
    Write-Host "Gradle wrapper nao encontrado. Execute 'gradle wrapper' primeiro." -ForegroundColor Yellow
    Write-Host "Ou baixe de: https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" -ForegroundColor Yellow
    exit 1
}

# === Versionamento automático ===
if (Test-Path $verFile) {
    $ver = @{}
    Get-Content $verFile | ForEach-Object {
        $k, $v = $_ -split '=', 2
        if ($k -and $v) { $ver[$k.Trim()] = $v.Trim() }
    }
    $vc = [int]$ver['versionCode'] + 1
    $vn = $ver['versionName']
    $bc = [int]$ver['buildCount'] + 1
    Write-Host "[VERSAO] ${vn} (code $vc, build $bc)" -ForegroundColor Yellow
    # Atualiza version.properties
    @"
versionCode=$vc
versionName=$vn
buildCount=$bc
"@ | Out-File $verFile -Encoding utf8 -Force
    # Atualiza build.gradle.kts
    $content = Get-Content $gradleFile -Raw
    $content = $content -replace 'versionCode = \d+', "versionCode = $vc"
    $content = $content -replace 'versionName = "[^"]*"', "versionName = `"$vn`""
    Set-Content -Path $gradleFile -Value $content -Encoding utf8
} else {
    Write-Host "[AVISO] version.properties nao encontrado, sem versionamento" -ForegroundColor Yellow
}

Write-Host "[1/2] Compilando APK..." -ForegroundColor Yellow
Set-Location $projectDir
& .\gradlew.bat assembleDebug 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FALHA] Build falhou." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Build concluido." -ForegroundColor Green

if ($Install -and (Test-Path $apkPath)) {
    Write-Host "[2/2] Instalando via ADB..." -ForegroundColor Yellow
    adb install -r $apkPath 2>&1
    if ($?) {
        Write-Host "[OK] Instalado!" -ForegroundColor Green
    } else {
        Write-Host "[FALHA] ADB nao disponivel ou dispositivo nao encontrado." -ForegroundColor Red
    }
}

Write-Host "`nAPK: $apkPath" -ForegroundColor Cyan
