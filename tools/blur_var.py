from PIL import Image, ImageFilter
im = Image.open(r"D:/code/paibanzhushou_native/shots/g3.png").convert("L")
def var(box):
    p = list(im.crop(box).getdata())
    m = sum(p)/len(p)
    return round(m,1), round((sum((v-m)**2 for v in p)/len(p))**0.5, 2)
# 卡片内部空白区（日历卡片左下空白，无文字）
print("inside card  ", var((80, 1560, 300, 1640)))
# 卡片外部同样的背景区（日历卡片左侧的页面背景，有白点阵）
print("outside card ", var((20, 900, 240, 980)))
# 用高反差保留看细节能量：边缘像素比
def detail(box):
    c = im.crop(box).filter(ImageFilter.FIND_EDGES)
    p = list(c.getdata())
    return round(sum(p)/len(p), 2)
print("edge inside ", detail((80, 1560, 300, 1640)))
print("edge outside", detail((20, 900, 240, 980)))
