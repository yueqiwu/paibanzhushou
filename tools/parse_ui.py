import re
p = 'D:/code/paibanzhushou_native/shots/ui.xml'
s = open(p, encoding='utf-8').read()
print('len', len(s))
nodes = re.findall(r'<node\b[^>]*>', s)
print('nodes', len(nodes))
out = []
for n in nodes:
    t = re.search(r'\btext="([^"]*)"', n)
    c = re.search(r'\bclickable="([^"]*)"', n)
    b = re.search(r'\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not (t and b): continue
    click = c.group(1) if c else '?'
    txt = t.group(1)
    x1,y1,x2,y2 = map(int, b.groups())
    if click == 'true' or re.search(r'展开|收起|对比|保存|新增|默认人员|同事2|今天', txt):
        out.append(f'{txt} | click={click} | ({(x1+x2)//2},{(y1+y2)//2}) | {x1},{y1},{x2},{y2}')
print('\n'.join(out))