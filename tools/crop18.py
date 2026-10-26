from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
im=Image.open(b+'a1.png').convert('RGB')
print(im.size)
im.crop((0,1850,1080,2400)).save(b+'a1_bot.png')
for y in range(1850,2400,50):
    print(y, im.getpixel((540,y)), im.getpixel((60,y)))