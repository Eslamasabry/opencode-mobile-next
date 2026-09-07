"""Render the existing project icon into the generated iOS asset catalog.

Development-only helper; requires Pillow. No dependency is added to the app.
"""
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/branding/opencode-mobile-app-icon-v2.png"
CATALOG = ROOT / "ios/Runner/Assets.xcassets/AppIcon.appiconset"


def main():
    manifest = json.loads((CATALOG / "Contents.json").read_text())
    with Image.open(SOURCE) as source:
        rgba = source.convert("RGBA")
        # App Store icons must be opaque. Use the existing dark icon background
        # rather than the default white/black alpha-removal background.
        opaque = Image.new("RGBA", rgba.size, (15, 20, 17, 255))
        opaque.alpha_composite(rgba)
        opaque = opaque.convert("RGB")
        rendered = set()
        for entry in manifest["images"]:
            name = entry["filename"]
            if Path(name).name != name or not name.endswith(".png"):
                raise ValueError("Unexpected icon filename")
            width, height = (float(value) for value in entry["size"].split("x"))
            scale = float(entry["scale"].removesuffix("x"))
            size = (round(width * scale), round(height * scale))
            if size[0] != size[1] or not 1 <= size[0] <= 1024:
                raise ValueError("Unexpected icon dimensions")
            if name not in rendered:
                opaque.resize(size, Image.Resampling.LANCZOS).save(CATALOG / name)
                rendered.add(name)
    print(f"Rendered {len(rendered)} opaque iOS icons from the project asset.")


if __name__ == "__main__":
    main()
