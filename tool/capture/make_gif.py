"""Stitches capture frames into the README GIF.

    python3 tool/capture/make_gif.py FRAMES_DIR video/public/demo.gif 390 2

(390 px wide, every 2nd frame = 15 fps.) The sandbox ffmpeg has no gif
encoder or palettegen, so Pillow does the job: one global palette built from
a spread of frames, no dithering (keeps the inter-frame deltas small), and
Pillow's own changed-rectangle optimisation. Needs `pip install pillow`.
"""
import glob, sys, os
from PIL import Image

frames_dir, out, width, step = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
paths = sorted(glob.glob(os.path.join(frames_dir, '*.png')))[::step]
if not paths:
    sys.exit('no frames')

def load(p):
    im = Image.open(p).convert('RGB')
    h = round(im.height * width / im.width)
    return im.resize((width, h), Image.LANCZOS)

# Global palette from a strip of sample frames spread across the sequence.
samples = [load(paths[i]) for i in range(0, len(paths), max(1, len(paths) // 24))]
strip = Image.new('RGB', (samples[0].width, samples[0].height * len(samples)))
for i, s in enumerate(samples):
    strip.paste(s, (0, i * s.height))
palette_img = strip.quantize(colors=256, method=Image.Quantize.MEDIANCUT)

frames = [load(p).quantize(palette=palette_img, dither=Image.Dither.NONE) for p in paths]
duration = round(1000 * step / 30)
frames[0].save(out, save_all=True, append_images=frames[1:], duration=duration,
               loop=0, optimize=True, disposal=1)
print(f'{len(frames)} frames, {duration} ms each -> {out} ({os.path.getsize(out)/1e6:.2f} MB)')
