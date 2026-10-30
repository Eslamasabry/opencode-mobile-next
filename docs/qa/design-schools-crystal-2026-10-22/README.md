# Crystal (sixth Style), 2026-10-22

Owner request (2026-10-22): "Can you do a crystal pure glass one also? Use best shaders available and this new library we spoke about a few days back". This reverses the 2026-10-02 no-glass rule for this opt-in style only. Solid stays the default, and Glass is unchanged.

Branch `feat/style-crystal` (from `8f456304b`). Package `liquid_glass_easy` **5.0.0** is pinned exactly (MIT). Its eight shaders are declared in the package's own pubspec, and Flutter bundles them, so the app's pubspec lists no assets. This is a pubspec and asset change, so it ships in a **new Shorebird release, not a patch**.

## What was built

| Piece | File |
|---|---|
| The only importer of the package: init (`LiquidGlassShaders.ensureLoaded`, bounded 400 ms wait in `main`), the probe (Impeller, and no high contrast, Saver, reduced motion, Motion › Off or accessible navigation), the token → `LiquidGlassStyle` mapping, the lens, lite glass and the per-page batch | `lib/ui/kit/glass/kit_crystal_engine.dart` |
| `KitSurfaceStyle.crystal` (appended; stored by name) and its spec: clearer than Glass on every pane, a rim lit on both edges, the H10 plate, H1 +50 weight | `lib/state/effects.dart`, `lib/ui/kit/style/kit_style_crystal.dart` |
| `KitStyleTokens.refracts`. `kitSurfaceBox(lens: true)` puts a real lens on a floating piece. A blurred piece with even corners that does not ask wears lite glass, and anything else blurs as Glass does. A `GlobalKey` keeps the content's state when the tree changes. | `lib/ui/kit/style/kit_style.dart`, `lib/ui/kit/kit_surface_look.dart` |
| These ask for a lens: the shell's pills and search, the message box and the dock (`KitGlass`), the jump pill, menus and the tip | `kit_glass.dart`, `kit_jump_pill.dart`, `kit_menu.dart`, `kit_tip.dart` |
| One `LiquidGlassBatch` per page, next to the H11 `BackdropGroup` | `kit_screen.dart` |
| The Style picker's sixth card, with en "Crystal" and ar "بلّوري" | `personal_settings_screens.dart`, ARBs |

These are never refracted: the transcript, code, diffs, the terminal, logs, fields, request cards (they do not pass `blur`) and the terminal's copied toast (it does not pass `lens`). The FAB stays solid mint (owner rule: the main action is opaque mint in every style). The dock's lens pill is not a lens, because that would put glass over glass. A sheet with top-only corners frosts (the Glass mechanism), because the package's lite glass needs even corners.

Lens values: index 1.28, a bend band as wide as the corner (14–26 dp), dispersion 4, magnification 1.04, saturation 1.25, a 3 dp specular and an 18 dp glow. The blur is 2.5 on clear pieces. Under a menu's thick tint (α ≥ .35) it is 12, so the page's words never show through the menu's words (pass 1 failed on exactly this). No touch flex and no morph, so nothing wobbles. Reduced motion turns the lens off.

## Proof

- **Emulator** `emulator-5584` (host GPU, `-gpu host`, Impeller **OpenGLES** through ANGLE, not SwiftShader), APKs 2590–2592. It showed **real refraction**: the server pill bends the aurora streak round its cap, where Glass blurs the same spot (`pass1-pill-zoom-crystal-dark.png` and `pass1-pill-zoom-glass-dark.png`). No crash, and no mirrored refraction.
- **Desktop shader path:** `flutter test --enable-impeller` renders the lens (`tool/capture/crystal_screens_test.dart`, `tool/capture/crystal_demo_test.dart`). The view's pixel ratio must equal the capture's, or the lens draws nothing.
- **Package demo comparison:** `pass*-demo-package-*.png` (the documented lens example) against `pass*-demo-crystal-*.png` (Crystal's mapping), over the same aurora.
- Images: `/home/eslam/Storage2/launch-footage/progress/crystal/` (passes 1–3, each pass logged in `LOG.md`).
- **Phone proof: pending.** The coordinator installs a build on the owner's phone.

## Frame timing (emulator, SurfaceFlinger present intervals, 3×3 swipes on Conversations, dark)

`gfxinfo` counts no frames for Flutter's SurfaceView, so I read the present times of the Flutter layer with `dumpsys SurfaceFlinger --latency` instead.

| Style | Mean interval | p90 | > 25 ms |
|---|---|---|---|
| Solid | 16.8 ms | 17.4 | 1 % |
| Glass | 37.1 ms | 47.1 | 99 % |
| Crystal (2592) | 38.9 ms (light 38.0) | 47.3 | 98 % |
| Crystal, live background off | 37.5 ms | 45.3 | 99 % |

On this emulator any backdrop read halves the frame rate, so Crystal costs the same as Glass. Phone numbers are still pending.

## Tests

- `test/kit/style/crystal_test.dart` covers:
  - the probe's fallbacks, including that a test (Skia) frosts as Glass;
  - every role under every knob;
  - AA through the plate over every live look at Bold;
  - no lens on the code block, the log body, a field, a request card, a reply or a card;
  - lite glass against the frost fallback;
  - a field keeping its state when the lens comes;
  - the style mapping;
  - only the engine importing the package;
  - the stored value round-tripping.
- `test/kit/kit_style_test.dart` now expects six styles, and blurs for Glass and Crystal. Its invariants loop over Crystal.
- Goldens: 32 new `*_crystal_*`. 11 changed only because the picker gained its sixth card (`kit_surfaces_appearance_*` ×10, `settings_appearance_loaded_1280x800_dark`). Every other golden is unchanged.
- The base commit's four `kit_surfaces_hooks_{brutal,paper}_*` goldens are stale: `8f456304b` did not regenerate them, and they are left untouched here.
- `flutter analyze lib test tool` is clean. G16/G17/G21/G49 ratchets pass, and so does `no_red_scan`.
