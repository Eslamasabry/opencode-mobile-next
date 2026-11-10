# feat/visual-language-v1: brought to STANDARDS §5 before it merges (2026-09-26)

This is STANDARDS.md §0.5 step 1. The foundation this builds on is recorded in
[`../visual-language-v1-2026-09-26/README.md`](../visual-language-v1-2026-09-26/README.md).

## 1. Scope

- **Unit:** coordinator pre-wave step §0.5.1 on `feat/visual-language-v1`.
  - **Finish line:** the branch's theme and kit tokens meet §5 for the items step 1 names, and tests prove them, so the branch can merge into `feat/phone-setup-v2`.
  - **Non-goal:** no screen is restyled or rearranged, and no kit part gains API beyond the named tokens and roles. G17, G18, G21 and G22 are not built here (that is §0.5 step 3).
- **Merge:** the latest `feat/phone-setup-v2` was merged first (`cca62454`). It brought docs and tooling only (STANDARDS.md, PLAN.md, work-units, the ARB merge driver, `.gitattributes`), with no conflicts.
- **Files changed (code):**
  - `lib/ui/theme_roles.dart`
  - `lib/ui/kit/glass/kit_glass.dart`
  - `lib/ui/kit/kit_tokens.dart`
  - `lib/ui/kit/kit_text.dart`
  - `lib/ui/app_theme.dart`
  - `lib/ui/screens/chat/composer.dart` (glow removed)
- **Files changed (tests):** `test/theme_roles_test.dart`, `test/app_theme_test.dart`, `test/theme_packs_test.dart`, `test/kit_glass_test.dart`, `test/workspace_stable_layout_test.dart`, `test/goldens/theme_gallery_golden_test.dart`, and 292 golden PNGs.
- **Pages (map ids):** none rebuilt. The whole app changes through the theme.
- **Specs followed:**
  - STANDARDS.md LOOK-1, LOOK-7, LOOK-8, LOOK-12, LOOK-13, LOOK-17, LOOK-20, LOOK-21, LOOK-39, KIT-9, TEST-6, TEST-8, TEST-19 and B15 (Appendix B);
  - visual language §2, §3, §4, §6 and §7.

### What step 1 asked, and what was done

