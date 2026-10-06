import sys, glob, re, os
from PIL import Image, ImageDraw
d, name = sys.argv[1], sys.argv[2]
views = sys.argv[3:] or ('quarter', 'front')
for view in views:
    fs = sorted(glob.glob(f'{d}/{name}_{view}_*.png'))
    if not fs:
        continue
    ims = [Image.open(f) for f in fs]; w, h = ims[0].size; cols = int(os.environ.get('COLS', 7))
    sh = Image.new('RGB', (w * cols, h * ((len(ims) + cols - 1) // cols)), 'white')
    for i, (f, im) in enumerate(zip(fs, ims)):
        ImageDraw.Draw(im).text((6, 6), re.findall(r'_(\d+)\.png', f)[0], fill='white')
        sh.paste(im, ((i % cols) * w, (i // cols) * h))
    sh.save(f'{d}/../sheet_{name}_{view}.png')
