$ErrorActionPreference="Stop"
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$front=Join-Path $root "web-manager\frontend"
if(-not(Test-Path(Join-Path $front "node_modules"))){& npm.cmd --prefix $front install}
& npm.cmd --prefix $front run build
Push-Location (Join-Path $root "web-manager")
try{
    $maven=Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if($maven){& $maven.Source spring-boot:run}
    elseif(Test-Path "C:\tmp\apache-maven-3.9.11\bin\mvn.cmd"){& "C:\tmp\apache-maven-3.9.11\bin\mvn.cmd" spring-boot:run}
    else{throw "Không tìm thấy Maven. Cài Maven 3.9+ và mở lại PowerShell."}
}finally{Pop-Location}
