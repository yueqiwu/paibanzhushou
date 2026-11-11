$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$dev = '3B65CB029KV00000'
$py = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$env:PYTHONIOENCODING = 'utf-8'

function Dump {
  & $adb -s $dev shell uiautomator dump /sdcard/ui2.xml | Out-Null
  & $adb -s $dev pull /sdcard/ui2.xml build\ui2.xml | Out-Null
}

# 回到顶部
for ($i = 0; $i -lt 8; $i++) { & $adb -s $dev shell input swipe 636 700 636 2400 150; Start-Sleep -Milliseconds 350 }
Start-Sleep -Milliseconds 600
Dump
$spot = & $py tools\find_text.py '展开'
if ($spot) {
  $t = ($spot -split 'tap ')[1]
  & $adb -s $dev shell input tap ($t -split ' ')[0] ($t -split ' ')[1]
  Start-Sleep -Milliseconds 900
}
for ($i = 0; $i -lt 14; $i++) {
  Dump
  $hit = & $py tools\find_text.py '闹钟权限'
  if ($hit) { break }
  & $adb -s $dev shell input swipe 636 1900 636 1400 400
  Start-Sleep -Milliseconds 550
}
Dump
& $py tools\find_text.py '闹钟'
Write-Output '----'
& $py tools\find_text.py '去设置'
Write-Output '----'
& $py tools\find_text.py '已开启'
