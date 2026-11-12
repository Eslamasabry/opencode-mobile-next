"""Mock (not app code): the home screen's Glass panes on the owner's real screenshot, 2026-11-11.
Keeps the real words and glyphs; replaces the page and the panes' faces. One full screen per image."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SRC = __import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "phone-home-dark-today.jpg")  # the owner's screenshot, beside this file
img = Image.open(SRC).convert("RGB")
W, H = img.size
A = np.asarray(img, dtype=np.float32) / 255.0
S = 4  # supersample for masks

PANES = [  # x0, y0, x1, y1, radius, kind
    (34, 412, 865, 1190, 44, "panel"),
    (34, 1235, 725, 1347, 44, "panel"),
    (34, 244, 316, 308, 32, "chip"),
    (350, 244, 557, 308, 32, "chip"),
    (575, 244, 709, 308, 32, "chip"),
]
DIVIDERS = [(152, 568, 865), (152, 724, 865), (152, 880, 865), (152, 1036, 865)]
K = W / 900.0  # the coordinates above were read off a 900 px wide view of the screenshot
PANES = [(x0 * K, y0 * K, x1 * K, y1 * K, r * K, kind) for x0, y0, x1, y1, r, kind in PANES]
DIVIDERS = [(int(x0 * K), int(round(y * K)), int(x1 * K)) for x0, y, x1 in DIVIDERS]


def blur(a, sigma):
    from scipy.ndimage import gaussian_filter
    return np.stack([gaussian_filter(a[..., c], sigma, mode="nearest") for c in range(a.shape[2])], axis=-1)


def blur1(m, sigma):
    return blur(m[..., None], sigma)[..., 0]


def rrect_mask(x0, y0, x1, y1, r, grow=0.0):
    im = Image.new("L", (W * S, H * S), 0)
    ImageDraw.Draw(im).rounded_rectangle(((x0 - grow) * S, (y0 - grow) * S, (x1 + grow) * S, (y1 + grow) * S), (r + grow) * S, fill=255)
    return np.asarray(im.resize((W, H), Image.LANCZOS), dtype=np.float32) / 255.0


# 1. the words and glyphs: everything clearly brighter than the page
maxc = A.max(-1)
fg = np.clip((maxc - 0.20) / 0.26, 0, 1)
fg = fg * fg * (3 - 2 * fg)
# the old rims and dividers are not words: mask thin bands along them
band = np.zeros((H, W), np.float32)
for x0, y0, x1, y1, r, _ in PANES:
    band = np.maximum(band, rrect_mask(x0, y0, x1, y1, r, 2.5) - rrect_mask(x0, y0, x1, y1, r, -3.5))
for x0, y, x1 in DIVIDERS:
    band[y - 3:y + 4, x0 - 2:x1 + 2] = 1
band[int(250 * K):int(302 * K), int(326 * K):int(340 * K)] = 1  # the small separator between the chips
fg = fg * (1 - np.clip(band * 1.5, 0, 1))
fgm = np.clip(blur1((fg > 0.02).astype(np.float32), 2.0) * 4, 0, 1)  # dilated support
# the old page under the words (normalised blur of everything that is not words, rims or dividers)
keep = 1 - np.clip(fgm + band, 0, 1)
old_bg = blur(A * keep[..., None], 18) / np.maximum(blur1(keep, 18), 1e-3)[..., None]
old_bg = old_bg * (1 - keep[..., None]) + A * keep[..., None]
old_bg = blur(old_bg, 3)
delta = np.clip(A - old_bg, 0, 1) * fgm[..., None]  # what the words add to the page

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)


def glow_page(strength):
    """A page with shape in it: the app's own green glow, with a few soft-edged lights."""
    page = old_bg.copy()
    if strength <= 0:
        return page
    lights = [  # cx, cy, radius, rgb, softness
        (120, 520, 420, (0.10, 0.62, 0.36), 0.55),
        (820, 980, 380, (0.05, 0.42, 0.50), 0.50),
        (260, 1500, 460, (0.12, 0.55, 0.30), 0.60),
        (760, 300, 260, (0.30, 0.70, 0.45), 0.45),
        (520, 1180, 220, (0.55, 0.85, 0.55), 0.35),
    ]
    add = np.zeros_like(page)
    for cx, cy, r, rgb, soft in lights:
        d = np.hypot(xx - cx * K, yy - cy * K) / (r * K)
        f = np.clip((1 - d) / soft, 0, 1)
        f = f * f * (3 - 2 * f)
        add += f[..., None] * np.array(rgb, np.float32) * 0.34
    return np.clip(page + add * strength, 0, 1)


