from PIL import Image, ImageDraw

BASE = 'D:/code/paibanzhushou_native/app/src/main/res'
src = Image.open(BASE + '/drawable/logo.png').convert('RGBA')
w, h = src.size
side = max(w, h)
sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
sq.paste(src, ((side - w) // 2, (side - h) // 2))

sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for d, s in sizes.items():
    img = sq.resize((s, s), Image.LANCZOS)
    img.save(BASE + '/mipmap-' + d + '/ic_launcher.png')
    mask = Image.new('L', (s * 4, s * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255)
    mask = mask.resize((s, s), Image.LANCZOS)
    rnd = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    rnd.paste(img, (0, 0), mask)
    rnd.save(BASE + '/mipmap-' + d + '/ic_launcher_round.png')
print('icons written')
