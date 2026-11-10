"""SPIKE (not app code): the joined body's outline as designed geometry, with its curvature measured.

Rules:
- Convex corners stay the kit's circular arcs (a lone pane must not change when it joins).
- The join is a smooth minimum with a CUBIC kernel: the fillet meets both edges with matching tangent and curvature,
  so the rim light runs through it without a kink. (The first prototype's quadratic kernel jumps in curvature.)
- The blend is strong only where the shapes face each other (the reference's rule), so nothing swells elsewhere.
- Menu on bar: the menu's right edge is the bar's right edge, and the menu's field reaches up to the bar's centre
  line, so the bar's end cap runs straight down into the menu's flank. The one concave corner (bar's bottom edge into
  the menu's left edge) gets a fillet whose tightest radius equals the menu's corner radius.
- Pill on box: the pill is a full capsule sunk a quarter of its height into the box. Each flank is an S-curve: the
  capsule's end (convex, radius 16 dp) into a fillet whose tightest radius is the same 16 dp.
The reach of each blend is solved numerically for those radii, not guessed.
"""
import sys

import numpy as np


def sdg_box(X, Y, box):
    cx, cy, hx, hy, r = box
    r = min(r, hx, hy)
    px, py = X - cx, Y - cy
    wx, wy = np.abs(px) - (hx - r), np.abs(py) - (hy - r)
    sx, sy = np.where(px < 0, -1.0, 1.0), np.where(py < 0, -1.0, 1.0)
    g = np.maximum(wx, wy)
    qx, qy = np.maximum(wx, 0), np.maximum(wy, 0)
    l = np.maximum(np.hypot(qx, qy), 1e-6)
    out = g > 0
    d = np.where(out, l - r, g - r)
    gx = sx * np.where(out, qx / l, np.where(wx > wy, 1.0, 0.0))
    gy = sy * np.where(out, qy / l, np.where(wx > wy, 0.0, 1.0))
    return d, gx, gy


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def join(X, Y, a, b, reach, cubic=True):
    """d, direction (not normalised), the partner's share for pane a, and da, db."""
    da, ax, ay = sdg_box(X, Y, a)
    db, bx, by = sdg_box(X, Y, b)
    facing = np.clip(0.5 - 0.5 * (ax * bx + ay * by), 0, 1)
    ma, mb = min(a[4], a[2], a[3]), min(b[4], b[2], b[3])
    inside = (1 - smoothstep(0.5 * ma, ma, -da)) * (1 - smoothstep(0.5 * mb, mb, -db))
    k = np.maximum(reach * facing * inside, 1e-3)
    h = np.maximum(k - np.abs(da - db), 0) / k
    if cubic:
        d = np.minimum(da, db) - h * h * h * k / 6.0
        mix = h * h / 2.0
    else:
        d = np.minimum(da, db) - h * h * k / 4.0
        mix = h / 2.0
    w = np.where(da <= db, mix, 1 - mix)
    return d, ax * (1 - w) + bx * w, ay * (1 - w) + by * w, w, da, db


def menu_field_box(bar, menu):
    """The menu as the field sees it: reaching up to the bar's centre line (inside the bar, so it changes no outline
    except the right flank, which becomes one straight line when the two right edges are aligned)."""
    cx, cy, hx, hy, r = menu
    top, bottom = min(cy - hy, bar[1]), cy + hy
    return (cx, (top + bottom) / 2, hx, (bottom - top) / 2, r)


def curvature_on_outline(d, step):
    """Signed curvature of the outline d = 0 (per dp; positive = convex), sampled on the pixels the outline crosses."""
    gy, gx = np.gradient(d, step)
    n = np.maximum(np.hypot(gx, gy), 1e-6)
    nx, ny = gx / n, gy / n
    kappa = np.gradient(nx, step, axis=1) + np.gradient(ny, step, axis=0)
    on = np.abs(d) < step * 0.75
    return kappa, on


def tightest_concave_radius(a, b, reach, window, cubic=True, ppd=8):
    """The smallest radius of the concave part of the outline inside [window] (x0, y0, x1, y1 in dp)."""
    x0, y0, x1, y1 = window
    ys, xs = np.mgrid[y0:y1:1.0 / ppd, x0:x1:1.0 / ppd]
    d = join(xs, ys, a, b, reach, cubic)[0]
    kappa, on = curvature_on_outline(d, 1.0 / ppd)
    # smooth the estimate a little along the outline
    k = kappa[on]
    if k.size == 0 or k.min() >= 0:
        return float("inf")
    return 1.0 / -np.percentile(k, 2)


def solve_reach(a, b, target_radius, window, cubic=True):
    lo, hi = 4.0, 160.0
    for _ in range(22):
        mid = (lo + hi) / 2
        if tightest_concave_radius(a, b, mid, window, cubic) < target_radius:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


# The two joins at rest, in dp. A 360 dp wide phone, 12 dp side gutters.
BAR = (180.0, 52.0, 168.0, 28.0, 28.0)
MENU = (248.0, 52.0 + 28.0 - 4.0 + 115.0, 100.0, 115.0, 24.0)        # right edge = the bar's right edge (348)
BOX = (180.0, 320.0, 168.0, 52.0, 28.0)
PILL = (180.0, 320.0 - 52.0 + 8.0 - 16.0, 76.0, 16.0, 16.0)          # a quarter of its 32 dp sunk into the box


if __name__ == "__main__":
    menu_f = menu_field_box(BAR, MENU)
    win_menu = (120.0, 70.0, 175.0, 125.0)          # round the one concave corner (bar's bottom into the menu's left)
    win_pill = (70.0, 240.0, 120.0, 285.0)          # the pill's left flank
    for name, a, b, target, win in (("menu on bar", BAR, menu_f, 24.0, win_menu), ("pill on box", BOX, PILL, 16.0, win_pill)):
        for cubic in (False, True):
            reach = solve_reach(a, b, target, win, cubic)
            r = tightest_concave_radius(a, b, reach, win, cubic)
            # curvature jump: the largest change of curvature between neighbouring outline samples in the window
            ys, xs = np.mgrid[win[1]:win[3]:1.0 / 8, win[0]:win[2]:1.0 / 8]
            d = join(xs, ys, a, b, reach, cubic)[0]
            kappa, on = curvature_on_outline(d, 1.0 / 8)
            idx = np.argwhere(on)
            order = np.argsort(np.arctan2(idx[:, 0] - idx[:, 0].mean(), idx[:, 1] - idx[:, 1].mean()))
            ks = kappa[on][order]
            ks = np.convolve(ks, np.ones(5) / 5, mode="valid")
            jump = np.abs(np.diff(ks)).max() if ks.size > 2 else 0.0
            print(f"{name:12s} {'cubic' if cubic else 'quadratic':9s} reach {reach:6.1f} dp  tightest concave radius {r:5.1f} dp  largest curvature step {jump:.4f} /dp")
