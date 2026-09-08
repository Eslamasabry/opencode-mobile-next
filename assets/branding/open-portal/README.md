# Open portal identity candidate

Finish line: a distinct launcher concept with an inspectable raster master, editable vector geometry, an adaptive/monochrome handoff, and one coherent in-app glyph contract.

Non-goal: signing, release, installing an APK, or editing other page owners' screens. The coordinator approved the Phosphor package and Android launcher-resource implementation on 2026-09-09.

`imagegen-concept.png` is the original built-in ImageGen result. It is 1254×1254 RGB, rather than the requested 1024 square; it is a design reference, not an Android resource. It intentionally avoids a baked rounded outer mask. `mark.svg`, `foreground.xml`, and `monochrome.xml` are newly authored deterministic interpretations of the concept, not raster edits. The SVG is an editable mark on transparency; both Android XMLs are readable source copies. Matching geometry is now wired in `android/app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`; existing adaptive-icon XML and manifest references are preserved.

The two open brackets form a recognizable aperture. Both strokes have the same weight and full-opacity silhouette. Large corner gaps replace the previous weak cursor and glow. The name and code association remain understandable without squeezing tiny letters into a launcher icon.

## Candidate geometry

- Android viewport/layer: 108×108. Inline `mark.svg` crops the same geometry to viewBox `24 24 60 60`, giving a 29.9px painted mark in a 32px header slot (22.4px at 24). `AppBrandMark` tints it to theme primary and defaults to decorative semantics. The Android render tool extracts the paths and keeps the full 108dp geometry.
- Center: 54,54. Mark stroke: 10. Upper/left and lower/right corner arcs: radius 12.
- Foreground: `#8FE5BD`; suggested full-bleed background: `#101713`.
- Monochrome: identical paths at full alpha; no glow, shade or partially transparent cursor.
- Geometric bounds: x26–82, y26–82; asymmetric open ends are optically balanced within the centered safe area. The SVG mask studies were rendered and visually inspected at full, 48px and 32px reference sizes; they are not actual Android launcher screenshots.

Android requires separately authored foreground and background layers at 108dp and a monochrome layer for controlled themed-icon rendering. Keep the mark within the central 66dp safe zone and avoid baking in the outer mask/shadow. The launcher controls masks and supported movement. These candidates meet the intended canvas/safe-box design; AAPT2 36.1.0 resource compilation passed; installed-launcher verification remains pending. [Android adaptive-icon documentation](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

## Production state and remaining handoff

Implemented: foreground and monochrome vectors, background color, and five legacy mipmaps (48/72/96/144/192px). The legacy PNGs are rendered directly from the newly authored SVG, not edited from the ImageGen raster. `tool/branding/render_android_icons.py` reproduces them using CairoSVG (verified with 2.9.1); this is development tooling only. XML parses, vector dimensions/strokes, legacy PNG dimensions/alpha, and circle/rounded-square/squircle color/themed SVG previews were checked. `mask-studies.svg` is the editable reference and `mask-studies.png` its rendering.

Remaining: inspect a built/installed launcher at real display size, including themed icons. The source resources are updated, but no claim is made that the installed launcher changed. Marketing/iOS/Linux identity pipelines remain outside this Android slice: existing `app-icon-256.png` and `opencode-mobile-app-icon-v2.png` are preserved and therefore still show the old identity until a coordinated cross-platform update.

## Generation provenance

Built-in ImageGen, generated 2026-09-09. Source result: `/home/eslam/.codex/generated_images/01a082f1-066d-7472-8be1-e4ade46112e2/exec-d4c3a8bb-bc0a-4417-9199-249ebfc292cc.png`. Copied unchanged into this directory.

Prompt:

> Use case: logo-brand. Asset type: new Android coding-companion launcher icon concept, 1024 square. Create one distinct, beautifully simple OpenCode app identity: an open portal formed from two opposing thick rounded right-angle bracket strokes, arranged as an almost-square aperture with two intentional diagonal corner gaps. The upper-left bracket and lower-right bracket are offset very slightly to suggest opening and forward movement, but together make one immediately legible compact symbol. It should read as a crafted abstract open O, not a terminal prompt and not a generic greater-than chevron. Center the mark inside the central 60% of the canvas, leaving generous Android adaptive mask safe space. Background full bleed solid near-black graphite #101713, no baked rounded icon plate. Mark fresh luminous pale mint #8FE5BD with extremely restrained satin tonal light across the solid strokes; flat geometric silhouette must carry the entire identity. Premium calm Android productivity, warm precise rounded corners, balanced negative space, confident and slightly playful. No typography, no letters, no cursor block, no chevrons, no tiny details, no neon glow, no border around the canvas, no drop shadow, no mockup device, no texture, no watermark. Single icon artwork filling the square, not a presentation board.
