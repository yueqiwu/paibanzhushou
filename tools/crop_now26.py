
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\marker.png")
im.crop((250, 2620, 560, 2800)).resize((1240, 720), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\cell26_now.png")
