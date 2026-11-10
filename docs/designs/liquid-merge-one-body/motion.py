"""SPIKE (not app code): the motion of the joined glass. "Drop down and then rebound to shape" (owner 2026-11-11).

Menu out of the bar:
- a bead swells under the control, then pours down: the HEIGHT runs ahead on a springy spring and passes its rest
  length; the WIDTH follows a beat later on a calmer one;
- while the height is past its rest the body is narrower, and wider while it is short of it (the liquid keeps its
  volume), so the rebound reads as one mass settling, not as two sizes animating;
- corners start as the bead's full round and relax to the menu's 24 dp; the neck is thicker while it pours;
- the rows arrive once the shape is most of the way there;
- closing is quick and has no bounce: the rows go first, the height is drawn back up, the bead sinks into the bar.
The pill does the same thing upwards: it pops up past its rest, narrow, then settles and widens.
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
BOUNCE = float(os.environ.get("BOUNCE", "0.62"))  # the height spring's damping ratio: lower = more rebound


def lerp(a, b, t):
    return a + (b - a) * t


def clamp01(x):
    return max(0.0, min(1.0, x))


def phase(t_open, t_close, lead_k, follow_k):
    """Height and width progress, the residual of the height over a calm spring, and the calm progress itself."""
    if t_close is None:
        hp = m.spring(t_open, BOUNCE, lead_k)
        calm = m.spring(t_open, 1.0, lead_k)
        wp = m.spring(max(t_open - 0.03, 0.0), 0.80, follow_k)
        return hp, wp, hp - calm, calm
    hp = 1 - m.spring(t_close, 1.0, 1500.0)
    wp = 1 - m.spring(max(t_close - 0.04, 0.0), 1.0, 1300.0)
    return hp, wp, 0.0, hp


def text_layer(img, items):
    """items: (x, y, text, size, rgb, alpha)."""
    over = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    for x, y, t, size, rgb, a in items:
        if a > 0.01:
            d.text((x, y), t, font=m.font(size), fill=rgb + (int(255 * clamp01(a)),))
    return Image.alpha_composite(img.convert("RGBA"), over).convert("RGB")


BAR_WORDS = [(96, 66, "Sunset palette for the light theme", 28, (240, 241, 244), 1), (96, 104, "project · This phone · Git", 20, (170, 174, 184), 1), (44, 84, "←", 30, (240, 241, 244), 1), (640, 78, "···", 30, (240, 241, 244), 1)]


def menu_frame(scroll, t_open, t_close=None):
    page = rim.page_at(scroll)
    bloom = c.bloom_of(page)
    bar, rest = shape.BAR, shape.MENU
    hp, wp, resid, calm = phase(t_open, t_close, 520.0, 620.0)
    bar_bottom, bar_right, ctrl_x = bar[1] + bar[3], bar[0] + bar[2], bar[0] + bar[2] - 28.0
    full_w, full_h = 2 * rest[2], 2 * rest[3]
    hgt = max(full_h * hp, 0.0)
    wid = max(full_w * wp * (1 - 0.6 * resid), 36.0 * clamp01(hp * 6))
    if hgt < 2.0 or wid < 2.0:
        out = c.lone(page, bloom, bar, c.BAR)
    else:
        right = lerp(ctrl_x + 20.0, bar_right, clamp01(wp * 1.3))
        top = bar_bottom - 4.0
        radius = min(lerp(min(wid, hgt) / 2, rest[4], clamp01(wp)), wid / 2, hgt / 2)
        box = (right - wid / 2, top + hgt / 2, wid / 2, hgt / 2, radius)
        reach = c.REACH_MENU * (1 + 0.5 * (1 - clamp01(calm)))
        out = c.glass(page, bloom, bar, box, shape.menu_field_box(bar, box), c.BAR, c.MENU, reach, True)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
    # the rows wait for the WIDTH (they must never stand outside the glass), then arrive top to bottom
    ready = min(calm, clamp01(wp))
    rows_a = clamp01((ready - 0.8) / 0.15) if t_close is None else clamp01(1 - t_close / 0.05)
    slide = (1 - clamp01((ready - 0.8) / 0.2)) * -10 if t_close is None else 0
    rows = [(330, 190 + i * 68 + slide, t, 26, (240, 241, 244), rows_a * clamp01((ready - 0.8 - i * 0.012) / 0.12 if t_close is None else 1)) for i, t in enumerate(("Find", "Rename conversation", "Fork conversation", "Copy transcript", "Go to", "More"))]
    return text_layer(img, BAR_WORDS + rows)


def pill_frame(scroll, t_open, t_close=None):
    page = rim.page_at(scroll)
    bloom = c.bloom_of(page)
    out = c.lone(page, bloom, shape.BAR, c.BAR)
    box, rest = shape.BOX, shape.PILL
    hp, wp, resid, calm = phase(t_open, t_close, 480.0, 620.0)
    cy = rest[1] + (1 - hp) * 44.0                       # passes its rest height, then comes back
    half_w = lerp(22.0, rest[2], clamp01(wp)) * (1 - 0.5 * resid)
    pill = (rest[0], cy, half_w, rest[3], rest[4])
    reach = c.REACH_PILL * (1 + 0.4 * (1 - clamp01(calm)))
    out = c.glass(out, bloom, box, pill, pill, c.BOX, c.PILL, reach, True)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
    a = clamp01((calm - 0.6) / 0.3) if t_close is None else clamp01(1 - t_close / 0.05)
    words = BAR_WORDS + [(262, cy * DP - 14, "↓ Jump to latest", 22, (240, 241, 244), a), (60, 590, "Ask OpenCode…", 26, (170, 174, 184), 1), (56, 676, "+", 34, (240, 241, 244), 1)]
    img = text_layer(img, words)
    ImageDraw.Draw(img).ellipse((618, 664, 678, 724), fill=(52, 199, 120))
    return img


def run(fn, fps, slow, t_open=0.62, t_hold=0.25, t_close=0.26, t_rest=0.2, label=""):
    """Yields frames of one open, hold, close, rest cycle. slow = how many times slower than real."""
    dt = 1.0 / fps / slow
    t, total, i = 0.0, t_open + t_hold + t_close + t_rest, 0
    while t < total:
        scroll = 120.0 + t * 260.0
        if t < t_open + t_hold:
            img = fn(scroll, t)
        elif t < t_open + t_hold + t_close:
            img = fn(scroll, 99.0, t - t_open - t_hold)
        else:
            img = fn(scroll, 99.0, 9.0)
        m.label(img, label)
        yield img
        t += dt
        i += 1


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    n = 0
    if MODE == "slow":
        seqs = [run(pill_frame, 30, 4.0, label="4x slower"), run(menu_frame, 30, 4.0, label="4x slower")]
    elif MODE == "real":
        seqs = [run(pill_frame, 60, 1.0, label="real speed"), run(menu_frame, 60, 1.0, label="real speed")] * 2
    else:  # a few stills of the menu for a check
        seqs = [iter([menu_frame(120.0, t) for t in (0.04, 0.09, 0.15, 0.22, 0.32, 0.6)])]
    for seq in seqs:
        for img in seq:
            img.save(os.path.join(OUT, f"m-{n:04d}.png"))
            n += 1
    print(n, "frames")
