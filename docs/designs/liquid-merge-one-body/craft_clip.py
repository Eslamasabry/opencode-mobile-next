"""SPIKE: the crafted joins in motion on the kit's release spring (shown four times slower), one screen at a time."""
import importlib.util, os, sys
here = os.path.dirname(os.path.abspath(__file__))
out = sys.argv[1]
spec = importlib.util.spec_from_file_location("craft", os.path.join(here, "craft.py"))
c = importlib.util.module_from_spec(spec); sys.argv = ["craft.py", out]; spec.loader.exec_module(c)
os.makedirs(out, exist_ok=True)
i = 0
for fn, key in ((c.screen_pill, "rise"), (c.screen_menu, "grow")):
    for j in range(64):
        if j < 44:
            p = c.m.spring(j / 30.0 / 4.0, 0.82, 700.0)
        else:
            u = (j - 44) / 19.0
            p = 1 - u * u * (3 - 2 * u)
        img = fn(100.0 + (i * 6.0), True, **{key: p})
        c.m.label(img, "crafted, 4x slower")
        img.save(os.path.join(out, f"c-{i:04d}.png"))
        i += 1
