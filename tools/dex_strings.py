
import zipfile, sys
z = zipfile.ZipFile(r"D:\code\paibanzhushou_native\app\build\outputs\apk\debug\app-debug.apk")
names = [n for n in z.namelist() if n.endswith(".dex")]
res = []
for n in names:
    data = z.read(n)
    for s in ("今天", "今日", "廿九"):
        res.append(f"{n}:{s}:{data.count(s.encode('utf-8'))}")
sys.stdout.buffer.write("\n".join(res).encode("utf-8"))
