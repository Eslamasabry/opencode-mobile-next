"""SPIKE (not app code): the crafted joins of shape.py drawn as glass (the app's saturation, the cut blur, the rim
that picks up passing content), next to the same glass on the first blend's outline."""
import importlib.util
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

here = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(here, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    sys.argv = [name + ".py", OUT]
    spec.loader.exec_module(mod)
    return mod


rim = load("rim")
shape = load("shape")
m = rim.m
W, H, DP, XX, YY = m.W, m.H, m.DP, m.XX, m.YY
MAT = dict(name="", sat=1.6, tint_scale=0.8, lift=0.0, alive=True, glow=0.20, gloss=0.10, fringe=0.0, rim=0.5)
BAR = dict(blur=3.0, bend=34, rim=22, share=0.40, tint=0.22)      # blur cut: 6 -> 3 dp
MENU = dict(blur=6.0, bend=48, rim=26, share=0.40, tint=0.26)     # 12 -> 6
BOX = dict(blur=2.0, bend=34, rim=22, share=0.40, tint=0.24)      # 4 -> 2
PILL = dict(blur=0.5, bend=12, rim=12, share=0.28, tint=0.24)     # 2 -> 0.5


def px(box):
    return tuple(v * DP for v in box)


SEAM = os.environ.get("SEAM", "ramp")  # "line": each side its own settings (the first drawing); "ramp": one frost, settings eased


def glass(page, bloom, a, b, field_b, sa, sb, reach_dp, cubic):
    """One joined body: a and b in dp; field_b is b as the field sees it."""
    d, gx, gy, w, da, db = shape.join(XX, YY, px(a), px(field_b), reach_dp * DP, cubic)
    ra = m.rim_of(2 * min(a[2], a[3]), sa["rim"], sa["share"])
    rb = m.rim_of(2 * min(b[2], b[3]), sb["rim"], sb["share"])
    if SEAM == "line":
        mine = da <= db
        share_b = np.where(mine, w, 1 - w)
    else:
        # One body, one frost (the body pane's). Rim, bend and tint ease from one pane's to the other's over a wide
        # band (24 dp each side of the line where the two are equally near), never at a line.
        share_b = shape.smoothstep(-24 * DP, 24 * DP, da - db)
    rimw = ra * (1 - share_b) + rb * share_b
    bend = (sa["bend"] * (1 - share_b) + sb["bend"] * share_b) * DP
    tint = sa["tint"] * (1 - share_b) + sb["tint"] * share_b
    if SEAM == "line":
        softs = (m.blur(page, sa["blur"] * DP), m.blur(page, sb["blur"] * DP))
        col = np.where((da <= db)[..., None], rim.lens(softs[0], bloom, d, gx, gy, rimw, bend, tint, MAT), rim.lens(softs[1], bloom, d, gx, gy, rimw, bend, tint, MAT))
    else:
        col = rim.lens(m.blur(page, sa["blur"] * DP), bloom, d, gx, gy, rimw, bend, tint, MAT)
    cover = np.clip(0.5 - d / np.maximum(np.hypot(gx, gy), 0.05), 0, 1)[..., None]
    return page * (1 - cover) + col * cover


def bloom_of(page):
    wide = m.blur(page, 10 * DP)
    return m.blur(1 - (1 - page) * (1 - np.clip((wide - 0.12) / 0.88, 0, 1)), 5 * DP)


def lone(page, bloom, a, sa):
    return glass(page, bloom, a, (a[0], a[1], 4.0, 4.0, 4.0), (a[0], a[1], 4.0, 4.0, 4.0), sa, sa, 0.0, True)


def screen_menu(scroll, crafted, grow=1.0):
    page = rim.page_at(scroll)
    bloom = bloom_of(page)
    bar, menu = shape.BAR, shape.MENU
    if not crafted:  # the first blend: no reach to the bar's centre, 4 dp overlap, quadratic, 48 dp
        out = glass(page, bloom, bar, menu, menu, BAR, MENU, 48.0, False)
    else:
        ax, ay = 348.0 - 28.0, bar[1] + bar[3]     # grows about the point under the control
        s = 0.55 + 0.45 * grow
        x0, x1 = ax + (menu[0] - menu[2] - ax) * s, ax + (menu[0] + menu[2] - ax) * s
        y0, y1 = ay + (menu[1] - menu[3] - ay) * s, ay + (menu[1] + menu[3] - ay) * s
        mm = ((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2, menu[4])
        out = glass(page, bloom, bar, mm, shape.menu_field_box(bar, mm), BAR, MENU, REACH_MENU, True)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    d.text((96, 66), "Sunset palette for the light theme", font=m.font(28), fill=(240, 241, 244))
    d.text((96, 104), "project · This phone · Git", font=m.font(20), fill=(170, 174, 184))
    d.text((44, 84), "←", font=m.font(30), fill=(240, 241, 244))
    d.text((640, 78), "···", font=m.font(30), fill=(240, 241, 244))
    if grow > 0.9:
        for i, t in enumerate(("Find", "Rename conversation", "Fork conversation", "Copy transcript", "Go to", "More")):
            d.text((330, 190 + i * 68), t, font=m.font(26), fill=(240, 241, 244))
    return img


def screen_pill(scroll, crafted, rise=1.0):
    page = rim.page_at(scroll)
    bloom = bloom_of(page)
    out = lone(page, bloom, shape.BAR, BAR)
    box, pill = shape.BOX, shape.PILL
    if not crafted:  # the first blend: 4 dp overlap, quadratic, 32 dp
        p0 = (pill[0], box[1] - box[3] + 4.0 - 16.0, pill[2], pill[3], pill[4])
        out = glass(out, bloom, box, p0, p0, BOX, PILL, 32.0, False)
        label_y = p0[1]
    else:
        p1 = (pill[0], pill[1] + (1 - rise) * 44.0, pill[2], pill[3], pill[4])
        out = glass(out, bloom, box, p1, p1, BOX, PILL, REACH_PILL, True)
        label_y = p1[1]
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    d.text((96, 66), "Sunset palette for the light theme", font=m.font(28), fill=(240, 241, 244))
    d.text((96, 104), "project · This phone · Git", font=m.font(20), fill=(170, 174, 184))
    d.text((44, 84), "←", font=m.font(30), fill=(240, 241, 244))
    d.text((640, 78), "···", font=m.font(30), fill=(240, 241, 244))
    if rise > 0.6:
        d.text((262, label_y * DP - 14), "↓ Jump to latest", font=m.font(22), fill=(240, 241, 244))
    d.text((60, 590), "Ask OpenCode…", font=m.font(26), fill=(170, 174, 184))
    d.text((56, 676), "+", font=m.font(34), fill=(240, 241, 244))
    d.ellipse((618, 664, 678, 724), fill=(52, 199, 120))
    return img


REACH_MENU = float(os.environ.get("REACH_MENU", "134"))
REACH_PILL = float(os.environ.get("REACH_PILL", "56"))

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn, s in (("menu", screen_menu, 120.0), ("pill", screen_pill, 180.0)):
        for crafted in (False, True):
            img = fn(s, crafted)
            m.label(img, "crafted" if crafted else "first blend")
            img.save(os.path.join(OUT, f"shape-{name}-{'2-crafted' if crafted else '1-first'}.png"))
    print("ok")
