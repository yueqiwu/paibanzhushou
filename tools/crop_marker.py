
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\marker.png")
im.crop((1090, 990, 1272, 1070)).resize((1092, 480), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\btn_marker.png")
