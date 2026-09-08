"""Render Android and bundled branding PNGs from the open-portal vector.

Requires CairoSVG (development tooling only). Adaptive foreground/monochrome
remain Android vectors; this script never edits an existing raster image.
"""

from pathlib import Path
import xml.etree.ElementTree as ET

import cairosvg


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/branding/open-portal/mark.svg"
ANDROID = ROOT / "android/app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main() -> None:
    mark = ET.parse(SOURCE).getroot()
    shapes = "".join(ET.tostring(child, encoding="unicode") for child in mark)
    # Legacy launchers need one complete tile. Use the same 90-unit conceptual
    # viewport as the mask study; the adaptive launcher owns its own mask.
    source = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="9 9 90 90">'
        '<rect x="9" y="9" width="90" height="90" rx="22" fill="#101713"/>'
        f"{shapes}</svg>"
    )
    targets = {
        ANDROID / f"mipmap-{density}/ic_launcher.png": size
        for density, size in DENSITIES.items()
    }
    # About/in-app bitmap branding and the Linux window use the same identity.
    targets[ROOT / "assets/branding/app-icon-256.png"] = 256
    for target, size in targets.items():
        cairosvg.svg2png(
            bytestring=source.encode(),
            write_to=str(target),
            output_width=size,
            output_height=size,
        )
        print(f"{target.relative_to(ROOT)}: {size}x{size}")


if __name__ == "__main__":
    main()
