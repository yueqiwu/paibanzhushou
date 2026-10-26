from PIL import Image
base = 'D:/code/paibanzhushou_native/shots/'
for n in ['t1','t3']:
    im = Image.open(base + n + '.png').convert('RGB')
    im.crop((0,0,1080,1100)).resize((378,385)).save(base + n + '_small.png')
print('ok')