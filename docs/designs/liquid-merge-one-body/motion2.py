"""SPIKE (not app code): the motion of the joined glass (owner 2026-11-11).

"Make it drop down and then rebound to shape", then: "When menu is dropping it should rebound a bit and then start
filling to the left at resting line."

Menu out of the bar, in order:
1. DROP. A narrow column, the width of the control, falls from the bar along its right flank. Its length runs on a
   springy spring and passes the resting line (the menu's bottom edge) by about 8 %.
2. REBOUND. It turns and comes back up towards the resting line.
3. FILL. Just after the turn the glass fills to the left on a calm spring (no bounce sideways: liquid levels, it does
   not ring). The rows are uncovered as the glass reaches them.
Closing is the same in reverse and quick, with no bounce: it empties to the right, then the column is drawn up.
The pill does the same upwards: it rises narrow, passes its rest, turns, then widens.
"""
import importlib.util
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

here = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]
MODE = sys.argv[2] if len(sys.argv) > 2 else "slow"
spec = importlib.util.spec_from_file_location("craft", os.path.join(here, "craft.py"))
c = importlib.util.module_from_spec(spec)
sys.argv = ["craft.py", OUT]
spec.loader.exec_module(c)
m, rim, shape = c.m, c.rim, c.shape
DP = m.DP
c.MAT["press"] = (0.36, 0.22)  # words over bright content: the glass holds back its light
BOUNCE = float(os.environ.get("BOUNCE", "0.62"))  # the drop spring's damping ratio: lower = more rebound
DROP_K, FILL_K = 900.0, 900.0
COLUMN_DP = 56.0
INK, INK2 = (240, 241, 244), (214, 217, 224)
BAR_WORDS = [(96, 66, "Sunset palette for the light theme", 28, INK, 1), (96, 104, "project · This phone · Git", 20, INK2, 1), (44, 84, "←", 30, INK, 1), (640, 78, "···", 30, INK, 1)]
ROWS = ("Find", "Rename conversation", "Fork conversation", "Copy transcript", "Go to", "More")


def lerp(a, b, t):
    return a + (b - a) * t


def clamp01(x):
    return max(0.0, min(1.0, x))


def turn_time(zeta, k):
    """When a spring first reaches its peak (seconds)."""
    return float(np.pi / (np.sqrt(k) * np.sqrt(1 - zeta * zeta)))


def text_layer(img, items, clip_left=None):
    """items: (x, y, text, size, rgb, alpha). clip_left: none of these words is drawn left of this x (px)."""
    over = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    for x, y, t, size, rgb, a in items:
        if a > 0.01:
            d.text((x, y), t, font=m.font(size), fill=rgb + (int(255 * clamp01(a)),))
    if clip_left is not None and clip_left > 0:
        arr = np.array(over)
        edge = int(min(clip_left, img.size[0]))
        ramp = np.clip((np.arange(img.size[0]) - edge) / 28.0, 0, 1)  # the words ease in behind the front
        arr[:, :, 3] = (arr[:, :, 3] * ramp[None, :]).astype(np.uint8)
        over = Image.fromarray(arr)
    return Image.alpha_composite(img.convert("RGBA"), over).convert("RGB")


