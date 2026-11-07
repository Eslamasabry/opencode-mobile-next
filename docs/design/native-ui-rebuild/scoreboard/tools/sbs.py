#!/usr/bin/env python3
"""Side-by-side: Flutter golden | native render | diff heat map, with a label strip on top.
usage: sbs.py <gate dir> <shot> <out.png> <label>"""
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageFilter

gate, shot, out, label = sys.argv[1:5]
f = Image.open(f'{gate}/{shot}-flutter.png').convert('RGB')
n = Image.open(f'{gate}/{shot}-native.png').convert('RGB')
w, h = max(f.width, n.width), max(f.height, n.height)


def pad(im):
    c = Image.new('RGB', (w, h), (255, 0, 255))
    c.paste(im, (0, 0))
    return c


fp, np_ = pad(f), pad(n)
d = ImageChops.difference(fp, np_).convert('L')
# heat map: grey native underlay, differences from yellow (small) to red (large)
base = np_.convert('L').point(lambda v: v // 3 + 30)
heat = Image.merge('RGB', (base, base, base))
px, dp = heat.load(), d.load()
for y in range(h):
    for x in range(w):
        v = dp[x, y]
        if v > 24:
            t = min(1.0, (v - 24) / 120)
            px[x, y] = (255, int(220 * (1 - t)), 0)
try:
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
    small = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 13)
except OSError:
    font = small = ImageFont.load_default()
gap, strip = 12, 58
W = 3 * w + 4 * gap
canvas = Image.new('RGB', (W, h + strip + gap), (24, 24, 28))
dr = ImageDraw.Draw(canvas)
dr.text((gap, 6), label, fill=(255, 255, 255), font=font)
for i, (t, im) in enumerate((('Flutter golden (FLUTTER_REF 8302de537)', fp), ('Native render (89ec49e55)', np_),
                             ('Diff heat map (yellow small, red large)', heat))):
    x = gap + i * (w + gap)
    dr.text((x, 32), t, fill=(200, 200, 210), font=small)
    canvas.paste(im, (x, strip))
if f.size != n.size:
    dr.text((W - 360, 6), f'SIZE MISMATCH flutter {f.width}x{f.height} vs native {n.width}x{n.height}', fill=(255, 90, 90), font=small)
canvas.save(out, optimize=True)
