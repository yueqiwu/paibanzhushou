from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'v5.png').convert('RGB').crop((0,1050,1080,1700)).save(b+'v5_crop.png')