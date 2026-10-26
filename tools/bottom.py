import re
s = open('D:/code/paibanzhushou_native/shots/ui.xml', encoding='utf-8').read()
nodes = re.findall(r'<node\b[^>]*>', s)
rows = []
for n in nodes:
    b = re.search(r'\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    t = re.search(r'\btext="([^"]*)"', n)
    if not b: continue
    x1,y1,x2,y2 = map(int, b.groups())
    rows.append((y2, x1,y1,x2,y2, t.group(1) if t else ''))
rows.sort()
for r in rows[-8:]:
    print(r)
print('screen 2400')