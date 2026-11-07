# Build the YouTube stretch of the road from straights and round hairpins, then ease the curvature
# in and out (Gaussian smoothing of the heading along the road), and print control points for RP.
# usage: uv run --with numpy python ytbend.py [y_top R1 R2 apexR apexL sigma]
import sys, math
import numpy as np

a = [float(v) for v in sys.argv[1:]] or []
y_top, R1, R2, apexR, apexL, sigma = (a + [27, 35, 40, 652, 507, 9][len(a):])[:6]
P0, th0 = (488.0, 56.0), math.radians(-35.2)       # leave the old road here (heading measured on the old road)
B, thB = (700.0, 180.0), math.radians(13.9)        # and rejoin it here
ds = .25

def build(L1, Ra, Ltop, Lmid, Lbot, Rb):
    segs = [('line', L1), ('arc', Ra, -th0), ('line', Ltop), ('arc', R1, math.pi), ('line', Lmid),
            ('arc', R2, -math.pi), ('line', Lbot), ('arc', Rb, thB)]
    k = []
    for sg in segs:
        if sg[0] == 'line': n = int(round(sg[1] / ds)); k += [0.0] * n
        else:
            R, turn = sg[1], sg[2]; n = int(round(abs(turn) * R / ds)); k += [math.copysign(1 / R, turn)] * n
    return np.array(k)

def integrate(k, x0, y0, t0, sig):
    th = t0 + np.concatenate([[0], np.cumsum(k) * ds])[:-1]
    if sig > 0:          # smooth the heading; pad with the end headings so they stay put
        pad = int(4 * sig / ds); g = np.exp(-.5 * (np.arange(-pad, pad + 1) * ds / sig) ** 2); g /= g.sum()
        thp = np.concatenate([np.full(pad, th[0]), th, np.full(pad, th[-1])]); th = np.convolve(thp, g, 'valid')
    x = x0 + np.concatenate([[0], np.cumsum(np.cos(th)) * ds]); y = y0 + np.concatenate([[0], np.cumsum(np.sin(th)) * ds])
    return x, y, th

# straight lengths from the layout targets (before smoothing)
Ra = 70.0
rise = Ra * (1 - math.cos(th0))                      # height the first arc climbs
L1 = (P0[1] - y_top - rise) / math.sin(-th0)
xa = P0[0] + L1 * math.cos(th0) + Ra * math.sin(-th0)  # x where the top run starts
xs = apexR - R1                                       # top run ends where the right hairpin's centre is
Ltop = xs - xa
y_mid = y_top + 2 * R1
xm = apexL + R2
Lmid = xs - xm
y_bot = y_mid + 2 * R2
Rb = (B[1] - y_bot) / (1 - math.cos(thB))
Lbot = B[0] - Rb * math.sin(thB) - xm
print(f'L1 {L1:.1f} top run {xa:.1f}->{xs:.1f} mid y {y_mid:.1f} bot y {y_bot:.1f} Rb {Rb:.1f} Lbot {Lbot:.1f}')

k = build(L1, Ra, Ltop, Lmid, Lbot, Rb)
x, y, th = integrate(k, *P0, th0, sigma)
err = (B[0] - x[-1], B[1] - y[-1])
u = np.linspace(0, 1, len(x)); x += err[0] * u; y += err[1] * u   # spread the small end error along the stretch
print(f'end error before fix {err[0]:.2f},{err[1]:.2f}  end heading {math.degrees(th[-1]):.1f}')
print(f'right apex x {x[y<y_mid+3].max():.1f}  left apex x {x[(y>y_mid)&(y<y_bot)].min():.1f}  top y {y.min():.1f}  bottom y {y[x<680].max():.1f}')
# curvature profile check
kk = np.diff(th) / ds
print(f'max |k| {np.abs(kk).max():.4f} (1/R1 {1/R1:.4f}, 1/R2 {1/R2:.4f}); biggest step in k per px {np.abs(np.diff(kk)).max()/ds:.5f}')
# control points every 5 px, without the two end points (they are already in RP)
L = len(x) - 1; step = int(5 / ds)
pts = [(round(x[i], 1), round(y[i], 1)) for i in range(step, L - step // 2, step)]
open('ytbend.txt', 'w').write(','.join(f'[{px:g},{py:g}]' for px, py in pts))
print(len(pts), 'points')
