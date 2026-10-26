
from PIL import Image
from collections import Counter
im = Image.open(r"D:\code\paibanzhushou_native\shots\uni_collapsed.png").convert("RGB")
px = im.load()
def dump(x0,x1,y0,y1,label):
    c = Counter()
    for x in range(x0,x1):
        for y in range(y0,y1):
            c[px[x,y]] += 1
    dark = [(k,v) for k,v in c.most_common(400) if sum(k) < 600]
    print(label, dark[:12])
dump(660, 700, 1850, 1970, "digit8")
dump(700, 745, 1850, 1970, "lunar8")
