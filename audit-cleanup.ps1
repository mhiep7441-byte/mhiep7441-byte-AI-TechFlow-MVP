param([switch]$Apply)
$ErrorActionPreference="Stop"
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$targets=@(
    (Join-Path $root "web-manager\target"),
    (Join-Path $root "web-manager\frontend\node_modules")
)
$found=@()
foreach($path in $targets){
    if(Test-Path -LiteralPath $path){
        $resolved=(Resolve-Path -LiteralPath $path).Path
        if(-not $resolved.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw "Đường dẫn nằm ngoài dự án: $resolved"}
        $bytes=(Get-ChildItem -LiteralPath $resolved -File -Recurse -ErrorAction SilentlyContinue|Measure-Object Length -Sum).Sum
        $found+=[pscustomobject]@{Path=$resolved;SizeMB=[math]::Round($bytes/1MB,2)}
    }
}
$found|Format-Table -AutoSize
if(-not $Apply){Write-Host "Chỉ quét. Dùng .\audit-cleanup.ps1 -Apply để xóa đúng các cache/build trên.";exit 0}
foreach($item in $found){Remove-Item -LiteralPath $item.Path -Recurse -Force;Write-Host "Đã xóa cache có thể tạo lại: $($item.Path)"}
