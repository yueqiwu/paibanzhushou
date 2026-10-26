param([int]$Warm = 10, [int]$Swipes = 6, [int]$Y = 1800, [switch]$Shots)
$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$s = '3B65CB029KV00000'
# warmup
for ($i = 0; $i -lt $Warm; $i++) {
  & $adb -s $s shell input swipe 1000 $Y 250 $Y 110 | Out-Null
  Start-Sleep -Milliseconds 150
}
# 回到今天的月，避免一直往后翻
& $adb -s $s shell input tap 1152 $Y-380 | Out-Null
Start-Sleep -Milliseconds 700
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou reset | Out-Null
Start-Sleep -Milliseconds 300
for ($i = 0; $i -lt $Swipes; $i++) {
  & $adb -s $s shell input swipe 1000 $Y 250 $Y 110 | Out-Null
  Start-Sleep -Milliseconds 150
}
Start-Sleep -Milliseconds 1500
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou | Select-String -Pattern 'Total frames|Janky frames|percentile|Missed Vsync|Slow UI thread|Slow issue|Frame deadline missed'
