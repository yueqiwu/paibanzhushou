
from PIL import Image
im = Image.open(r"D:\code\paibanzhushou_native\shots\now.png")
im.crop((1130, 580, 1245, 650)).resize((920, 560), Image.LANCZOS).save(r"D:\code\paibanzhushou_native\shots\btn_zoom.png")
