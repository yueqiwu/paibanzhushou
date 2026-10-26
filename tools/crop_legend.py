from PIL import Image
im = Image.open(r"D:/code/paibanzhushou_native/shots/n1.png").convert("RGB")
im.crop((40, 1130, 1230, 1340)).resize((1190*1, 210*2), Image.LANCZOS).save(r"D:/code/paibanzhushou_native/shots/legend2.png")
print("ok")
