
import io
p = r"D:\code\paibanzhushou_native\shots\framestats.txt"
txt = io.open(p, encoding="utf-8", errors="replace").read()
i = txt.find("---PROFILEDATA---")
lines = txt[i:].splitlines()
print(lines[0]); print(lines[1]); print(lines[2]); print(lines[3]); print(lines[4])
print("total lines", len(lines))
