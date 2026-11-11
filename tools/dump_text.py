import re, sys
p = sys.argv[1] if len(sys.argv) > 1 else 'build/ui2.xml'
raw = open(p, 'r', encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node[^>]*>', raw):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if t and t.group(1).strip():
        print(t.group(1), '|', b.group(1)+','+b.group(2)+'-'+b.group(3)+','+b.group(4) if b else '')
