from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'v4.png').convert('RGB').crop((0,400,1080,760)).save(b+'v4_crop.png')