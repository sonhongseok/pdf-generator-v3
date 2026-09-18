# build.ps1 - V3 Build Script
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot      = $PSScriptRoot
$FrontendDir      = Join-Path $ProjectRoot "frontend"
$BackendDir       = Join-Path $ProjectRoot "backend"
$StaticDir        = Join-Path $BackendDir "src\main\resources\static"
$FrontendDistDir  = Join-Path $FrontendDir "dist"
$DistOutputDir    = Join-Path $ProjectRoot "dist"
$JarStageDir      = Join-Path $ProjectRoot "_jar_stage"
$AppName          = "OP_Certificate_Generator"
$AppVersion       = "1.0.0"
$AppVendor        = "OP"
$MainJarName      = "pdf-generator-backend-1.0.0.jar"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "======================================================" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host "======================================================" -ForegroundColor Cyan
}

Write-Step "Checking prerequisites"
Write-Host "  Node: $(node --version)" -ForegroundColor Green
Write-Host "  npm : $(npm --version)" -ForegroundColor Green

Write-Step "STEP 1/4 - Frontend Build"
Set-Location $FrontendDir
npm install --silent
npm run build

Write-Step "STEP 2/4 - Copy Static Files"
if (Test-Path $StaticDir) { Remove-Item -Recurse -Force $StaticDir }
New-Item -ItemType Directory -Path $StaticDir | Out-Null
Copy-Item -Path "$FrontendDistDir\*" -Destination $StaticDir -Recurse

Write-Step "STEP 3/4 - Backend Build"
Set-Location $BackendDir
& .\mvnw.cmd clean package -DskipTests

if (Test-Path $JarStageDir) { Remove-Item -Recurse -Force $JarStageDir }
New-Item -ItemType Directory -Path $JarStageDir | Out-Null
Copy-Item (Join-Path $BackendDir "target\$MainJarName") $JarStageDir

Write-Step "STEP 4/4 - jpackage App Image"
if (Test-Path $DistOutputDir) { Remove-Item -Recurse -Force $DistOutputDir }
New-Item -ItemType Directory -Path $DistOutputDir | Out-Null

jpackage `
    --type app-image `
    --name $AppName `
    --app-version $AppVersion `
    --vendor $AppVendor `
    --input $JarStageDir `
    --main-jar $MainJarName `
    --dest $DistOutputDir `
    --java-options "-Xmx512m" `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Dspring.profiles.active=prod" `
    --java-options "-Dapp.browser.launch=true"

Remove-Item -Recurse -Force $JarStageDir

Write-Step "STEP 5/5 - Copy External Files"

Write-Host "Removing bundled MSVC DLLs to avoid version conflict..."
Get-ChildItem -Path (Join-Path $DistOutputDir "$AppName\runtime\bin\*140*.dll") -ErrorAction SilentlyContinue | Remove-Item -Force

$AppRootDir = Join-Path $DistOutputDir $AppName
$VbsSource = Join-Path $BackendDir "docx2pdf.vbs"
if (Test-Path $VbsSource) { Copy-Item $VbsSource (Join-Path $AppRootDir "docx2pdf.vbs") }
$BatchVbsSource = Join-Path $BackendDir "docx2pdf_batch.vbs"
if (Test-Path $BatchVbsSource) { Copy-Item $BatchVbsSource (Join-Path $AppRootDir "docx2pdf_batch.vbs") }
$TemplateSource = Join-Path $ProjectRoot "certificate_template.docx"
$TemplateDest   = Join-Path $AppRootDir "certificate_template.docx"
if (Test-Path $TemplateSource) { Copy-Item $TemplateSource $TemplateDest }
New-Item -ItemType Directory -Path (Join-Path $AppRootDir "data") -Force | Out-Null

Get-ChildItem -Path $AppRootDir -Filter "*.ico" | Remove-Item -Force

Write-Step "STEP 6/6 - Copy Manuals and Zip"
$Manuals = @("사용설명서.pdf")
foreach ($m in $Manuals) {
    if (Test-Path (Join-Path $ProjectRoot $m)) {
        Copy-Item (Join-Path $ProjectRoot $m) $AppRootDir
    }
}

$ZipPath = Join-Path $DistOutputDir "OP_Certificate_Generator_v3.zip"
if (Test-Path $ZipPath) { Remove-Item -Force $ZipPath }
Compress-Archive -Path $AppRootDir -DestinationPath $ZipPath -Force

Write-Step "BUILD COMPLETE"
Set-Location $ProjectRoot