from PIL import Image
base='D:/code/paibanzhushou_native/shots/'
im = Image.open(base+'e3.png').convert('RGB')
im.crop((760,600,1080,900)).save(base+'e3_zoom.png')
print(im.size)