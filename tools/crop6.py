from PIL import Image
base='D:/code/paibanzhushou_native/shots/'
Image.open(base+'drag1.png').convert('RGB').crop((0,250,1080,700)).save(base+'drag1_crop.png')
print('ok')