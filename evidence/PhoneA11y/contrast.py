import re,sys
s=open('shared/android/src/main/kotlin/ai/hey/designsystem/token/HeyTokens.kt').read()
def block(name):
    i=s.find('val %s = HeyColors('%name); j=s.find('\n)',i)
    d={}
    for m in re.finditer(r'(\w+) = Color\(0x([0-9A-Fa-f]{8})\)',s[i:j]):
        d[m.group(1)]=tuple(int(m.group(2)[k:k+2],16) for k in (2,4,6,0))  # r g b a
    return d
def blend(fg,bg):
    a=fg[3]/255; return tuple(round(fg[k]*a+bg[k]*(1-a)) for k in range(3))+(255,)
def lum(c):
    f=lambda v:(v/255/12.92) if v/255<=0.03928 else ((v/255+0.055)/1.055)**2.4
    return .2126*f(c[0])+.7152*f(c[1])+.0722*f(c[2])
def cr(a,b):
    x,y=lum(a),lum(b); 
    if x<y:x,y=y,x
    return (x+.05)/(y+.05)
rows=[]
for theme,n in (('light','HeyLightColors'),('dark','HeyDarkColors')):
    c=block(n); bgs=['bgPage','bgSurface','bgRaised','secondaryContainer','infoContainer','sceneSand','sceneLilac','scenePeach','dangerContainer','bgSunken']
    for fg in ['fgDefault','fgMuted','fgSubtle','danger','primary']:
        for bg in bgs:
            rows.append((theme,fg,bg,cr(blend(c[fg],c[bg]),c[bg])))
    for fg,bg in [('fgOnPrimary','primary'),('fgOnDanger','danger'),('fgOnSecondary','buttonSecondary'),('fgOnAccent','accentContainer'),('fgDefault','accentContainer'),('fgDisabled','bgSunken'),('fgDisabled','bgPage')]:
        rows.append((theme,fg,bg,cr(c[fg],c[bg])))
print('| theme | text | on | ratio | ok(>=4.5) |\n|---|---|---|---|---|')
for t,f,b,r in rows: print(f'| {t} | {f} | {b} | {r:.2f} | {"yes" if r>=4.5 else "NO"} |')
