from PIL import Image
a = Image.open(r"D:/code/paibanzhushou_native/shots/phone1.png").convert("RGB")
b = Image.open(r"D:/code/paibanzhushou_native/shots/g1.png").convert("RGB")
print(a.size, b.size)
def pair(box, name):
    ca = a.crop(box); cb = b.crop(box)
    w, h = ca.size
    out = Image.new("RGB", (w, h * 2 + 10), (255, 0, 0))
    out.paste(ca, (0, 0)); out.paste(cb, (0, h + 10))
    out.save("D:/code/paibanzhushou_native/shots/" + name)
pair((40, 150, 1230, 350), "cmp_hero.png")
pair((40, 930, 1230, 1130), "cmp_cal.png")
def stats(img, box):
    px = list(img.crop(box).getdata())
    return tuple(round(sum(c[i] for c in px) / len(px), 1) for i in range(3))
print("hero inner old", stats(a, (700, 200, 1150, 330)), "new", stats(b, (700, 200, 1150, 330)))
