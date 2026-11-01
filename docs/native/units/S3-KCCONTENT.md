# Unit S3-KCCONTENT: content kit, first parts (report)

Branch `native/kccontent-2026-11-01`, from `claude/native-kccontent-kit-u6hpdu` at `6b9a2b21`. Date: 2026-11-01.
Plan: `plan/06` §3, §7.7, §16, §17; `plan/10` §9, §11, §14; roadmap row S3-KCCONTENT (`plan/15`).
Write set used: `native/ui/kit-content/**` (new module) and this report. Nothing outside it was edited.

## Scope taken, and how it relates to the roadmap row

The brief for this session named: image view with zoom, file-viewer frames (text, code, image, PDF via `PdfRenderer` in
`androidMain`, binary), the QR display part, and goldens. The roadmap row's finish line is wider (code block with
`CodeTokenizer`, log panel, `KitImage` + resolver, scanner slot with CameraX + zxing-cpp, terminal output view,
capability explainer, checklist, rings, task chip). This unit delivers the brief. The rest of the row is **not done**
(see "Open problems"). So the row's finish line is **not** met yet; the parts listed below are.

Status per part: **implemented, tested (desktop + Robolectric), goldens recorded, committed, pushed.** Not integrated
into any screen. Not checked on an emulator.

## What was built

All in `native/ui/kit-content/`, package `io.github.eslamasabry.oc.ui.kit.content.*`. The module is discovered by
`settings.gradle.kts` (layer-folder discovery), and build-logic already allows `:ui:kit-content` → `:ui:kit`, `:ui:design`,
`:ui:words` and material3. The part index is `native/ui/kit-content/KIT.md`.

