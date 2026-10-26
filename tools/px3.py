from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
im=Image.open(b+'mt4.png').convert('RGB')
W,H=im.size
px=im.load()
# count near-white-but-gray pixels per column over a tall band
counts=[]
for x in range(W):
    c=0
    for y in range(690,2100,3):
        r,g,bl=px[x,y]
        if 195<=r<=245 and abs(r-g)<6 and abs(g-bl)<6:
            c+=1
    counts.append(c)
cand=[(x,c) for x,c in enumerate(counts) if c>300]
print(cand)