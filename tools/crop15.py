from PIL import Image
b='D:/code/paibanzhushou_native/shots/'
for n in ['c2','c3']:
    Image.open(b+n+'.png').convert('RGB').crop((0,640,1080,900)).save(b+n+'_crop.png')