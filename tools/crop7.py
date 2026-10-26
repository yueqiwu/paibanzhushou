from PIL import Image
base='D:/code/paibanzhushou_native/shots/'
Image.open(base+'drag3.png').convert('RGB').crop((0,1230,1080,1420)).save(base+'drag3_crop.png')