def menu_frame(scroll, t_open, t_close=None):
    page = rim.page_at(scroll)
    bloom = c.bloom_of(page)
    bar, rest = shape.BAR, shape.MENU
    bar_bottom, bar_right = bar[1] + bar[3], bar[0] + bar[2]
    full_w, full_h = 2 * rest[2], 2 * rest[3]
    if t_close is None:
        hp = m.spring(t_open, BOUNCE, DROP_K)                                              # 1 drop, 2 rebound
        calm = m.spring(t_open, 1.0, DROP_K)
        wp = m.spring(max(t_open - turn_time(BOUNCE, DROP_K) - 0.02, 0.0), 0.92, FILL_K)   # 3 fill, after the turn
    else:
        wp = 1 - m.spring(t_close, 1.0, 2400.0)                                            # empties to the right
        hp = 1 - m.spring(max(t_close - 0.07, 0.0), 1.0, 2000.0)                           # then is drawn up
        calm = hp
    hgt = full_h * hp
    wid = lerp(COLUMN_DP, full_w, clamp01(wp)) * (1 - max(hp - 1.0, 0.0))                  # a touch slimmer while stretched
    left = bar_right
    if hgt < 3.0:
        out = c.lone(page, bloom, bar, c.BAR)
    else:
        left = bar_right - wid
        radius = min(rest[4], wid / 2, hgt / 2)
        box = (bar_right - wid / 2, bar_bottom - 4.0 + hgt / 2, wid / 2, hgt / 2, radius)
        reach = c.REACH_MENU * (1 + 0.5 * (1 - clamp01(calm)))
        out = c.glass(page, bloom, bar, box, shape.menu_field_box(bar, box), c.BAR, c.MENU, reach, True)
    img = text_layer(Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8)), BAR_WORDS)
    rows_a = clamp01(wp / 0.08) if t_close is None else clamp01(wp / 0.5)
    rows = [(330, 190 + i * 68, t, 26, INK, rows_a) for i, t in enumerate(ROWS)]
    return text_layer(img, rows, clip_left=(left + 10.0) * DP)


def pill_frame(scroll, t_open, t_close=None):
    page = rim.page_at(scroll)
    bloom = c.bloom_of(page)
    out = c.lone(page, bloom, shape.BAR, c.BAR)
    box, rest = shape.BOX, shape.PILL
    if t_close is None:
        hp = m.spring(t_open, BOUNCE, 800.0)
        calm = m.spring(t_open, 1.0, 800.0)
        wp = m.spring(max(t_open - turn_time(BOUNCE, 800.0) - 0.02, 0.0), 0.92, 900.0)
    else:
        wp = 1 - m.spring(t_close, 1.0, 2400.0)
        hp = 1 - m.spring(max(t_close - 0.06, 0.0), 1.0, 2000.0)
        calm = hp
    cy = rest[1] + (1 - hp) * 44.0
    pill = (rest[0], cy, lerp(24.0, rest[2], clamp01(wp)), rest[3], rest[4])
    reach = c.REACH_PILL * (1 + 0.4 * (1 - clamp01(calm)))
    out = c.glass(out, bloom, box, pill, pill, c.BOX, c.PILL, reach, True)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
    a = clamp01((wp - 0.7) / 0.25)
    words = BAR_WORDS + [(262, cy * DP - 14, "↓ Jump to latest", 22, INK, a), (60, 590, "Ask OpenCode…", 26, INK2, 1), (56, 676, "+", 34, INK, 1)]
    img = text_layer(img, words)
    ImageDraw.Draw(img).ellipse((618, 664, 678, 724), fill=(52, 199, 120))
    return img


def run(fn, fps, slow, label, t_open=0.60, t_hold=0.30, t_close=0.24, t_rest=0.22):
    """One open, hold, close, rest cycle. slow = how many times slower than real."""
    dt = 1.0 / fps / slow
    t, total = 0.0, t_open + t_hold + t_close + t_rest
    while t < total:
        scroll = 120.0 + t * 200.0
        if t < t_open + t_hold:
            img = fn(scroll, t)
        elif t < t_open + t_hold + t_close:
            img = fn(scroll, 99.0, t - t_open - t_hold)
        else:
            img = fn(scroll, 99.0, 9.0)
        m.label(img, label)
        yield img
        t += dt


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    if MODE == "slow":
        seqs = [run(menu_frame, 30, 4.0, "4x slower"), run(pill_frame, 30, 4.0, "4x slower")]
    elif MODE == "real":
        seqs = [run(fn, 60, 1.0, "real speed") for fn in (menu_frame, menu_frame, pill_frame, menu_frame)]
    else:  # stills of the menu's opening for a check
        tt = turn_time(BOUNCE, DROP_K)
        seqs = [iter([menu_frame(120.0, t) for t in (0.05, 0.09, tt, tt + 0.06, tt + 0.12, 0.55)])]
    n = 0
    for seq in seqs:
        for img in seq:
            img.save(os.path.join(OUT, f"m-{n:04d}.png"))
            n += 1
    print(n, "frames; the drop turns at", round(turn_time(BOUNCE, DROP_K) * 1000), "ms")
