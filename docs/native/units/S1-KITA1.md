# S1-KITA1 · kit part A: themes, packs, frames, rows, notice, details fold

Date: 2026-11-01. Branch: `native/kita1-2026-11-01`, created from `claude/kita1-kit-parts-port-o9m3m6` at `6b9a2b21` (the M0 tip).
No PR, no CI run, no release.

Inputs read: `native/AGENTS.md`; `docs/native/slice0/P7-kit.md`; plan 01 (binding), plan 15 (unit row S1-KITA1), plan 06
§1–§12, §14, §16 and §18, plan 08 §2; the kit-api specs `KitScreen`, `KitTopBar`, `KitStatusLine`, `KitRow`, `KitNotice`,
`KitDetailsFold`; the Dart sources `lib/ui/theme_roles.dart`, `theme_packs.dart`, `theme_packs_generated.dart`, and
`lib/ui/kit/kit_layout.dart`, `kit_screen.dart`, `kit_system_bars.dart`; the Dart tests `test/theme_roles_test.dart`,
`test/theme_packs_test.dart`, `test/appearance_final_test.dart` and `test/support/no_red.dart`.

**Shipping state:** implemented, tested locally (desktop and Robolectric), committed and pushed. It is not merged and has
not been compared with Flutter or checked on a device.

**Finish line, honestly:** the 31 packs, accents, Material You, the frames, the row family, `KitNotice` and
`KitDetailsFold` are built and tested, with Roborazzi goldens in light/dark × en/ar. The plan 15 finish line also asks for
"the capacity, manifest, Konsist and literal gates live" and the full plan 06 §16.3 checklist for each part. Those are
**not done** (see §6). This unit is a working slice, not a finished one.

---

## 1. What was built

### 1.1 `:ui:design` (pure Kotlin)

