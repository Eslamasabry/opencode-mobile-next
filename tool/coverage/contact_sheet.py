"""One sheet of the opened coverage cases (build/coverage/<prefix>*.png).

    python3 tool/coverage/contact_sheet.py out.png [--cols N] [--dir D] prefix [prefix ...]

--dir D reads D/<prefix>*.png instead of build/coverage (for a "before" folder).

Prefixes: paseo_ (tool steps), items_ (timeline items), perm_ (permission and
question requests). Each cell is cropped to what was drawn and captioned with
the case id.
"""
import glob
import sys

from PIL import Image, ImageDraw

args = sys.argv[1:]
out = args.pop(0)
cols = 4
if "--cols" in args:
    i = args.index("--cols")
    cols = int(args[i + 1])
    del args[i : i + 2]
base = "build/coverage"
if "--dir" in args:
    i = args.index("--dir")
    base = args[i + 1]
    del args[i : i + 2]
files = []
for prefix in args:
    files += sorted(glob.glob(f"{base}/{prefix}*.png"))
cell_w = 412
images = []
for path in files:
    img = Image.open(path).convert("RGB")
    # The screenshot is a tall canvas; crop the empty bottom.
    bg = img.getpixel((img.width - 1, img.height - 1))
    bottom = img.height
    for y in range(img.height - 1, 0, -1):
        if any(img.getpixel((x, y)) != bg for x in range(0, img.width, 8)):
            bottom = min(img.height, y + 24)
            break
    name = path.split("/")[-1][:-4]
    images.append((name, img.crop((0, 0, img.width, bottom))))
rows = [images[i : i + cols] for i in range(0, len(images), cols)]
height = sum(max(i.height for _, i in row) + 22 for row in rows)
sheet = Image.new("RGB", (cols * cell_w, height), (40, 40, 40))
draw = ImageDraw.Draw(sheet)
y = 0
for row in rows:
    for c, (name, img) in enumerate(row):
        draw.text((c * cell_w + 8, y + 5), name, fill=(255, 220, 0))
        sheet.paste(img, (c * cell_w, y + 22))
    y += max(i.height for _, i in row) + 22
sheet.save(out)
print(out, sheet.size, len(images), "cells")
