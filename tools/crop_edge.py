from PIL import Image
im = Image.open(r"D:/code/paibanzhushou_native/shots/g7.png").convert("RGB")
# 日历卡片左上角（含卡片边缘与页面背景）
im.crop((0, 1560, 420, 1900)).resize((840, 680), Image.LANCZOS).save(r"D:/code/paibanzhushou_native/shots/edge_cal.png")
# 标题卡右下角边缘
im.crop((900, 60, 1272, 320)).resize((744, 520), Image.LANCZOS).save(r"D:/code/paibanzhushou_native/shots/edge_hero.png")
print("ok")
