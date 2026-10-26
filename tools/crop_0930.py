
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\cur.png")
im.crop((560, 1160, 900, 1330)).resize((1360, 680), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\crop_0930.png")
