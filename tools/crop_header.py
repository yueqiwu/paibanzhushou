
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\native_table_oct.png")
print(im.size)
# header area: preview y ~560-660 of 1918 -> scale 2800/1918 = 1.4599
s = im.size[1]/1918.0
box = (0, int(520*s), im.size[0], int(660*s))
im.crop(box).resize(((box[2]-box[0]), (box[3]-box[1])*1), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\crop_header.png")
print("saved", box)
