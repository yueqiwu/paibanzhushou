from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'m3.png').convert('RGB').crop((0,1420,1080,2400)).save(b+'m3_crop.png')