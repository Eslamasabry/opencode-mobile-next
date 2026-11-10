"""SPIKE (not app code): the v2 design of docs/designs/liquid-merge-one-body.md, drawn with numpy to judge in motion.

Left: today's kit at its real motion (two panes; the frosted bridge only while the piece moves). Right: the joined
body. Each pane draws its own side of one field, with exact mixed directions (not normalised, so the bend fades in the
junction), rim, bend and tint mixed per pixel, each side's own frost. The pieces move on the kit's springs, shown four
times slower. 2 px = 1 dp.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 720, 800
DP = 2.0
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
INK = np.array([0.16, 0.17, 0.20], dtype=np.float32)


def font(size):
    p = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    return ImageFont.truetype(p, size) if os.path.exists(p) else ImageFont.load_default()


def background(scroll):
    img = Image.new("RGB", (W, H), (14, 15, 18))
    d = ImageDraw.Draw(img)
    f = font(26)
    words = "the glass bends what lies behind it at the rim and stays calm in the middle so the words read".split()
    y = -int(scroll) % 52 - 52
    i = int(scroll // 52)
    while y < H + 52:
        d.text((32, y), " ".join(words[(i * 5 + j) % len(words)] for j in range(8)), font=f, fill=(226, 228, 233))
        if i % 6 == 2:
            d.rounded_rectangle((380, y + 4, 660, y + 40), 14, fill=(52, 120, 246))
        if i % 9 == 5:
            d.rounded_rectangle((32, y + 4, 260, y + 40), 14, fill=(46, 204, 138))
        y += 52
        i += 1
    return np.asarray(img, dtype=np.float32) / 255.0


def blur(img, radius):
    pil = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    return np.asarray(pil, dtype=np.float32) / 255.0


def sdg_box(box):
    """A rounded box's signed distance and exact outward direction (unit length)."""
    cx, cy, hx, hy, r = box
    r = min(r, hx, hy)
    px, py = XX - cx, YY - cy
    wx, wy = np.abs(px) - (hx - r), np.abs(py) - (hy - r)
    sx, sy = np.where(px < 0, -1.0, 1.0), np.where(py < 0, -1.0, 1.0)
    g = np.maximum(wx, wy)
    qx, qy = np.maximum(wx, 0), np.maximum(wy, 0)
    l = np.maximum(np.hypot(qx, qy), 1e-6)
    out = g > 0
    d = np.where(out, l - r, g - r)
    gx = sx * np.where(out, qx / l, np.where(wx > wy, 1.0, 0.0))
    gy = sy * np.where(out, qy / l, np.where(wx > wy, 0.0, 1.0))
    return d.astype(np.float32), gx.astype(np.float32), gy.astype(np.float32)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def join(a, b, reach):
    """The v2 field. Returns d, the direction g (not normalised), and w: the partner's share for pane a."""
    da, ax, ay = sdg_box(a)
    db, bx, by = sdg_box(b)
    facing = np.clip(0.5 - 0.5 * (ax * bx + ay * by), 0, 1)
    ma, mb = min(a[4], a[2], a[3]), min(b[4], b[2], b[3])
    inside = (1 - smoothstep(0.5 * ma, ma, -da)) * (1 - smoothstep(0.5 * mb, mb, -db))
    k = np.maximum(reach * facing * inside, 1e-3)
    h = np.maximum(k - np.abs(da - db), 0) / (2 * k)
    d = np.minimum(da, db) - h * h * k
    wa = np.where(da <= db, h, 1 - h)  # the partner's share where pane a draws; also valid on b's side as a's share
    gx = ax * (1 - wa) + bx * wa
    gy = ay * (1 - wa) + by * wa
    return d, gx, gy, wa, da, db


def sample(img, x, y):
    x = np.clip(x, 0, W - 1.001)
    y = np.clip(y, 0, H - 1.001)
    x0, y0 = x.astype(np.int32), y.astype(np.int32)
    fx, fy = (x - x0)[..., None], (y - y0)[..., None]
    return (img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx) * (1 - fy) + (img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx) * fy


