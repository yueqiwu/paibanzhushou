from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'v3.png').convert('RGB').crop((0,0,1080,1400)).save(b+'v3_crop.png')