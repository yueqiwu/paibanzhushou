from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'b3.png').convert('RGB').crop((0,380,1080,1000)).save(b+'b3_crop.png')