| Item | Done |
|---|---|
| `ThemeRoles.glassRimLight`, `glassRimDark`, `glassShadow`, derived in `deriveRoles`, tested | Added as required fields. Graphite dark: white .20 / black .50. Graphite light: white .90 / black .10. The shadow is black .30 in both brightnesses (LOOK-20). `deriveRoles` copies them from the Graphite role set of the same brightness, so every pack and every custom theme has them. `copyWith` and `lerp` carry them. |
| `kit_glass.dart` rim and shadow from the roles | The rim painter takes `roles.glassRimLight` and `roles.glassRimDark`. The shadow is `KitTokens.glassShadows`: one `BoxShadow`, y 6, blur 16, colour `glassShadow`. No `Colors.white` or `Colors.black` remains in the rim or the shadow. |
| `KitTokens.rowValue` → secondary (14); `typedName` → mono 13 | Both are now the role style with no `fontSize` override. The row value keeps `text3`. |
| Every Material `textTheme` slot → one LOOK-12 role, no other size | See the table in `KitText.textTheme`: display*/headlineLarge → largeTitle 32; headlineMedium/Small, titleLarge → title 24; titleMedium → rowTitle 16; titleSmall, labelLarge, labelMedium → label 13; bodyLarge → body 16; bodyMedium, bodySmall → secondary 14; labelSmall → caption 12. `KitTextRole.button` went from 15 to 16/20 (LOOK-12). The top bar title is headline, no longer 20/26 (LOOK-17). No 57, 45, 36, 28, 20 or 15 is left. |
| `AppTheme.raised`: one value per brightness, or remove it; remove `AppTheme.glow` and its composer use | Both were removed. The only shadow is `KitTokens.glassShadows`. The composer's working ring stays, and the glow under it is gone. |
| `KitGlass` radius from a `KitTokens` name | `borderRadius` is now nullable. Null means `BorderRadius.circular(KitTokens.navRadius)` (22). |
| Orange in `graphiteAccents` → teal (B15), passing LOOK-8 and LOOK-39 | Teal is dark `#3CCFCF` and light `#0D7377`. The measurements are in the next table. |
| Update `app_theme_test` and `theme_packs_test` | `app_theme_test` now tests the one glass shadow, zero content elevations and the headline top bar, not `AppTheme.raised`. `theme_packs_test` now holds the theme each pack builds (not the pack's raw Material scheme) to the LOOK-8 floors, and the four hand-written packs are no longer exempt. |
| `tool/capture/fixtures.dart` loads Geist | Already done in `76095bd3`: `loadCaptureFonts` loads `assets/fonts/geist/Geist-*.ttf` as AppSans and `GeistMono-*.ttf` as AppMono. It was verified here and not changed. The goldens render in Geist. |
| Kit gate breaks baselined per file | None: `kit_ratchet_test` and `design_standard_test` stayed green, and no baseline changed. |

### Teal, measured (the test computes these)

| | Dark `#3CCFCF` | Light `#0D7377` |
|---|---|---|
| onAccent on accent (≥ 4.5) | 9.83 (ink) | 5.62 (white) |
| accent on ground (≥ 4.5; the task asked ≥ 3) | 10.27 | 5.06 |
| accent on surface1 (≥ 4.5) | 9.58 | 5.62 |
| hue distance from attention / danger (≥ 30°) | 144° / 180° | 148° / 178° |
| ΔE2000 from attention / danger (≥ 20) | 43.9 / 57.3 | 40.7 / 51.2 |
| ΔE2000 from green (distinct in the picker) | 21.9 | 21.7 |

`withAccent` leaves teal unchanged in both brightnesses, so the contrast guard does not have to move it.

- **Contract problems (PROC-20):** none that block. One gap was found (not blocking): **LOOK-39 fails for 11 of the theme packs.** Their accents sit next to attention or danger:
  - gruvbox, monokai, ayu, cobalt, paper, sunset, coffee and amber (dark and/or light);
  - rosePine light, sakura and highContrast dark.

  LOOK-39 also says `deriveRoles` moves a custom accent out of the band, and it does not yet. Step 1 names only `graphiteAccents`, so the packs were left alone and nothing guards them (G18 is "missing"). The proposal: the G18 unit asserts LOOK-39 on every pack, and `_guardAccent` rotates the hue out of the band. Measured with the same helper as `theme_roles_test`, in a scratch run that is not committed.
- **New kit parts (KIT-3):** none. New token: `KitTokens.glassShadows`, a getter.
- **Map items (EVID-11):** n/a. No page is owned by this step.
- **States per page (STATE-20) and deferred states (STATE-21):** n/a.

## 2. Builds

- Branch `feat/visual-language-v1`, worktree `/home/owner/Storage/Code/oc_app-visual`.
- **Base (after the merge):** `cca62454`.
- **Code head:** `7422c5cf` (theme and tests `2193c7d9`, goldens `7422c5cf`).
- No APK. This step does not build.

## 3. Devices

None: tests, goldens and renders only.

## 4. Runs

All runs used the pinned Flutter 3.47.1 (`91f8bd7`), with `-j 2` or less.

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Theme and kit tests: `theme_roles`, `app_theme`, `theme_packs`, `kit_glass`, `glass_surface`, `workspace_stable_layout` | pass | pass (`run-affected-and-gates.txt`) | PASS |
| 2 | Gates: `kit_ratchet_test`, `design_standard_test`, `accessibility_guidelines_test`, `text_scale_overflow_test` | pass | pass (same file, 165 tests with run 1) | PASS |
| 3 | Rim test fails with the old `Colors.white`/`Colors.black` rim | assertion failure | `Expected: a value greater than <133> Actual: <70>` (top pixel r 70, b 73), then restored | PASS |
| 4 | Goldens regenerated, one file at a time (23 files, `--update-goldens <file>`) | each passes | all 23 passed; 292 of 368 PNGs changed | PASS |
| 5 | Goldens verified without updating (23 files) | pass | 387 passed (`suite-chunks.txt`) | PASS |
| 6 | The other 431 test files, in 12 chunks of 34–38 files, each under 600 s | only the known base failures | chunks 00–02, 04, 07–09 and 11 passed. Chunks 03, 05, 06 and 10 failed only on the six known tests in row 7 (`suite-chunks.txt`). | PASS |
| 7 | The six known failures, run alone on the base `cca62454` | fail there too | all six fail on the base (`known-failures-on-base.txt`); the foundation record lists the same six | PASS (pre-existing) |
| 8 | `flutter analyze lib test tool` | no issues | "No issues found!" (`analyze.txt`) | PASS |

Row 6 is not a formal PROC-6 gate: it was run by hand in chunks, not with `tool/qa/run_serial_tests.py`. Chunks 00–10 ran on the tree just before the commits. The committed code is identical, apart from the workspace test fix, and chunk 11 (the chunk that holds that test) was rerun after the fix.

## 5. Evidence

- **Rule evidence (PROC-31):**

  | Rule | Test (`file` + `--plain-name`) or golden | Output |
  |---|---|---|
  | LOOK-39, B15 | `test/theme_roles_test.dart` "every Graphite accent keeps clear of attention and danger (LOOK-39)", "Graphite offers green, blue, teal and violet; teal replaced orange", "the ΔE2000 used for LOOK-39 matches the published reference" | `run-affected-and-gates.txt` |
  | LOOK-8 | `test/theme_roles_test.dart` (the same teal test and "every theme pack supplies the whole role set, readable"); `test/theme_packs_test.dart` "every theme keeps text and controls readable in both modes" | same |
  | LOOK-1, LOOK-20, LOOK-21 | `test/theme_roles_test.dart` "floating glass has its own rim and shadow roles in every theme"; `test/kit_glass_test.dart` "the rim paints glassRimLight on top, glassRimDark below", "the one shadow is the glassShadow role, y 6, blur 16"; `test/app_theme_test.dart` "no theme elevation lifts content (LOOK-20)" | same |
  | KIT-9 | `test/kit_glass_test.dart` "the corners default to the floating tab bar token" | same |
  | LOOK-12, LOOK-13, LOOK-17 | `test/theme_roles_test.dart` "every Material type slot is one role, with no other size", "a row value is the secondary role and a typed name is mono 13"; `test/app_theme_test.dart` "the top bar title is the headline role (LOOK-17)" | same |

- **Changed test expectations (TEST-19):**
  - `app_theme_test`: `AppTheme.raised(theme)` has length 1 → `KitTokens.glassShadows` is one 30 % black shadow, y 6, blur 16 (LOOK-20).
  - `theme_packs_test`: scheme floors of 4.5/3 on generated packs only → the built theme of every pack at LOOK-8 (text1 7, accent 4.5 on ground and surface1, danger and success 4.5) (LOOK-7, LOOK-8).
  - `workspace_stable_layout_test`: "390dp 1x … side-by-side dock" moved into a final group that loads the real fonts. With the 1em test font, the 16 px button label (LOOK-12) is 256 dp and can never share a compact row, so the case was only testing a glyph-width accident. The expectations are unchanged.
  - `theme_gallery_golden_test`: the "Raised surface with shadow" panel lost its content shadow (LOOK-20).
- **Goldens changed:** 292, in 23 golden test files. Each file was regenerated separately. These were opened and compared:
  - `settings_hub_dark`, `settings_appearance_light`, `shell_reconnecting_light`, `chat_permission_dark`;
  - `kit/kit_confirm_typed_ready_dark`, `kit/kit_foundation_work_412x915_dark`.

  What changed in them:
  - The top bar title is headline 17 (was 20).
  - Buttons are 16 (was 15).
  - Segment labels, theme names and chips (titleSmall/labelLarge) are label 13 (was 14).
  - The typed name is mono 13 (was 16).
  - The light dock's shadow is 30 % (was 12 %).
  - Row values are 14 (was 15).

  **Approved renders (EVID-12):**
  - `kit_foundation_work_412x915_dark` against `docs/design/visual-language-2026-09-26/Main.png`: the button and row type now matches the render (16 px buttons, 14 px values). The remaining differences are the render's large title and composer, which are screen work for wave 2.
  - `kit_confirm_typed_ready_dark` against `Confirm.png`: none in type or shape.
  - `settings_hub_dark` against `Settings.png`: the render's largeTitle page head and grouped panels are screen work (deferred to the Settings screen unit). The top bar now follows LOOK-17.
- **Before and after (EVID-10):** `before-*.png` come from base `cca62454` and `after-*.png` from `7422c5cf`. There are six pairs, one for each name in the "Goldens changed" list above.
- **Accessibility:**
  - Contrast floors are now tested on every pack's built theme.
  - Button text grew to 16, and `text_scale_overflow_test` and `accessibility_guidelines_test` pass.
  - Touch targets are unchanged.
- **Privacy and security:** n/a. No credentials, stored data, links or notifications changed.
- **Migration:** n/a. No stored format changed. The accent picker has no UI yet, so no stored `orange` value exists.
- **Found, not fixed (outside this step):** 24 golden-failure artefacts in `test/goldens/failures/` have been committed since `436aa35d` (TEST-12). They are on `feat/phone-setup-v2` as well.

## 6. How to reproduce

```bash
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
cd /home/owner/Storage/Code/oc_app-visual
$F test -j 2 test/theme_roles_test.dart test/app_theme_test.dart test/theme_packs_test.dart \
  test/kit_glass_test.dart test/glass_surface_test.dart test/workspace_stable_layout_test.dart \
  test/kit_ratchet_test.dart test/design_standard_test.dart \
  test/accessibility_guidelines_test.dart test/text_scale_overflow_test.dart
# goldens, one file at a time (only when deliberately regenerating)
$F test -j 1 --update-goldens test/goldens/settings_golden_test.dart
$F analyze lib test tool
```

## 7. NOT proven

- The app was not run on a device or emulator. The 30 % glass shadow in light and the 1-physical-pixel rim have only been seen in goldens at DPR 1 and 3.
- The liquid-glass path (Impeller shader) was not run. `kit_glass_test` covers frosted glass, and the rim painter is shared.
- LOOK-39 for the theme packs and for custom accents: 11 packs fail it (see Contract problems). No gate checks it.
- The whole suite was not run as a PROC-6 gate with `run_serial_tests.py`. The chunks in row 6 cover every file once, and six tests fail as they do on the base.
- §0.5 steps 2–8 (the kit seams, the W1 gates, `work-units.json`, the workflow, the Flutter version drift, the global secure-storage mock, and the design-doc banners) are not part of this step.

## State

| State | Yes/No | Where |
|---|---|---|
| Implemented | Yes | `feat/visual-language-v1` |
| Enabled | Yes: the theme applies to the whole app | |
| Verified | Tests and goldens only | this record |
| Committed | Yes | code head `7422c5cf` |
| Deployed | No | |
| Released | No | |
