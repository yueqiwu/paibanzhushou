
import re, sys
x = open(r"D:\code\paibanzhushou_native\shots\u4.xml", encoding="utf-8").read()
rs = []
for m in re.finditer(r'text="([^"]{1,12})"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
    rs.append((m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))))
sel = [r for r in rs if r[0] in ("今天X", "今日", "今", "十九", "廿九", "今天")]
sys.stdout.buffer.write("\n".join(f"{a} [{b},{c}][{d},{e}]" for a, b, c, d, e in sel).encode("utf-8"))
