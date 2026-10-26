
from PIL import Image
for tag, path in (("a", r"D:\code\paibanzhushou_native\shots\final_home2.png"), ("b", r"D:\code\paibanzhushou_native\shots\final_home.png")):
    im = Image.open(path)
    im.crop((230, 1840, 540, 2100)).resize((930, 780), Image.LANCZOS).save(rf"D:\code\paibanzhushou_native\shots\cell26_{tag}.png")
