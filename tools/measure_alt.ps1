param([int]$Warm = 10, [int]$Swipes = 8, [int]$Y = 1800)
$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$s = '3B65CB029KV00000'
function SwipeLeft { & $adb -s $s shell input swipe 1000 $Y 250 $Y 110 | Out-Null }
function SwipeRight { & $adb -s $s shell input swipe 250 $Y 1000 $Y 110 | Out-Null }
for ($i = 0; $i -lt $Warm; $i++) {
  if ($i % 2 -eq 0) { SwipeLeft } else { SwipeRight }
  Start-Sleep -Milliseconds 150
}
# 回到 2026年10月附近：连点「今天」不可靠，改为左右交替不移动范围
Start-Sleep -Milliseconds 800
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou reset | Out-Null
Start-Sleep -Milliseconds 300
for ($i = 0; $i -lt $Swipes; $i++) {
  if ($i % 2 -eq 0) { SwipeLeft } else { SwipeRight }
  Start-Sleep -Milliseconds 150
}
Start-Sleep -Milliseconds 1500
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou | Select-String -Pattern 'Total frames|Janky frames|percentile|Missed Vsync|Slow UI thread|Slow issue|Frame deadline missed'
