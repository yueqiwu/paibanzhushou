from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'c5.png').convert('RGB').crop((0,640,1080,1500)).save(b+'c5_crop.png')