from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'v9.png').convert('RGB').crop((0,900,1080,1500)).save(b+'v9_crop.png')