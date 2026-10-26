from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'m4.png').convert('RGB').crop((0,200,1080,900)).save(b+'m4_crop.png')