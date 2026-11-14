import sys,re,xml.etree.ElementTree as ET
# density: 420dpi => 2.625 px/dp at 1080x2400 (scaled by wm density)
dens=float(sys.argv[2]) if len(sys.argv)>2 else 2.625
t=ET.parse(sys.argv[1]); bad=0
for n in t.iter('node'):
    if n.get('clickable')=='true' or n.get('checkable')=='true':
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
        w,h=(x2-x1)/dens,(y2-y1)/dens
        ok=w>=48 and h>=48
        bad+= not ok
        print(f"{'OK ' if ok else 'BAD'} {w:6.1f}x{h:6.1f}dp  {n.get('class').split('.')[-1]:12} text={n.get('text')!r} desc={n.get('content-desc')!r}")
print('violations',bad)
