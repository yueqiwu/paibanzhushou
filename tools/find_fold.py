import re, sys
xml = sys.argv[1] if len(sys.argv) > 1 else r'D:/code/paibanzhushou_native/shots/u6.xml'
d = open(xml, encoding='utf-8').read()
out = []
for n in re.findall(r'<node[^>]*>', d):
    if 'clickable="true"' not in n:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if (y2 - y1) < 130:
        t = re.search(r'text="([^"]*)"', n)
        out.append((y1, f'{x1},{y1}-{x2},{y2} w={x2-x1} h={y2-y1} text={t.group(1) if t else ""}'))
for _, line in sorted(out):
    print(line)
