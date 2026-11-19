import numpy as np, os
from PIL import Image, ImageDraw
R='/home/user/repo/app/src/main/res'
k=Image.open('keyed_full.png')
BB=(145,314,1105,953)  # logo bbox in source px
logo=k.crop(BB)  # 960x639
cx=(BB[0]+BB[2])/2; cy=(BB[1]+BB[3])/2
RAD=500.3
dens={'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}
def adaptive(canvas_px, target_r_px, img):
    s=target_r_px/RAD
    full=img  # full keyed source
    w,h=full.size
    sc=full.resize((round(w*s),round(h*s)),Image.LANCZOS)
    out=Image.new('RGBA',(canvas_px,canvas_px),(0,0,0,0))
    ox=round(canvas_px/2-cx*s); oy=round(canvas_px/2-cy*s)
    out.alpha_composite(sc,(ox,oy)) if ox>=0 and oy>=0 else out.paste(sc,(ox,oy),sc)
    return out
kn=np.asarray(k).copy()
mono=kn.copy(); mono[...,0:3]=255
mono_img=Image.fromarray(mono)
for d,f in dens.items():
    px=round(108*f)
    os.makedirs(f'{R}/mipmap-{d}',exist_ok=True)
    adaptive(px,33.5*f,k).save(f'{R}/mipmap-{d}/ic_launcher_foreground.png',optimize=True)
    adaptive(px,33.5*f,mono_img).save(f'{R}/mipmap-{d}/ic_launcher_monochrome.png',optimize=True)
# Play icon / full square with gradient background
def square(size, logo_frac=0.765):
    top=np.array([25,29,42]); bot=np.array([10,13,24])
    t=np.linspace(0,1,size)[:,None,None]
    g=(top*(1-t)+bot*t).repeat(size,axis=1)
    # subtle radial lift in the upper middle like the original
    yy,xx=np.mgrid[0:size,0:size]/size
    r=np.sqrt((xx-0.5)**2+(yy-0.25)**2)
    g=g+np.clip(0.35-r,0,None)[...,None]*14
    bg=Image.fromarray(np.clip(g,0,255).astype(np.uint8)).convert('RGBA')
    s=size*logo_frac/960
    sc=logo.resize((round(960*s),round(639*s)),Image.LANCZOS)
    # centre logo like the source (its centre is at y=633.5/1254)
    ox=round(size/2-sc.size[0]/2); oy=round(size*633.5/1254-sc.size[1]/2)
    bg.alpha_composite(sc,(ox,oy))
    return bg.convert('RGB')
square(512).save('play-icon-512.png',optimize=True)
# Splash layers: assign each logo pixel to one part by region (source coords)
src=np.asarray(k)
H,W,_=src.shape
yy,xx=np.mgrid[0:H,0:W]
parts={}
badge=(xx>=780)&(yy>=757)&(yy<=865)
tail=(xx>=950)&(yy>=458)&(yy<757)
top=(yy<458)&~tail
lcs=(yy>=458)&(yy<757)&~tail
cam=(xx>=380)&(xx<780)&(yy>=757)&(yy<=875)
bottom=(yy>=757)&~badge&~cam
for name,m in dict(top=top,bottom=bottom,lcs=lcs,tail=tail,cam=cam,badge=badge).items():
    layer=src.copy(); layer[~m]=0
    im=Image.fromarray(layer).crop(BB)
    im=im.resize((720,round(639*720/960)),Image.LANCZOS)
    os.makedirs(f'{R}/drawable-nodpi',exist_ok=True)
    im.save(f'{R}/drawable-nodpi/logo_part_{name}.png',optimize=True)
