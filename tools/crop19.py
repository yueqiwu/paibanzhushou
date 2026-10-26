from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'b2.png').convert('RGB').crop((100,600,980,1500)).save(b+'b2_crop.png')