| File | Content | Flutter source |
|---|---|---|
| `color/ColorMath.kt` | `DColor`: float-channel colours, the way Flutter 3.27+ holds them. `mix` (Dart `Color.lerp`), `alphaBlend`, luminance, contrast, HSV hue, CIE Lab, `deltaE2000Lab` (Sharma–Wu–Dalal, line by line) | `theme_roles.dart`, dart:ui |
| `color/RoleDerivation.kt` | `deriveRoles`, `readableOn`, the accent guard, `onColor`, `accentKeepsMeaning` (LOOK-39), `Roles.withAccent`, `Roles.withPartner` | `theme_roles.dart` |
| `color/Contrast.kt` (extended) | public `hueDistance`, `deltaE2000`, `isRedHue` (Dart `test/support/no_red.dart`) | |
| `color/Packs.kt` | `PackId` (31 values, `storageName` = Dart name, unknown → Graphite), `PackPalette`, `Pack`, `Packs.of(id, dynamic)`, `Packs.mostUsed`, `Packs.curated` | `ThemePackId`, `ThemePalette`, `effectiveThemePack` |
| `color/CuratedPacks.kt` | Catppuccin, Gruvbox, Solarized: the slots the derivation reads, value for value | `theme_packs.dart` |
| `color/PackRecipe.kt`, `color/PacksGenerated.kt` | `_build` for the 26 generated packs (the `t += .04` float loop reproduced exactly); the 26 specs as data, extracted from `_specs` with a one-off script | `theme_packs_generated.dart` |
| `color/Accents.kt` | the 7 Accent swatches and Graphite's 4 accents; `byId` (unknown → Match theme) | `appearanceAccents`, `graphiteAccents` |
| `color/Dynamic.kt` | `DynamicTones`, `DynamicSource.toPack()` (fixed success colours) | `dynamicThemePack` |
| `color/ThemeResolver.kt` | pack → swatch as partner → Saver (Saver always uses the dark palette) | `main.dart` + `AppTheme.fromPalette` |
| `color/StatusTone.kt` | `StatusTone` → text tone + `StatusGlyph` (plan 06 §4.10) | `AppStatusTone` |
| `layout/Layout.kt`, `WindowClass.kt`, `CapRole.kt` | every width constant; the window classes and the short rule; the 13 capped roles plus Fill/Wrapper/Scope with Dart alignments; `sidebarWidth`; `showsDetail` and `showsSide` (Dart's "panes follow the body" rule) | `kit_layout.dart`, `kit_max_width.dart`, `kit_screen.dart` |
| `effects/Appearance.kt` | `KitAppearance` gains `pack` and `accent` (defaulted, so existing callers compile) | |

### 1.2 `:ui:kit`

| File | Part |
|---|---|
| `KitTheme.kt` | Resolves the roles with `ThemeResolver`, so packs, accents, Material You and Saver all apply. Calls `platform.dynamicSource()` only for `PackId.Dynamic`. Measures the window into `LocalKitWindow`. Calls `platform.SystemBars(...)`. |
| `platform/KitPlatform.kt` | Adds `dynamicSource()` (default null) and `@Composable SystemBars(darkContent, ground)` (default no-op). Both are defaulted, so P7's desktop and fake platforms compile unchanged. |
| `androidMain/…/AndroidKitPlatform.kt` | API 31+: `dynamicDark/LightColorScheme(context)` → `DynamicTones`. System bars, as Dart `KitSystemBars`: transparent status bar, navigation bar in the ground, icons opposite the theme, no navigation contrast scrim. |
| `theme/KitLocals.kt` | `KitWindowInfo(widthDp, heightDp, finePointer)` with `cls`, `short` and `modal`, and `LocalKitWindow` (dynamic). |
| `foundation/KitMaxWidth.kt` | `Modifier.kitMaxWidth(role, align)`, a port of `_RenderKitMaxWidth`: it wraps the child, and start/end mirror in RTL. |
| `foundation/KitAssert.kt` | `kitAssert` (plan 06 §16.1): throws only when `isDebug`. `KitRuleBroken`. |
| `foundation/KitInlineAction.kt` | `KitLineAction` (words, `enabled` + `disabledReason`) and the internal tertiary words action. |
| `status/KitStatus.kt`, `KitStatusLine.kt`, `KitStatusSlot.kt`, `KitStatusGlyph.kt` | `KitStatusKind` (7, priority order), `KitStatus` with `highest` and `announcementKey`; the line (glyph, words, supporting, Now line, action, honest dismiss; the action stacks under the words at ≥ 1.29×; one polite live region; a risky switch cannot be dismissed); `KitStatusScope`, `KitStatusSlot`, `KitStatusContribution` and `KitStatusLineFold` (one line per window). |
| `frame/KitScreen.kt` | `KitScreen` (bar → status → search → header → loading line → body → bottom lifted above the IME; `KitScreenWidth` Full/Reading/List from medium up; owns the slot or contributes to it; hosts `KitSheetHost`) and `KitPaneScreen` (list 296 · detail ≤ 700 · side 340; Dart's room rule; separate traversal groups; mirrored in RTL). |
| `frame/KitTopBar.kt`, `frame/KitExitGlyph.kt` | Title (Headline, frame text, 2 lines from 1.3×), subtitle, Back at the start / Close at the end, a switcher title, actions limited to 2 on compact and 3 on medium (all on wide), and the `more` overflow slot. Hidden actions without an overflow fail `kitAssert`. |
| `frame/KitPageHeading.kt` | LargeTitle plus one quiet line, as a heading. |
| `nav/KitNavRail.kt`, `KitSidebar.kt`, `KitNavDestination.kt` | Rail 80 dp. Sidebar 296 → 400 dp or a third of the window. Labels capped at 2.0× (`navLabelMaxScale`). The chosen item sits on `surface3` with `selected` and Tab semantics. Needs-you count is amber (an approved meaning), capped at 1.3×, with its words as the state description. |
| `row/KitRow.kt`, `KitRowParts.kt`, `KitRowGroup.kt`, `KitSectionLabel.kt`, `KitGroupNote.kt` | `KitRow`: leading tile (glyph or initials); trailing None, Chevron, Value or Action; `server` prefix; file-name titles with break chances only after `_`/`-` and before the extension; supporting lines 1/2/3 at 1.0/1.3/2.0; a disabled row states its reason; `selected`; `growKey` (plan 08 grow). `KitRowGroup`: flat Solid list with one-pixel inset hairlines, mirrored in RTL, and a `destructive` slot that is always last behind a full-width rule. Plus the section label and the group note. |
| `state/KitNotice.kt` | `KitNotice` (tone glyph, title, message, notes, ≤ 2 actions, honest dismiss, live region), `KitCostNotice`, `KitOfferNotice`, `KitErrorNotice`, and `KitErrorKind.of(Failure?)`. |
| `state/KitDetailsFold.kt` | `KitDetailsFold` and `KitTechnicalValue`. Takes `Redacted` only. Collapsed by default, with expand/collapse semantics actions. Values are deduplicated; values and text are mono, LTR and selectable; the text shows 12 lines, then "Show all"; Copy all. Nothing to show → nothing drawn. |
| `KitParts.kt` (the `KitScreen` half only), `KitRow.kt` | The slice-0 `KitScreen(title, backLabel, onBack, content)` and `KitRow(title, supporting, onClick, modifier, growKey)` keep their signatures. They now delegate to the frame and row parts. The `KitButton` half of `KitParts.kt` was not touched. |

### 1.3 Tests

| Suite | Count | Rows |
|---|---|---|
| `:ui:design` `PacksTest` (new) | 16 | B-KIT-009, 056, 061, 062 |
| `:ui:design` `LayoutTokensTest` (new) | 5 | B-KIT-029, 035 |
| `:ui:design` `DesignTokensTest` (P7) | 9 | unchanged, green |
| `:ui:kit` `PartADesktopTest` (new, `runComposeUiTest`) | 28 | B-KIT-001, 010, 024, 027, 029, 035, 051, 052, 053, 061 |
| `:ui:kit` `KitSeamsDesktopTest` (P7) | 7 | unchanged, green |
| `:ui:kit` `PartAGoldensTest` (new, Robolectric SDK 37 + Roborazzi 1.77.0) | 36 | goldens (§3) |

The scenes are shared by the desktop tests and the goldens, in `commonTest/PartAScenes.kt`.

---

## 2. Catalog rows covered

These rows are covered at the logic or desktop-compose level. Each test is named after its row:

- **B-KIT-001:** Back and the chevrons mirror, and Close does not.
- **B-KIT-009:** every swatch in every pack, guarded to AA and kept away from amber; Match theme clears it.
- **B-KIT-010:** the pack, accent and mode reach every kit colour.
- **B-KIT-024:** the network/other classifier over `Failure`; the error notice's fix; the redacted fold.
- **B-KIT-027:** supporting lines, file names, the trailing value under the title at large text, destructive last.
- **B-KIT-029:** window classes, caps and alignments, `kitMaxWidth`, frame order, widths, panes, the rail and sidebar.
- **B-KIT-035:** priority, ties keep the first, one line per window, app-wide before the screen, risky switch, stacking.
- **B-KIT-051:** a disabled row says why, once, and does not act.
- **B-KIT-052:** the bar title is frame text, capped at 1.3× at 200 %.
- **B-KIT-053:** a missing appearance gives Graphite dark.
- **B-KIT-056:** no pack is red, using Flutter's red test.
- **B-KIT-061:** 31 packs, storage names, complete and distinct palettes, the floors, a disabled button in every pack, Material You and its fallback.
- **B-KIT-062:** custom derivation, the accent swap, Graphite's accents, ΔE2000 reference pairs, LOOK-39.

B-KIT-003 (Graphite floors) stays covered by P7's `DesignTokensTest`.

These rows are only partly covered: B-KIT-029's two- and three-pane fold in a screen with its debug "one of each" audit (§6), and B-KIT-035's countdown and 8 s escalation (`KitSince` is not built).

---

## 3. Commands and results

All commands run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`. JDK 17 (17.0.20.1) was installed as the toolchain.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :ui:design:desktopTest` | 30 / 30 |
| 2 | `./gradlew :ui:kit:compileKotlinDesktop :ui:kit:compileAndroidMain` | BUILD SUCCESSFUL |
| 3 | `./gradlew :ui:kit:desktopTest` | 35 / 35 (28 new + 7 P7) |
| 4 | `./gradlew :ui:kit:testAndroidHostTest --tests '*PartAGoldensTest*' -Proborazzi.test.record=true` | 36 captures written under `ui/kit/src/screenshots/` |
| 5 | `./gradlew :ui:kit:verifyRoborazziAndroidHostTest` | 55 / 59. `PartAGoldensTest` 36/36, `TextScaleCapTest` 3/3, `AndroidKitPlatformTest` 2/2 and `KitFrostFallbackGoldenTest` 2/2 pass. **`KitGoldensTest.kitSheet` fails in 4/4 variants**; see §5, D9. |
| 6 | `./gradlew :feature:chats:compileKotlinDesktop :ui:shell:compileKotlinDesktop` | Not runnable here: `:engine:connection` fails to compile on the base branch (`ConnectionRegistry.kt`, `ConnectionSession.kt`), from the parallel contract reconciliation. The slice-0 `KitScreen`/`KitRow` signatures they use are kept. |

Goldens recorded (412×915 at 420 dpi unless noted; light/dark × en/ar):

- `KitScreen/status-loading`, `KitRow/group`, `KitStatusLine/tones`, `KitNotice/kinds-fold-open`, `KitNav/rail-sidebar` and `KitPageHeading/default`;
- `KitTheme/pack-{opencode,catppuccin,gruvbox,solarized}`;
- `KitPaneScreen/selected-1280x800` (160 dpi);
- `KitTopBar/frame-cap-scale-2.0` (fontScale 2.0).

What I checked by eye: the RTL mirroring (Back, chevrons, hairline insets and list pane on the right), LTR technical values in Arabic, the amber needs-you count, and the dark and light grounds. I did not compare them with Flutter goldens or a device.

---

## 4. Environment (container only, not in the repo)

- Android SDK: `commandlinetools-linux-16111833` → `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`.
- `native/local.properties` (gitignored).
- `~/.gradle/init.d/central-mirror.gradle.kts`: Maven Central answers 429 here, so Google's Central mirror goes first, Central is dropped, and the plugin portal (which redirects to Central) goes last.
- `~/.gradle/gradle.properties` lists JDK 21 and 17.
- The Robolectric jars for SDK 37 and 30 were seeded into `~/.m2` from the mirror, with their SHA-1 values checked.
- A throwaway Dart SDK plus material_color_utilities 0.13.0 (the version in `pubspec.lock`) computed the `fromSeed` tertiaries (D2).

---

## 5. Deviations

| # | Plan / spec says | Built | Why |
|---|---|---|---|
| D1 | Floors hold exactly (Dart tests) | The derivation runs on float colours and rounds once to 8 bits. The tests allow 0.035 for that rounding. | Flutter derives and measures float colours. Five packs land at 4.47–4.49 after rounding, which is what Flutter paints too. The derivation is not loosened. |
| D2 | Port `generatedThemePack` / curated packs | Ports only the slots the role derivation reads. Each pack's `fromSeed` **tertiary** is committed as data, computed once with material_color_utilities 0.13.0 `SchemeTonalSpot`. | The partner falls back to the scheme's tertiary when the secondary sits near amber (Gruvbox, for one). Porting HCT is out of scope. [U] Flutter 3.47's `fromSeed` spec version is assumed to be MCU's default. |
| D3 | `KitEnabled` (06 §16.2) and `KitAction` | Rows use `enabled` + `disabledReason` (the existing `KitTappable` shape). Line actions use `KitLineAction`. | S1-KITA2 owns `KitAction`/`KitEnabled` and had pushed nothing yet. Converge when its part lands. |
| D4 | Glyphs from `KitIcon` / `KitGlyphs` | Status glyphs and exit glyphs are drawn in kit `Canvas`. Destinations and leading tiles take an `ImageVector`. | The glyph generator (K0) does not exist yet. |
| D5 | `KitLoadingBar` in `KitScreen` | A still 2 dp accent band with progress semantics, private to `KitScreen` | `KitLoadingBar` is part B. Swap it in when it lands. |
| D6 | `KitDetailsFold` copies through `KitCopy` | Uses the deprecated `LocalClipboardManager`, with suppression. Values and text must already be `Redacted`. | `KitCopy` and `clipEntry(sensitive)` are not built. |
| D7 | `KitDetailsFold` in `modal/` | In `state/`, beside `KitNotice` | Its users are the notice and state view. Move it at the contract freeze if wanted. |
| D8 | Dart test 9 says threePane hides the side pane at 1280×800 | Ports Dart's code: the side shows when body − 296 − 340 ≥ 360 | The code wins (06 rule 0). It is hidden at 1280 only when a sidebar takes 296. |
| D9 | — | The slice-0 `KitScreen` now draws a real `KitTopBar`, whose title is Headline per B-KIT-003. This changes P7's `KitSheet/open-*.png` (the sheet scene sits on that screen). | Those PNGs are part B's goldens, so I did not re-record them. **The sheet owner or coordinator re-records them:** `./gradlew :ui:kit:testAndroidHostTest --tests '*KitGoldensTest*' -Proborazzi.test.record=true`. |
| D10 | Kit words from `strings_kit.xml` | Labels are parameters (`exitLabel`, `copyLabel`, `dismissLabel`…), as P7 D8 did | Adding `:ui:words` to the kit's build file is a shared-file edit. |
| D11 | `KitRow(destructive)` reorders in groups | `KitRowGroup(destructive = { … })` slot | It is explicit and needs no child inspection. |
| D12 | The sidebar and rail are drawn by the shell scenes | Parts only (rail, sidebar, destination); no `KitNav` scaffold | The scenes are S1-SHELL's. |
| D13 | The slice-0 `KitRow` played `Select` on tap | It no longer does | Plain taps are silent (MOT-11). |

---

## 6. Not done (open problems)

1. **Gates:** the capacity JSON and scenes (`KitCapacityTest`), `KitManifestTest`, Konsist rules and `LiteralBanTest` are not built. The kit KDocs do carry `States/Text/Cap/Motion/Hidden/Replaces` lines, so the manifest gate can be added. No `api/<Part>.md`, `KIT.md` rows or `kit_renames.tsv` yet. No `@Preview`s (they need `ui-tooling-preview` on the kit, which is a build-file change).
2. **KitScreen:**
   - no debug "one of each" audit;
   - no `KitBottomInset` publication;
   - no search slot type (it is a lambda);
   - `jump` and the snack-bar host are not ported.
3. **Status line:** no `since` escalation (`rememberKitSince`), no countdown, no `more` actions, and no `KitEntrance` on change. The slot fold uses `AnimatedVisibility` directly; `KitReveal` belongs to S2-KITB.
4. **Top bar:** no `accessory`, `scope`, `brand`, shell controls or tail line (K2). An `auto` exit needs the navigator, so the caller passes `Back` or `Close`.
5. **Rows:**
   - no menu, swipe twins or custom actions (they need `KitMenu` from part B);
   - no `KitRow.unavailable` with `enable`;
   - no `LazyListScope.kitRowGroup`;
   - no `KitFeedItem` (slice 2).
6. **Notice:** `KitReportHook` is not built; the caller passes `report`. Copy details lives in the fold.
7. **Goldens:** recorded in this container, not on the VPS (plan 06 §15.3). Re-record them there. Not yet captured: the style matrix, Saver, high contrast, the 26 generated packs, or 280/1600 widths.
8. **Material You** is not checked on a device. Android's dynamic tones may differ from Dart's `CorePalette` (plan 06 §4.2 [U]).
9. **The four `KitSheet` goldens are red** until re-recorded (D9).

## 7. Catalog requests (for the build-logic owner)

- None new. P7's requests still stand: catalog aliases for `ui-test-junit4`, `ui-test-manifest` and the Roborazzi libraries; the Robolectric JVM flags in `oc.kmp.library`.
- For previews: `org.jetbrains.compose.ui:ui-tooling-preview` on `:ui:kit` `commonMain` (catalog alias `compose-ui-tooling-preview` exists).
- For kit words: `:ui:words` on `:ui:kit`.

## 8. Coordination with S1-KITA2

- **Shared files I edited:**
  - `KitParts.kt`: only the `KitScreen` block above `KitButton`;
  - `KitTheme.kt`, `KitPlatform.kt` and `KitLocals.kt`: additive changes.
- **New foundation files** others may also add:
  - `foundation/KitAssert.kt` (`kitAssert`, `KitRuleBroken`): if S1-KITA2 adds the same file, keep one;
  - `foundation/KitMaxWidth.kt`.
- **Converge on `KitAction` and `KitEnabled`** (D3).
