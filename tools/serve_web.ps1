
$root = 'D:\code\paiban_uniapp\unpackage\dist\build\web'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8099/')
$listener.Start()
Write-Output 'listening'
while ($listener.IsListening) {
  try { $ctx = $listener.GetContext() } catch { break }
  $req = $ctx.Request; $res = $ctx.Response
  $path = $req.Url.LocalPath
  if ($path -eq '/') { $path = '/index.html' }
  $file = Join-Path $root ($path.TrimStart('/'))
  if (Test-Path $file -PathType Leaf) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $ext = [System.IO.Path]::GetExtension($file)
    $map = @{'.html'='text/html'; '.js'='application/javascript'; '.css'='text/css'; '.png'='image/png'; '.jpg'='image/jpeg'; '.json'='application/json'; '.ttf'='font/ttf'; '.svg'='image/svg+xml'}
    if ($map.ContainsKey($ext)) { $res.ContentType = $map[$ext] }
    $res.ContentLength64 = $bytes.Length
    $res.OutputStream.Write($bytes, 0, $bytes.Length)
  } else { $res.StatusCode = 404 }
  $res.OutputStream.Close()
}
