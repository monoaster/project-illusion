# Makes the car pictures the Home page loads (assets/home/cars/*.webp, 320 px wide) from the 640 px PNGs in
# cars/, and rewrites photos.js with each car's wheel positions, roof and rear deck at that size.
# usage: uv run --with pillow --with numpy python make_cars.py
import json, re
import numpy as np
from PIL import Image

W = 320
OUT = '../../launcher/app/src/main/assets/home/cars'
photos = json.loads(re.search(r'const PHOTO=(\[.*\]);', open('photos.js').read()).group(1))
for p in photos:
    src = Image.open(f"cars/{p['id']}.png").convert('RGBA')
    old_w = p['w']                                   # the size the numbers in photos.js are for
    im = src.resize((W, round(src.height * W / src.width)), Image.LANCZOS)
    im.save(f"{OUT}/{p['id']}.webp", 'WEBP', quality=86, method=6)
    a = np.asarray(im)[..., 3]

    def top(x):
        ys = np.nonzero(a[:, x] > 60)[0]
        return int(ys[0]) if len(ys) else im.height

    k = W / old_w
    p.update(w=W, h=im.height, src=f"cars/{p['id']}.webp",
             wheels=[[round(x * k, 1), round(y * k, 1), round(r * k, 1)] for x, y, r in p['wheels']],
             geo={'roof': min(top(x) for x in range(W)), 'deck': min(top(x) for x in range(int(W * .08)))})
open('photos.js', 'w').write('// The five car sprites (assets/home/cars/*.webp): wheel centres and radii, the roof and the rear deck in '
                             'sprite pixels, length in metres. Made by make_cars.py.\nconst PHOTO=' + json.dumps(photos, separators=(',', ':')) + ';\n')
print('ok')
