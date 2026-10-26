import re,sys,xml.etree.ElementTree as ET
p = sys.argv[1] if len(sys.argv)>1 else r"D:/code/paibanzhushou_native/shots/ui.xml"
t = ET.parse(p)
for n in t.iter('node'):
    b = n.get('bounds','')
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
    if not m: continue
    x1,y1,x2,y2 = map(int, m.groups())
    txt = (n.get('text') or '')[:12]
    cd = (n.get('content-desc') or '')[:16]
    if 1500 <= y1 <= 2200 or (1000 <= y1 <= 2600 and (txt or cd)):
        print(f"y={y1}-{y2} x={x1}-{x2} clickable={n.get('clickable')} text={txt!r} desc={cd!r}")
