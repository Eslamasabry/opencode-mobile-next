#!/usr/bin/env python3
"""Copy named Flutter kit goldens out of the app repo at a ref, for the native golden-diff gate.

Usage: fetch_flutter_goldens.py --repo /path/to/oc_app [--ref mobile-next/master] --out looks-gate/flutter NAME...
NAME is a golden stem without the mode (kit_row_default); both _light and _dark are copied. With --all, every
test/goldens/kit/*.png at the ref is copied.
"""
import argparse, os, subprocess

ap = argparse.ArgumentParser()
ap.add_argument("--repo", required=True)
ap.add_argument("--ref", default="mobile-next/master")
ap.add_argument("--out", required=True)
ap.add_argument("--all", action="store_true")
ap.add_argument("names", nargs="*")
a = ap.parse_args()
os.makedirs(a.out, exist_ok=True)
if a.all:
    listing = subprocess.run(["git", "-C", a.repo, "ls-tree", "-r", "--name-only", a.ref, "test/goldens/kit"], capture_output=True, text=True, check=True).stdout.split()
    paths = [p for p in listing if p.endswith(".png") and "/failures/" not in p]
else:
    paths = [f"test/goldens/kit/{n}_{m}.png" for n in a.names for m in ("light", "dark")]
for p in paths:
    data = subprocess.run(["git", "-C", a.repo, "show", f"{a.ref}:{p}"], capture_output=True, check=True).stdout
    open(os.path.join(a.out, os.path.basename(p)), "wb").write(data)
print(f"copied {len(paths)} goldens to {a.out}")
