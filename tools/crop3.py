from PIL import Image
base='D:/code/paibanzhushou_native/shots/'
for n in ['e1','e3']:
    Image.open(base+n+'.png').convert('RGB').crop((0,0,1080,1400)).resize((432,560)).save(base+n+'_s.png')
print('ok')