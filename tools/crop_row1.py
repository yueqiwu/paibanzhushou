
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\fix3_home.png")
im.crop((640, 1620, 1240, 1830)).resize((1800, 630), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\crop_row1.png")