def rim_of(short_dp, rim_max, share):
    return min(rim_max, share * short_dp) * DP


def lens(soft, d, gx, gy, rim, bend, tint):
    """The library's lens on any field: depth along the true distance, the bend along g as it is."""
    glen = np.maximum(np.hypot(gx, gy), 0.05)
    depth = np.clip(-(d / glen) / rim, 0, 1)
    amount = (1 - np.sqrt(np.clip(1 - (1 - depth) ** 2, 0, 1))) * bend
    col = sample(soft, XX - gx * amount, YY - gy * amount)
    col = col * (1 - tint[..., None]) + INK * tint[..., None]
    lit = np.abs((gx / glen) * -0.7071 + (gy / glen) * -0.7071) ** 1.6
    line = np.clip(1 - np.abs(d / glen + 1.0) / 1.2, 0, 1) * (0.2 + 0.8 * lit)
    return np.clip(col + line[..., None] * 0.7, 0, 1)


def pane_alone(page, soft, box, spec):
    d, gx, gy = sdg_box(box)
    rim = rim_of(2 * min(box[2], box[3]) / DP, spec["rim"], spec["share"])
    col = lens(soft, d, gx, gy, rim, spec["bend"] * DP, np.full_like(d, spec["tint"]))
    cover = np.clip(0.5 - d, 0, 1)[..., None]
    return page * (1 - cover) + col * cover


def joined(page, softs, a, b, sa, sb, reach):
    """Both panes of one joined body: each draws its side (da <= db is a's) with its own frost."""
    d, gx, gy, wa, da, db = join(a, b, reach)
    ra = rim_of(2 * min(a[2], a[3]) / DP, sa["rim"], sa["share"])
    rb = rim_of(2 * min(b[2], b[3]) / DP, sb["rim"], sb["share"])
    mine = da <= db
    share_b = np.where(mine, wa, 1 - wa)  # how much of b's recipe applies at this pixel
    rim = ra * (1 - share_b) + rb * share_b
    bend = (sa["bend"] * (1 - share_b) + sb["bend"] * share_b) * DP
    tint = sa["tint"] * (1 - share_b) + sb["tint"] * share_b
    col_a = lens(softs[0], d, gx, gy, rim, bend, tint)
    col_b = lens(softs[1], d, gx, gy, rim, bend, tint)
    col = np.where(mine[..., None], col_a, col_b)
    cover = np.clip(0.5 - d / np.maximum(np.hypot(gx, gy), 0.05), 0, 1)[..., None]
    return page * (1 - cover) + col * cover


def today(page, softs, a, b, sa, sb, pull):
    """Today's kit: two panes, and while the piece moves a frosted, tinted bridge with a light line."""
    out = page
    k = 40 * DP * pull
    if k >= 1:
        da, _, _ = sdg_box(a)
        db, _, _ = sdg_box(b)
        h = np.maximum(k - np.abs(da - db), 0) / k
        d = np.minimum(da, db) - h * h * k * 0.25
        fill = blur(page, 16) * (1 - sa["tint"]) + INK * sa["tint"]
        cover = np.clip(0.5 - d, 0, 1)[..., None]
        out = out * (1 - cover) + fill * cover
        own = np.clip(1 - np.minimum(np.abs(da), np.abs(db)) / 2.5, 0, 1)
        out = out + (np.clip(1 - np.abs(d + 0.75) / 1.25, 0, 1) * (1 - own))[..., None] * 0.55
    out = pane_alone(out, softs[0], a, sa)
    return np.clip(pane_alone(out, softs[1], b, sb), 0, 1)


def spring(t, zeta, k):
    """Progress 0 -> 1 of a spring after t seconds."""
    w0 = math.sqrt(k)
    if zeta >= 1:
        return 1 - math.exp(-w0 * t) * (1 + w0 * t)
    wd = w0 * math.sqrt(1 - zeta * zeta)
    return 1 - math.exp(-zeta * w0 * t) * (math.cos(wd * t) + zeta * w0 / wd * math.sin(wd * t))


