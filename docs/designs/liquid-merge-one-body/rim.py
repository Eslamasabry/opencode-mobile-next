"""SPIKE (not app code): two rim lights on the same joined glass, over a colourful scrolling page.

Left: a fixed light, as the kit's liquid panes have on a plain page (brightest where the edge faces the top start and
the bottom end). Right: the reference's edge (ShaderToy s3XSzM, method only): the same fixed light, screen-blended
with a reflection of the bright content just OUTSIDE the glass, so the rim picks up colour as content passes it.
"""
import importlib.util
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

here = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]
sys.argv = [sys.argv[0], OUT]
spec = importlib.util.spec_from_file_location("p2", os.path.join(here, "proto2.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
W, H, DP, XX, YY = m.W, m.H, m.DP, m.XX, m.YY


def page_at(scroll):
    """A dark chat page with colour in it: words, a photo-like card, a code block, bright chips."""
    img = Image.new("RGB", (W, H), (14, 15, 18))
    d = ImageDraw.Draw(img)
    f = m.font(26)
    words = "the rim picks up the colour of what passes next to the glass and lets it go again".split()
    period = 1300
    for rep in (-1, 0, 1):
        base = rep * period - (scroll % period)
        for i in range(25):
            y = base + i * 52
            if -60 < y < H + 60 and i not in (5, 6, 7, 8, 14, 15, 16):
                d.text((32, y), " ".join(words[(i * 5 + j) % len(words)] for j in range(8)), font=f, fill=(226, 228, 233))
        y = base + 5 * 52
        for k in range(200):  # a photo: a warm-to-pink gradient card
            c = (255, int(120 + 0.5 * k), int(40 + 0.9 * k))
            d.rectangle((40, y + k, 420, y + k + 1), fill=c)
        d.rounded_rectangle((460, y + 20, 690, y + 180), 20, fill=(52, 120, 246))
        y = base + 14 * 52
        d.rounded_rectangle((32, y, 688, y + 150), 18, fill=(24, 34, 30))
        d.text((52, y + 18), "val glass = join(bar, menu)", font=f, fill=(90, 230, 150))
        d.text((52, y + 62), "draw(glass, alive = true)", font=f, fill=(255, 210, 90))
        d.rounded_rectangle((52, y + 106, 300, y + 134), 12, fill=(240, 80, 110))
        y = base + 21 * 52
        d.rounded_rectangle((300, y + 4, 680, y + 40), 14, fill=(46, 204, 138))
    return np.asarray(img, dtype=np.float32) / 255.0


LUMA = np.array([0.213, 0.715, 0.072], dtype=np.float32)

# Three materials on the same shapes. "sent": the first prototype's glass (no vibrancy step, a grey tint).
# "kit": the app's Glossy recipe as LiquidEdge has it (saturation 1.6, a thin tint), a fixed rim light.
# "vivid": more colour through the glass, a thinner tint, a rim that picks up what passes beside it and lets it glow a
# little into the glass, a soft gloss at the top start, and a slight colour fringe in the bend.
MATERIALS = (
    dict(name="first prototype", sat=1.0, tint_scale=1.0, lift=0.0, alive=False, glow=0.0, gloss=0.0, fringe=0.0, rim=0.75),
    dict(name="the app's recipe", sat=1.6, tint_scale=0.8, lift=0.0, alive=False, glow=0.0, gloss=0.0, fringe=0.0, rim=0.75),
    dict(name="vivid", sat=1.9, tint_scale=0.45, lift=0.05, alive=True, glow=0.30, gloss=0.16, fringe=0.10, rim=0.45),
)


def lens(soft, bloom, d, gx, gy, rim, bend, tint, mat):
    glen = np.maximum(np.hypot(gx, gy), 0.05)
    nx, ny = gx / glen, gy / glen
    dist = d / glen
    depth = np.clip(-dist / rim, 0, 1)
    amount = (1 - np.sqrt(np.clip(1 - (1 - depth) ** 2, 0, 1))) * bend
    if mat["fringe"] > 0:  # red bends a little less, blue a little more
        col = np.stack([m.sample(soft, XX - gx * amount * (1 + mat["fringe"] * (c - 1)), YY - gy * amount * (1 + mat["fringe"] * (c - 1)))[..., c] for c in range(3)], axis=-1)
    else:
        col = m.sample(soft, XX - gx * amount, YY - gy * amount)
    luma = (col * LUMA).sum(-1, keepdims=True)
    col = np.clip(luma + (col - luma) * mat["sat"] + mat["lift"], 0, 1)
    if mat.get("press"):
        # Words on the glass must read over bright content without more blur, more tint or a plate behind them:
        # the glass holds back only the LIGHT of what is bright behind it (a soft knee on luminance), keeping its
        # colour. Dark themes; a light theme lifts the dark end the same way.
        knee, slope = mat["press"]
        lum = (col * LUMA).sum(-1, keepdims=True)
        held = np.where(lum > knee, knee + (lum - knee) * slope, lum)
        col = col * (held / np.maximum(lum, 1e-4))
    t = (tint * mat["tint_scale"])[..., None]
    col = col * (1 - t) + m.INK * t
    if mat["gloss"] > 0:  # a soft gloss inside the rim where the edge faces the top start
        facing = np.clip(nx * -0.7071 + ny * -0.7071, 0, 1) ** 2
        col = col + (facing * (1 - depth) ** 2 * np.clip(-dist, 0, 1))[..., None] * mat["gloss"]
    band = 1.5 * DP
    edge = np.clip((dist + band) / band, 0, 1)
    cos_edge = (1 - np.cos(edge * np.pi / 2))[..., None]
    fixed = (np.abs(nx * -0.7071 + ny * -0.7071) ** 1.6)[..., None] * np.ones(3, dtype=np.float32) * mat["rim"]
    if not mat["alive"]:
        return np.clip(col + fixed * cos_edge, 0, 1)
    off = (12 + 4 * cos_edge[..., 0]) * DP
    refl = np.clip(m.sample(bloom, XX + nx * off, YY + ny * off), 0, 1)
    rl = (refl * LUMA).sum(-1, keepdims=True)
    refl = np.clip(rl + (refl - rl) * 1.5, 0, 1)
    merged = 1 - (1 - fixed) * (1 - np.clip(refl * 1.8, 0, 1))
    out = col * (1 - cos_edge) + np.maximum(col, merged) * cos_edge
    glow = np.clip(1 + dist / (10 * DP), 0, 1)[..., None] ** 2 * np.clip(-dist, 0, 1)[..., None]
    return np.clip(out + refl * glow * mat["glow"], 0, 1)


def words(img):
    """The words that sit on the glass in the app, so their reading over the content can be judged."""
    d = ImageDraw.Draw(img)
    d.text((96, 50), "Sunset palette for the light theme", font=m.font(28), fill=(240, 241, 244))
    d.text((96, 88), "project · This phone · Git", font=m.font(20), fill=(170, 174, 184))
    d.text((44, 68), "←", font=m.font(30), fill=(240, 241, 244))
    d.text((640, 62), "···", font=m.font(30), fill=(240, 241, 244))
    d.text((272, 508), "↓ Jump to latest", font=m.font(22), fill=(240, 241, 244))
    d.text((60, 600), "Ask OpenCode…", font=m.font(26), fill=(170, 174, 184))
    d.text((56, 676), "+", font=m.font(34), fill=(240, 241, 244))
    d.ellipse((618, 664, 678, 724), fill=(52, 199, 120))
    return img


def body(page, softs, bloom, a, b, sa, sb, reach, mat):
    d, gx, gy, wa, da, db = m.join(a, b, reach)
    ra = m.rim_of(2 * min(a[2], a[3]) / DP, sa["rim"], sa["share"])
    rb = m.rim_of(2 * min(b[2], b[3]) / DP, sb["rim"], sb["share"])
    mine = da <= db
    share_b = np.where(mine, wa, 1 - wa)
    rim = ra * (1 - share_b) + rb * share_b
    bend = (sa["bend"] * (1 - share_b) + sb["bend"] * share_b) * DP
    tint = sa["tint"] * (1 - share_b) + sb["tint"] * share_b
    col = np.where(mine[..., None], lens(softs[0], bloom, d, gx, gy, rim, bend, tint, mat), lens(softs[1], bloom, d, gx, gy, rim, bend, tint, mat))
    cover = np.clip(0.5 - d / np.maximum(np.hypot(gx, gy), 0.05), 0, 1)[..., None]
    return page * (1 - cover) + col * cover


def frame(scroll, v, frost=1.0):
    """frost scales every pane's blur (1 = the app's: bar 6 dp, message box 4 dp, pill 2 dp)."""
    mat = MATERIALS[v]
    page = page_at(scroll)
    wide = m.blur(page, 10 * DP)
    bloom = 1 - (1 - page) * (1 - np.clip((wide - 0.12) / 0.88, 0, 1))  # the bright parts of the page, spread
    bloom = m.blur(bloom, 5 * DP)
    out = page
    bar, menu = m.scene_menu(1.0, 4)
    softs = (m.blur(page, m.BAR["blur"] * DP * frost), m.blur(page, m.MENU["blur"] * DP * frost))
    # the bar alone at the top (no menu open while reading), the pill joined to the message box at the bottom
    lone = (bar[0], bar[1], bar[2], bar[3], bar[4])
    out = body(out, softs, bloom, lone, (bar[0], bar[1], 8.0, 8.0, 8.0), m.BAR, m.BAR, 0.0, mat)
    box, pill = m.scene_pill(1.0, 4)
    softs = (m.blur(page, m.BOX["blur"] * DP * frost), m.blur(page, m.PILL["blur"] * DP * frost))
    return np.clip(body(out, softs, bloom, box, pill, m.BOX, m.PILL, 32 * DP, mat), 0, 1)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    n = int(os.environ.get("FRAMES", "80"))
    runs = [x.split(":") for x in os.environ.get("RUNS", "2:1:vivid, the app's blur;2:0.35:vivid, a third of the blur").split(";")]
    i = 0
    for v, frost, name in runs:  # one full screen at a time, the same scroll for each
        for j in range(n):
            img = words(Image.fromarray((frame(j * 10.0, int(v), float(frost)) * 255).astype(np.uint8)))
            m.label(img, name)
            img.save(os.path.join(OUT, f"rim-{i:04d}.png"))
            i += 1
