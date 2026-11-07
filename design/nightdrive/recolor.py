# Repaint three grey cars and brighten the M8 so all five read apart at TV distance.
# usage: uv run --with pillow --with numpy python recolor.py <in dir> <out dir>
import sys, colorsys
import numpy as np
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]


def hls_arrays(rgb):
    r, g, b = [rgb[..., i] / 255.0 for i in range(3)]
    mx, mn = np.max(rgb / 255.0, axis=-1), np.min(rgb / 255.0, axis=-1)
    l = (mx + mn) / 2
    d = mx - mn
    s = np.where(d == 0, 0, d / (1 - np.abs(2 * l - 1) + 1e-9))
    h = np.zeros_like(l)
    m = d > 0
    rc = np.where(m, (mx - r) / (d + 1e-9), 0)
    gc = np.where(m, (mx - g) / (d + 1e-9), 0)
    bc = np.where(m, (mx - b) / (d + 1e-9), 0)
    h = np.where(r == mx, bc - gc, np.where(g == mx, 2 + rc - bc, 4 + gc - rc))
    h = (h / 6.0) % 1.0
    return h, l, s


def to_rgb(h, l, s):
    def f(n):
        k = (n + h * 12) % 12
        a = s * np.minimum(l, 1 - l)
        return l - a * np.clip(np.minimum(k - 3, 9 - k), -1, 1)
    return np.stack([f(0), f(8), f(4)], -1) * 255


# id: (mask rule, target hue 0-1, saturation, lightness map)
PLAN = {
    'gt3rs':   ('grey', 48 / 360, .92, lambda L: .12 + .86 * L),        # racing yellow
    'gtr':     ('grey', 215 / 360, .10, lambda L: .50 + .48 * L),        # pearl white
    'vantage': ('grey', 152 / 360, .58, lambda L: .05 + .46 * L),        # racing green
    'm8':      ('blue', 222 / 360, .92, lambda L: .18 + .78 * L),        # bright blue
}
for cid in ['mustang', 'gt3rs', 'gtr', 'm8', 'vantage']:
    im = Image.open(f'{src}/{cid}.png').convert('RGBA')
    a = np.asarray(im).astype(np.float64)
    if cid in PLAN:
        rule, hue, sat, lmap = PLAN[cid]
        h, l, s = hls_arrays(a[..., :3])
        alpha = a[..., 3] > 8
        if rule == 'grey':
            m = alpha & (s < .30) & (l > .26) & (l < .93)
        else:
            m = alpha & (((h > 190 / 360) & (h < 260 / 360) & (s > .12)) | (s < .2)) & (l > .16) & (l < .9)
        nl = np.clip(lmap(l), 0, 1)
        out = to_rgb(np.full_like(l, hue), nl, np.full_like(l, sat))
        a[..., :3] = np.where(m[..., None], out, a[..., :3])
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(f'{dst}/{cid}.png')

sheet = Image.new('RGB', (1300, 800), '#0B0D17')
for i, cid in enumerate(['mustang', 'gt3rs', 'gtr', 'm8', 'vantage']):
    im = Image.open(f'{dst}/{cid}.png')
    sheet.paste(im, (10 + (i % 2) * 650, 10 + (i // 2) * 260), im)
sheet.save(f'{dst}/sheet.png')
print('ok')