- **QR** (`qr/`)
  - `QrEncoder` is a self-contained QR Model 2 encoder: byte mode (UTF-8), error correction M, the smallest version
    1…40 that fits, Reed–Solomon over GF(256), interleaving, format and version info.
  - `QrRules.penalty` ports Dart `qr` 3.0.2 `_lostPoint` (Arase's N1–N4). `bestMask` scores trial symbols with the format, version and dark-module
    bits left light, as Dart's `makeImpl(test = true)` does, so the mask choice matches Flutter's. It also has
    `sidePx`, which snaps to whole physical pixels (LOOK-21).
  - `QrPayload.of` never throws: it returns `Empty` (B-KIT-026), `TooLong` or `Ready`.
  - `KitQr(data, semanticsLabel)` draws dark modules on a light Graphite card in every theme, with a 4-module quiet
    zone, at 240 dp at most and square, always left to right. The data is never put in text or semantics. A payload that
    is too long shows `kitQrTooLong` as a polite live region. An empty payload throws in debug builds only and draws
    nothing in release (`contentAssert`). `KitQrs.fits(data)` lets a host check first.
- **Zoom and image** (`image/`)
  - `ZoomMath` (pure): the floor is fit (1×), the ceiling is 5 × fit, the offset is clamped so no edge shows, and a
    pinch keeps its focus still.
  - `KitZoomState` (`zoomIn`, `zoomOut`, `fit`, `isFitted` for pagers).
  - `KitZoom`: pinch, pan and double tap. Scale is read in the draw phase, and the zoom level goes out as a state
    description.
  - `KitZoomControls`: the visible Zoom out / Fit to screen / Zoom in buttons. A disabled button says why ("Already
    at full view", "Largest zoom").
  - `KitImageView(painter, …)`: a fitted picture plus zoom and the buttons.
- **Viewer** (`viewer/`)
  - `KitViewerFrame`:
    - the name, and under it the parent folder from `ViewerPaths.folderOf` (B-NAV-028), with paths isolated left to
      right;
    - optional Close;
    - the body slot;
    - a bottom bar with one primary (`KitButton`) and an optional secondary.
  - Bodies:
    - `KitViewerTextBody`: lazy lines, wraps, empty and truncated states.
    - `KitViewerCodeBody`: lazy lines, always left to right, mono, line numbers kept out of selection, scrolls sideways
      by default. `initialLine` is scrolled to and marked with the .18 accent wash and `selected` semantics.
    - `KitViewerBinaryBody`: "Can't show this file", then "type · 2.4 MB", then the caller's action.
    - `KitPdfPage`: loading with the space reserved, ready, failed with Try again. The caption reads "Page 3 of 12".
      Width is `min(available − 2 × space3, 720 dp)`.
  - `ViewerText` (pure): strict UTF-8 (a NUL or an invalid sequence gives `null`, B-MEDIA-001), a 200,000-character
    cut that never splits a surrogate pair, line splitting, and size in words.
- **PDF** (`pdf/`)
  - `PdfLimits` (commonMain, pure): input ≤ 10 MiB, page < 200, output PNG ≤ 10 MiB, scale
    `min(1536 / max(w, h), √(2 000 000 / (w × h)))`. A reply is checked before it is decoded: PNG signature, IHDR
    width and height equal to the reported ones, reported size within budget, and the page asked for (B-MEDIA-004).
  - `PdfPageRasterizer` (androidMain): `PdfRenderer` on a descriptor, white background, `RENDER_MODE_FOR_DISPLAY`,
    PNG through `CappedOutputStream`. Every expected failure is a `PdfRaster.Failed` reason (Encrypted, Invalid,
    PageOutOfRange, TooLarge), never an exception. It is written to run inside the isolated `:local_pdf` service; the
    service, binding, descriptors and watchdog belong to S5-VIEWER.
- **Goldens:** 24 PNGs in `src/screenshots/`. Six scenes (KitQr, KitViewerFrame text, KitViewerCodeBody at line 5,
  KitViewerBinaryBody, KitImageView, KitPdfPage) × light/dark × en/ar, at 412 × 915 dp and 420 dpi, motion Off.
  I looked at them. The first recording showed scene bugs (no themed ground behind the QR and PDF scenes, PDF pages
  clipped); I fixed the scenes and recorded again.

## Catalog rows covered (tests named after the row)

| Row | Tests |
|---|---|
| B-KIT-026 | `QrPayloadTest` (empty refused; too long refused as words), `ContentPartsDesktopTest` (debug throws; too long shows words) |
| B-NAV-028 | `ViewerRulesTest` ×4 (folder only with slash; root has none; suffix kept; caption as is), `ContentPartsDesktopTest` ×2 |
| B-MEDIA-001 | `ViewerRulesTest` (invalid UTF-8 / NUL → no text; cut keeps surrogate pairs) |
| B-MEDIA-004 | `PdfLimitsTest` ×6 (input and page budget; size disagreement; forged IHDR; wrong page / oversized PNG; 200-page cap) |
| MV-8 / MEDIA-VOICE-46…48 (plan rows, no `B-` id) | `QrPayloadTest` (versions 1–40 decode; smallest version; mask = lowest penalty; penalty hand-counted), `KitQrReadabilityTest` (50 fixtures drawn on Android graphics scan in light and dark) |
| MEDIA-VOICE-37, 40, 42 (plan rows) | `ViewerRulesTest`, `ZoomMathTest`, `PdfLimitsTest`, `ContentPartsDesktopTest` |

Not covered: B-KIT-021 (log buffer), B-ANDR-049 (scanner) and the other rows of the full roadmap row (see "Open problems").

## Commands and results (this container, JDK 21, Android SDK platform 37.1, build-tools 37.0.0 and 36.0.0)

```
./gradlew :ui:kit-content:compileKotlinDesktop               # green
./gradlew :ui:kit-content:desktopTest                        # 39 tests, 0 failures
./gradlew :ui:kit-content:recordRoborazziAndroidHostTest     # 46 tests, 0 failures; 24 goldens written
```

`testAndroidHostTest` compiles `androidMain` (the `PdfRenderer` rasterizer included) and runs the common tests again
under Robolectric. Other modules were not built or tested, apart from what these tasks compile (`:core:common`,
`:ui:design`, `:ui:kit`, `:ui:words`). `:quality:test` was **not** run (see `MODULE_OWNERS` below).

Environment notes (not committed):

- Maven Central answered HTTP 429 for many artifacts. A local init script (`~/.gradle/init.d/central-mirror.gradle.kts`)
  puts Google's Maven Central mirror first.
- Robolectric's own `android-all` download also hit a 429 once; a rerun fixed it.

## Deviations

1. **Own QR encoder instead of `qrose-encoder-matrix`.** The catalog has no qrose entry, and this unit may not edit it.
   The encoder is about 250 lines of pure Kotlin, and ZXing core decodes every version from 1 to 40 (module grid and
   image). Moving to qrose would change only `QrEncoder.encode`; `QrRules` and `KitQr` stay.
2. **Byte mode only.** Dart `qr` may choose numeric or alphanumeric mode for some payloads, and then its version, and
   so its mask, can differ from ours. Every code still scans. Proving parity with Dart's mask on the 50 fixtures needs
   Dart-exported fixtures, which this unit did not produce.
3. **No Coil and no Telephoto.** Neither is in the catalog. `KitImageView` takes a `Painter`, and `KitZoom` is built on
   Compose gestures. `KitImage(ref)`, `KitImageResolver` and the loader are left to `:ui:kit` (C10-17) and to a later
   pass once the catalog has Coil.
4. **The PDF rasterizer lives in kit-content `androidMain`,** as the brief asked. Plan 10 puts it at
   `:android:media/pdf/PdfPageRasterizer`. S5-VIEWER may move it; the isolated service is not built here.
5. **Words.** The parts use the existing `strings_kit.xml` keys in `:ui:words` (owned by the l10n owner), so no new copy
   was added. Size units (B, KB, MB…) are symbols, as in Dart. Numbers use Latin digits, without locale formatting.
6. **Goldens were recorded in this container,** not on the VPS that plan 06 §15.3 names. Re-record them there before
   using them as a gate.
7. **Kit gaps covered by internal stand-ins:**
   - `contentAssert` stands in for `kitAssert`, which `:ui:kit` does not have yet.
   - `ContentTextAction` stands in for the quiet `KitButton` style.
   - Viewer More (⋯), Find, Copy and Wrap are not built: `KitMenu` does not exist yet.
   - There is no skeleton loading state: `KitSkeleton` does not exist yet.
8. **Not done from plan 06 §16.3:** the `api/<Part>.md` specs, capacity contract entries and scenes, an
   `AccessibilityRulesTest` pass, a check that the parts idle after one frame under reduced motion, and previews.
   `KIT.md` exists.
9. **Formatting.** `tool/format-changed.sh` does not exist in the tree, so the files were formatted by hand in the
   official style.

## Catalog and coordinator requests

- `MODULE_OWNERS` (coordinator only): add `:ui:kit-content	S	content-kit-owner	slice-3`. Until then
  `ModuleOwnersTest` in `:quality` will flag the new module.
- `libs.versions.toml` (build-logic owner), per plan 10 §2.1:
  - `qrose-encoder-matrix` 1.3.1, if qrose is still wanted (see deviation 1);
  - `zxing-core` 3.5.4, for tests (used here as a direct `com.google.zxing:core:3.5.4` test coordinate, as `:ui:kit`
    does for Roborazzi);
  - `telephoto-zoomable` 0.19.0;
  - `coil-compose`, `coil-svg` and `coil-gif` library entries (only the version `coil` exists today);
  - CameraX 1.6.2 and `zxing-cpp` 3.1.1, for the scanner slot.
- `ExternalRules`: add `"io.github.alexzhirkevich"` for kit-content and `:engine:media` if qrose is adopted.
- The kit owner: `kitAssert`, `KitMenu`, `KitSkeleton`, and the quiet/secondary `KitButton` styles. When they land,
  the two internal stand-ins here are deleted.

## Open problems

1. **ZXing's camera-style finder search** misses some large synthetic renders: versions 22, 26 and 32 in a sweep, and
   the 1,000-character fixture on Android graphics. Its pure-barcode decoder reads all of them, so the symbols are
   valid. Payloads up to 500 characters (Wi-Fi URLs, pairing links) pass the camera-style search, and a test enforces
   that. Whether a real camera has trouble with long handoff links needs the emulator-camera recipe (plan 10 §20.5).
   ISO-standard N3 scoring might choose friendlier masks, but that would break mask parity with Flutter.
2. **The rest of the roadmap row is open:**
   - the code block with `CodeTokenizer` (SyntaxMP benchmark first);
   - the log panel and buffer (B-KIT-021);
   - the scanner slot (CameraX + zxing-cpp; B-ANDR-049; `dependencyInsight` shows no ML Kit);
   - the terminal output view;
   - the capability explainer;
   - the checklist, progress and context rings;
   - the task chip;
   - `KitImage` and its resolver;
   - the §16.3 items in deviation 8.
3. **The `:ui:words` dependency.** The parts depend on `:ui:words` for kit words, as the module rules allow. If the
   kit-content owner prefers its own `strings_kit_content.xml`, plan 06 §13.2 says kit words live in `strings_kit.xml`,
   so the current choice follows the plan.
