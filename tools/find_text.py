import re, sys
path = 'build/ui2.xml'
kw = sys.argv[1] if len(sys.argv) > 1 else ''
raw = open(path, 'r', encoding='utf-8', errors='replace').read()
out = []
for m in re.finditer(r'<node[^>]*>', raw):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not t or not t.group(1).strip():
        continue
    text = t.group(1)
    if kw and kw not in text:
        continue
    if b:
        x1, y1, x2, y2 = map(int, b.groups())
        out.append(f"{text} | {x1},{y1}-{x2},{y2} | tap {(x1+x2)//2} {(y1+y2)//2}")
    else:
        out.append(text)
print("\n".join(out))
