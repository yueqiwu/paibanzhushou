from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'h1.png').convert('RGB').crop((0,1550,1080,2100)).save(b+'h1_crop.png')