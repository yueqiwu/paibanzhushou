import sys
from PIL import Image

COLS = [("red",(255,77,109)),("yellow",(255,209,102)),("green",(6,214,160)),("blue",(74,144,226)),("purple",(156,39,176)),("orange",(255,159,28))]

def scan(path, y0=0, y1=700):
    im = Image.open(path).convert("RGB")
    w,h = im.size
    px = im.load()
    found = {}
    for y in range(y0, min(y1,h), 2):
        for x in range(0, w, 2):
            p = px[x,y]
            for name,c in COLS:
                if abs(p[0]-c[0])<6 and abs(p[1]-c[1])<6 and abs(p[2]-c[2])<6:
                    found[name] = found.get(name,0)+1
                    break
    return found

if __name__ == "__main__":
    for p in sys.argv[1:]:
        print(p, scan(p))
