from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
im=Image.open(b+'mt4.png').convert('RGB')
# scan header row for vertical border lines (color ~ (0,0,0,0.1) over white => ~230)
y=620
row=[im.getpixel((x,y)) for x in range(0,1080)]
edges=[x for x in range(1,1080) if sum(row[x])<sum(row[x-1])-12]
print('edges header y=620:', edges[:40])
y2=740
row2=[im.getpixel((x,y2)) for x in range(0,1080)]
edges2=[x for x in range(1,1080) if sum(row2[x])<sum(row2[x-1])-12]
print('edges row y=740:', edges2[:40])