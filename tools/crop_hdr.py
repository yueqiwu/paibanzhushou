
from PIL import Image
specs = [("hdr2", r"D:\code\paibanzhushou_native\shots\final_home2.png", (1060, 570, 1272, 660)),
         ("hdr1", r"D:\code\paibanzhushou_native\shots\final_home.png", (1060, 570, 1272, 660))]
for tag, path, box in specs:
    im = Image.open(path)
    im.crop(box).resize(((box[2]-box[0])*4, (box[3]-box[1])*4), Image.LANCZOS).save(rf"D:\code\paibanzhushou_native\shots\{tag}.png")
