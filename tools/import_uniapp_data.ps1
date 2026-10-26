# 把 uniapp 版排班助手（com.paibanzhushou.wyq）里的排班数据写入原生 App（debug 包）
# 数据源见 tools/mk_prefs.py（人员、班次表、锚点、周期、自动休息）。
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\import_uniapp_data.ps1
$ErrorActionPreference = 'Stop'

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.bilu.paibanzhushou'
$dev = '3B65CB029KV00000'
$py  = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $env:PYTHONIOENCODING = 'utf-8'
    & $py tools\mk_prefs.py | Out-Null
    if (-not (Test-Path 'shots\paiban_store.xml')) { throw 'paiban_store.xml missing' }

    & $adb -s $dev shell am force-stop $pkg
    & $adb -s $dev push 'shots\paiban_store.xml' /data/local/tmp/paiban_store.xml | Out-Null
    # run-as 以应用 uid 写入，文件属主与 SELinux 上下文才正确
    & $adb -s $dev shell "run-as $pkg sh -c 'cat /data/local/tmp/paiban_store.xml > shared_prefs/paiban_store.xml'"
    & $adb -s $dev shell "run-as $pkg ls -l shared_prefs"
    & $adb -s $dev shell am start -n $pkg/.MainActivity
    Write-Output 'done'
} finally {
    Pop-Location
}