def sat(a, s):
    l = (a * np.array([0.213, 0.715, 0.072], np.float32)).sum(-1, keepdims=True)
    return np.clip(l + (a - l) * s, 0, 1)


def render(page_strength, face):
    """face: dict(fill, sheen, rim, lit, blur_dp) ; None = today's pane (the page's own darkness, a grey rim)."""
    page = glow_page(page_strength)
    out = page.copy()
    for x0, y0, x1, y1, r, kind in PANES:
        m = rrect_mask(x0, y0, x1, y1, r)
        inner = rrect_mask(x0, y0, x1, y1, r, -2.2)
        ring = np.clip(m - inner, 0, 1)
        f = face or dict(fill=0.0, sheen=0.0, rim=0.20, lit=0.0, blur=14)
        frost = sat(blur(page, f["blur"] * 3.1), 1.6)
        frost = frost * 0.86  # the pane's thin dark tint
        frost = frost + (1 - frost) * f["fill"]
        if f["sheen"] > 0:  # light along the top edge, fading down and away from the start side
            depth = np.clip((yy - y0) / ((150.0 if kind == "panel" else 60.0) * K), 0, 1)
            across = 1 - 0.45 * np.clip((xx - x0) / max(x1 - x0, 1), 0, 1)
            frost = frost + ((1 - depth) ** 2 * across * f["sheen"])[..., None]
        out = out * (1 - m[..., None]) + np.clip(frost, 0, 1) * m[..., None]
        # rim light: brightest where the edge faces the top start and the bottom end
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        nx = np.clip((np.abs(xx - cx) - ((x1 - x0) / 2 - r)) / r, 0, 1) * np.sign(xx - cx)
        ny = np.clip((np.abs(yy - cy) - ((y1 - y0) / 2 - r)) / r, 0, 1) * np.sign(yy - cy)
        # on straight edges take the nearer edge's normal
        top = np.abs(yy - y0) < 4
        bot = np.abs(yy - y1) < 4
        left = np.abs(xx - x0) < 4
        right = np.abs(xx - x1) < 4
        ny = np.where(top, -1.0, np.where(bot, 1.0, ny))
        nx = np.where(left, -1.0, np.where(right, 1.0, nx))
        n = np.maximum(np.hypot(nx, ny), 1e-3)
        lit = np.abs((nx / n) * -0.7071 + (ny / n) * -0.7071) ** 1.6
        rim = f["rim"] + f["lit"] * lit
        out = np.clip(out + (ring * rim)[..., None], 0, 1)
    # dividers inside the list panel
    for x0, y, x1 in DIVIDERS:
        out[y:y + 1, x0:x1] = np.clip(out[y:y + 1, x0:x1] + 0.07, 0, 1)
    return np.clip(out + delta, 0, 1)


def save(a, name, label):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    try:
        f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 36)
    except Exception:
        f = ImageFont.load_default()
    tw = d.textlength(label, font=f)
    d.rounded_rectangle((W / 2 - tw / 2 - 26, H - 320, W / 2 + tw / 2 + 26, H - 250), 35, fill=(255, 214, 10))
    d.text((W / 2 - tw / 2, H - 304), label, font=f, fill=(0, 0, 0))
    im.save(name, quality=92)


if __name__ == "__main__":
    which = sys.argv[1:] or ["0", "1", "2", "3"]
    FACE = dict(fill=0.055, sheen=0.085, rim=0.10, lit=0.42, blur=14)
    LIGHT = dict(fill=0.03, sheen=0.05, rim=0.10, lit=0.30, blur=14)
    if "0" in which:
        save(render(0.0, None), "home-0-rebuilt-now.png", "0 · today (rebuilt, to check the mock)")
    if "1" in which:
        save(render(0.0, FACE), "home-1-pane-face.png", "1 · the pane stands on its own")
    if "2" in which:
        save(render(1.0, None), "home-2-page.png", "2 · the page has light in it")
    if "3" in which:
        save(render(0.6, LIGHT), "home-3-both.png", "3 · both, lightly")