BAR = dict(blur=6, bend=34, rim=22, share=0.40, tint=0.22)
MENU = dict(blur=12, bend=48, rim=26, share=0.40, tint=0.26)
BOX = dict(blur=4, bend=34, rim=22, share=0.40, tint=0.24)
PILL = dict(blur=2, bend=12, rim=12, share=0.28, tint=0.24)


def scene_menu(p, overlap_dp):
    """The bar and a menu growing from 55 % about the bar's '...' control. p: the release spring's progress."""
    bar = (W / 2, 76 * DP / 2 + 24, W / 2 - 24, 28 * DP, 28 * DP)
    bar_bottom = bar[1] + bar[3]
    ax, ay = W - 24 - 28 * DP, bar_bottom  # the point it grows from: under the control, on the bar's edge
    x1, x0 = W - 24, W - 24 - 200 * DP
    y0 = bar_bottom - overlap_dp * DP
    y1 = y0 + 230 * DP
    s = 0.55 + 0.45 * p
    r = (ax + (x0 - ax) * s, ay + (y0 - ay) * s, ax + (x1 - ax) * s, ay + (y1 - ay) * s)
    menu = ((r[0] + r[2]) / 2, (r[1] + r[3]) / 2, (r[2] - r[0]) / 2, (r[3] - r[1]) / 2, 24 * DP)
    return bar, menu


def scene_pill(p, overlap_dp):
    """The message box and the jump pill rising 44 dp out of it. p: the release spring's progress."""
    box = (W / 2, H - 150, W / 2 - 24, 52 * DP, 28 * DP)
    top = box[1] - box[3]
    rest = top + overlap_dp * DP - 16 * DP  # the pill's centre at rest (its face is 32 dp high)
    cy = rest + (1 - p) * 44 * DP
    pill = (W / 2, cy, 76 * DP, 16 * DP, 16 * DP)
    return box, pill


def label(img, text):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((16, H - 52, 16 + 15 * len(text) + 24, H - 14), 12, fill=(0, 0, 0))
    d.text((28, H - 46), text, font=font(24), fill=(255, 255, 255))


def render(name, scene, sa, sb, reach_dp, today_gap_dp, frames_open=44, frames_hold=24, frames_close=20, slow=4.0):
    n = frames_open + frames_hold + frames_close
    for i in range(n):
        if i < frames_open + frames_hold:
            t = i / 30.0 / slow
            p = spring(t, 0.82, 700.0)
            pull = 1 - spring(t, 1.0, 800.0)  # today's pull: 1 at the first frame, easing to 0
        else:
            u = (i - frames_open - frames_hold) / max(frames_close - 1, 1)
            p = 1 - u * u * (3 - 2 * u)
            pull = 1 - p
        page = background(i * 2.5)
        softs = (blur(page, sa["blur"] * DP), blur(page, sb["blur"] * DP))
        a, b = scene(p, 4)
        right = Image.fromarray((np.clip(joined(page, softs, a, b, sa, sb, reach_dp * DP), 0, 1) * 255).astype(np.uint8))
        a0, b0 = scene(p, -today_gap_dp)
        left = Image.fromarray((today(page, softs, a0, b0, sa, sb, pull) * 255).astype(np.uint8))
        label(left, "today")
        label(right, "joined")
        both = Image.new("RGB", (W * 2 + 8, H), (0, 0, 0))
        both.paste(left, (0, 0))
        both.paste(right, (W + 8, 0))
        both.save(os.path.join(OUT, f"{name}-{i:04d}.png"))


if __name__ == "__main__":
    which = sys.argv[2] if len(sys.argv) > 2 else "all"
    if which in ("all", "menu"):
        render("menu", scene_menu, BAR, MENU, reach_dp=48, today_gap_dp=0)
    if which in ("all", "pill"):
        render("pill", scene_pill, BOX, PILL, reach_dp=32, today_gap_dp=8)
