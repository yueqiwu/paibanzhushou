
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\uni_collapsed.png")
im.crop((60, 1700, 1240, 2200)).resize((2360, 1000), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\crop_uni_cells2.png")
