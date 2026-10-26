
import re, sys
x = open(r"D:\code\paibanzhushou_native\shots\u3.xml", encoding="utf-8").read()
rows = []
for m in re.finditer(r'text="([^"]{1,10})"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
    t, x1, y1 = m.group(1), int(m.group(2)), int(m.group(3))
    rows.append((y1, x1, t))
rows.sort()
out = " || ".join(f"{t}@({x},{y})" for y, x, t in rows)
sys.stdout.buffer.write(out.encode("utf-8"))
