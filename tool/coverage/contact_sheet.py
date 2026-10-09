"""One sheet of every opened Paseo tool step (build/coverage/paseo_*.png).

    python3 tool/coverage/contact_sheet.py [out.png]
"""
import glob
import sys

from PIL import Image, ImageDraw

out = sys.argv[1] if len(sys.argv) > 1 else "build/coverage/sheet.png"
files = sorted(glob.glob("build/coverage/paseo_*.png"))
cols, cell_w = 4, 412
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
    images.append((path.split("paseo_")[1][:-4], img.crop((0, 0, img.width, bottom))))
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
print(out, sheet.size)
