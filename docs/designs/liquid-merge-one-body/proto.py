"""SPIKE (not app code): the blend maths for one-body liquid merges, drawn with numpy so it can be judged in motion.

Left: today's kit (two panes with their own lens and rim, a constant-strength smooth union painted between them as a
frosted bridge). Right: the proposal (issue #104's idea): one signed-distance field for both shapes, blended with a
smooth minimum whose strength falls as the two shapes' edge directions line up, and one lens, tint and rim light run
round the whole outline. The same formulas port 1:1 to the AGSL shader.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 560, 760
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def font(size):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def background(scroll):
    """A dark chat page: lines of text and a few coloured blocks, scrolled by [scroll] px."""
    img = Image.new("RGB", (W, H), (14, 15, 18))
    d = ImageDraw.Draw(img)
    f = font(22)
    words = "the glass bends what lies behind it at the rim and stays calm in the middle so the words read".split()
    y = -int(scroll) % 44 - 44
    i = int(scroll // 44)
    while y < H + 44:
        line = " ".join(words[(i * 5 + j) % len(words)] for j in range(7))
        d.text((24, y), line, font=f, fill=(226, 228, 233))
        if i % 6 == 2:
            d.rounded_rectangle((300, y + 4, 520, y + 34), 12, fill=(52, 120, 246))
        if i % 9 == 5:
            d.rounded_rectangle((24, y + 4, 200, y + 34), 12, fill=(46, 204, 138))
        y += 44
        i += 1
    return np.asarray(img, dtype=np.float32) / 255.0


def blur(img, radius):
    pil = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    return np.asarray(pil, dtype=np.float32) / 255.0


def sd_box(cx, cy, hx, hy, r):
    r = min(r, hx, hy)
    qx = np.abs(XX - cx) - hx + r
    qy = np.abs(YY - cy) - hy + r
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def grad(d):
    gy, gx = np.gradient(d)
    n = np.maximum(np.hypot(gx, gy), 1e-5)
    return gx / n, gy / n


def smin_const(da, db, k):
    h = np.maximum(k - np.abs(da - db), 0) / k
    return np.minimum(da, db) - h * h * k * 0.25


def smin_facing(da, db, k, hold=None):
    """Smooth minimum whose strength falls as the two shapes' outward directions line up.

    [hold] = (x, y, radius, pull): where the drop came from. As the pull falls, the liquid lets go everywhere but
    there, so two flat edges part as one neck that thins at that point instead of a film that vanishes at once.
    """
    ax, ay = grad(da)
    bx, by = grad(db)
    facing = np.clip(0.5 - 0.5 * (ax * bx + ay * by), 0, 1)  # 1: the shapes face each other; 0: same direction
    ke = k * facing
    if hold is not None:
        hx, hy, hr, pull = hold
        # the held region is the whole screen at full pull and closes in on the point as the pull falls
        r = hr * pull / max(1 - pull, 1e-3)
        ke = ke * np.exp(-((XX - hx) ** 2 + (YY - hy) ** 2) / (r * r))
    ke = np.maximum(ke, 1e-3)
    h = np.maximum(ke - np.abs(da - db), 0) / ke
    return np.minimum(da, db) - h * h * ke * 0.25


def sample(img, x, y):
    x = np.clip(x, 0, W - 1.001)
    y = np.clip(y, 0, H - 1.001)
    x0 = x.astype(np.int32)
    y0 = y.astype(np.int32)
    fx = (x - x0)[..., None]
    fy = (y - y0)[..., None]
    return (img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx) * (1 - fy) + (img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx) * fy


RIM, BEND = 26.0, 34.0  # the lens: how deep it reaches in from the edge, and how far it bends (px; 2 px = 1 dp)
TINT = np.array([0.16, 0.17, 0.20], dtype=np.float32)


def glass(page, soft, d, tint=0.22, rim=RIM):
    """One body of glass inside d<0: the page behind it blurred, bent in the rim, tinted, with a rim light."""
    gx, gy = grad(d)
    depth = np.clip(-d / rim, 0, 1)
    bend = (1 - np.sqrt(np.clip(1 - (1 - depth) ** 2, 0, 1))) * BEND
    inside = sample(soft, XX - gx * bend, YY - gy * bend)
    inside = inside * (1 - tint) + TINT * tint
    # rim light: brightest where the edge faces the top start and the bottom end, as the library's highlight
    lit = np.abs(gx * -0.6 + gy * -0.8) ** 1.6
    line = np.clip(1 - np.abs(d + 1.2) / 1.4, 0, 1) * (0.25 + 0.75 * lit)
    inside = inside + line[..., None] * 0.75
    cover = np.clip(0.5 - d, 0, 1)[..., None]
    return page * (1 - cover) + np.clip(inside, 0, 1) * cover


def old(page, soft, da, db, k):
    """Today: each pane is its own glass; between them a frosted, tinted bridge with a light line on its free edges."""
    out = page
    if k >= 1:
        d = smin_const(da, db, k)
        fill = blur(page, 16) * 0.78 + TINT * 0.22
        cover = np.clip(0.5 - d, 0, 1)[..., None]
        out = out * (1 - cover) + fill * cover
        own = np.clip(1 - np.minimum(np.abs(da), np.abs(db)) / 2.5, 0, 1)
        line = np.clip(1 - np.abs(d + 0.75) / 1.25, 0, 1) * (1 - own)
        out = out + line[..., None] * 0.55
    out = glass(out, soft, da)
    out = glass(out, soft, db, rim=14.0)
    return np.clip(out, 0, 1)


def new(page, soft, da, db, k, hold=None):
    d = smin_facing(da, db, k, hold) if k >= 1 else np.minimum(da, db)
    return np.clip(glass(page, soft, d), 0, 1)


def ease(t):
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def scene_menu(t):
    """A menu drawn out of the bar's right end, then separating from it (the kit's menu merge)."""
    bar = (W / 2, 96, W / 2 - 24, 36, 36)
    e = ease(min(t / 0.7, 1))
    x0, y0, x1, y1 = lerp(W - 96, W - 300, e), lerp(72, 150, e), lerp(W - 40, W - 24, e), lerp(120, 470, e)
    menu = ((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2, lerp(24, 40, e))
    pull = 1 - ease(max(0.0, (t - 0.55) / 0.45))
    return bar, menu, pull, (W - 68, 132)


def scene_pill(t):
    """The jump pill rising out of the message box as a drop (the kit's pill merge)."""
    box = (W / 2, H - 110, W / 2 - 24, 64, 44)
    e = ease(min(t / 0.75, 1))
    cy = lerp(H - 150, H - 250, e)
    pill = (W / 2, cy, lerp(40, 92, e), lerp(18, 26, e), 26)
    pull = 1 - ease(max(0.0, (t - 0.6) / 0.4))
    return box, pill, pull, (W / 2, H - 174)


def label(img, text):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((12, H - 44, 12 + 13 * len(text) + 20, H - 12), 10, fill=(0, 0, 0))
    d.text((22, H - 39), text, font=font(20), fill=(255, 255, 255))


def render(name, scene, frames=72, reach=80.0):
    for i in range(frames * 2):
        t = i / (frames - 1) if i < frames else 1 - (i - frames) / (frames - 1)  # out, then back in
        t = min(max(t, 0.0), 1.0)
        a, b, pull, at = scene(t)
        page = background(i * 2.0)
        soft = blur(page, 8)
        da, db = sd_box(*a), sd_box(*b)
        k = reach * pull
        left = Image.fromarray((old(page, soft, da, db, k) * 255).astype(np.uint8))
        right = Image.fromarray((new(page, soft, da, db, k, (at[0], at[1], reach * 0.6, pull)) * 255).astype(np.uint8))
        label(left, "today")
        label(right, "one body")
        both = Image.new("RGB", (W * 2 + 8, H), (0, 0, 0))
        both.paste(left, (0, 0))
        both.paste(right, (W + 8, 0))
        both.save(os.path.join(OUT, f"{name}-{i:04d}.png"))


if __name__ == "__main__":
    which = sys.argv[2] if len(sys.argv) > 2 else "all"
    if which in ("all", "menu"):
        render("menu", scene_menu)
    if which in ("all", "pill"):
        render("pill", scene_pill)
