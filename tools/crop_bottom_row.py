
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\fix3_home.png")
im.crop((40, 2380, 1240, 2760)).resize((2400, 760), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\crop_bottom_row.png")
