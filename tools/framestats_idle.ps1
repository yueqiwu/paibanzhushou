$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$s = '3B65CB029KV00000'
& $adb -s $s shell am start -n com.bilu.paibanzhushou/.MainActivity | Out-Null
Start-Sleep -Milliseconds 1800
& $adb -s $s shell dumpsys activity activities | Select-String -Pattern 'ResumedActivity:' | Select-Object -First 1
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou reset | Out-Null
Start-Sleep -Milliseconds 2000
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou framestats | Out-File -Encoding ascii 'D:\code\paibanzhushou_native\shots\framestats_idle.txt'
& $adb -s $s shell dumpsys gfxinfo com.bilu.paibanzhushou | Select-String -Pattern 'Total frames|Janky frames \('
