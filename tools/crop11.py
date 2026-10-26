from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'v7.png').convert('RGB').crop((60,760,1020,1520)).save(b+'v7_crop.png')