param([int]$Swipes = 6, [int]$Y = 1800)
$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$s = '3B65CB029KV00000'
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou reset | Out-Null
Start-Sleep -Milliseconds 400
for ($i = 0; $i -lt $Swipes; $i++) {
  & $adb -s $s shell input swipe 1000 $Y 250 $Y 110 | Out-Null
  Start-Sleep -Milliseconds 150
}
Start-Sleep -Milliseconds 1500
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou framestats | Out-File -Encoding ascii 'D:\code\paibanzhushou_native\shots\framestats.txt'
Write-Output 'done'
