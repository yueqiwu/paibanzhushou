import re, sys
xml = sys.argv[1] if len(sys.argv) > 1 else r'D:/code/paibanzhushou_native/shots/u5.xml'
d = open(xml, encoding='utf-8').read()
for n in re.findall(r'<node[^>]*>', d):
    if 'clickable="true"' not in n:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    t = re.search(r'text="([^"]*)"', n)
    label = t.group(1) if t else ''
    if y1 > 1850:
        print(f'{x1},{y1}-{x2},{y2}  w={x2-x1} h={y2-y1}  {label}')
