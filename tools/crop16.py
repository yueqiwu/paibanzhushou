from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
Image.open(b+'c4.png').convert('RGB').crop((0,640,1080,900)).save(b+'c4_crop.png')