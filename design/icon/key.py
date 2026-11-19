import numpy as np
from PIL import Image
im=np.asarray(Image.open('src.png').convert('RGB')).astype(np.float32)
H,W,_=im.shape
# background estimate: vertical gradient sampled down the left side of the inner square
ys=np.arange(H)
bgcol=np.array([np.median(im[max(60,min(y,1190))-3:max(60,min(y,1190))+3, 60:110],axis=(0,1)) for y in range(H)])
bg=np.repeat(bgcol[:,None,:],W,axis=1)
mx=im.max(axis=2); bgmx=bg.max(axis=2)
a=np.clip((mx-bgmx-6)/(235-bgmx),0,1)
# region of logo only
mask=np.zeros((H,W),bool); mask[250:1010,110:1150]=True
a[~mask]=0
a3=a[...,None]
col=np.where(a3>0.01,(im-(1-a3)*bg)/np.maximum(a3,1e-3),0)
col=np.clip(col,0,255)
rgba=np.dstack([col,a*255]).astype(np.uint8)
ys,xs=np.where(a>0.3)
print('bbox',xs.min(),xs.max(),ys.min(),ys.max())
Image.fromarray(rgba).save('keyed_full.png')
np.save('bgcol.npy',bgcol)
print(bgcol[250],bgcol[627],bgcol[1000], bgcol[70], bgcol[1180])
