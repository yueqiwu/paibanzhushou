from PIL import Image
base='D:/code/paibanzhushou_native/shots/'
im = Image.open(base+'member5.png').convert('RGB')
im.crop((0,2050,1080,2400)).save(base+'m5_bottom.png')
print(im.size)