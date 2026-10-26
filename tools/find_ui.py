import re, subprocess, sys
adb = r'C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe'
subprocess.run([adb,'shell','uiautomator','dump','/sdcard/ui.xml'], capture_output=True)
subprocess.run([adb,'pull','/sdcard/ui.xml',r'D:/code/paibanzhushou_native/shots/ui.xml'], capture_output=True)
s = open('D:/code/paibanzhushou_native/shots/ui.xml', encoding='utf-8').read()
nodes = re.findall(r'<node\b[^>]*>', s)
rows = []
for n in nodes:
    t = re.search(r'\btext="([^"]*)"', n)
    c = re.search(r'\bclickable="([^"]*)"', n)
    b = re.search(r'\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b: continue
    txt = t.group(1) if t else ''
    x1,y1,x2,y2 = map(int, b.groups())
    rows.append((txt, c.group(1) if c else '?', (x1+x2)//2, (y1+y2)//2, x1,y1,x2,y2))
pat = sys.argv[1] if len(sys.argv) > 1 else '.*'
for r in rows:
    if r[0] and re.search(pat, r[0]):
        print(f"{r[0]} | click={r[1]} | tap({r[2]},{r[3]}) | {r[4]},{r[5]},{r[6]},{r[7]}")