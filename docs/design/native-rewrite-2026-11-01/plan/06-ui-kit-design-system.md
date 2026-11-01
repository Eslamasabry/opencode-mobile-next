# 06. UI kit and design system

Written 2026-11-01. Status: **plan, binding for every kit and screen agent** once the coordinator accepts it. It sits
under `01-architecture-decision.md`. Where this file and §01 disagree, §01 wins and this file gets fixed.

## In plain words (for the owner)

1. The new app gets one kit of building blocks, the same as today. Every button, row, sheet and chat part comes from
   it, and a robot check fails any screen that builds its own.
2. The kit keeps today's look exactly. The colours, the Geist font, row heights, corners, the five Styles, the 31
   colour themes and "no red, amber means needs you" all carry over. Solid stays the default and must look the same.
3. On Android the kit is built from Google's own parts, so ripples, predictive back, stretch scrolling, haptics and
   the keyboard animation are the phone's own, not imitations.
4. Your rules are built into the kit: hard maximum widths, frame text capped at 1.3× while reading text grows fully,
   Arabic right-to-left, 150–250 ms motion that switches off with reduced motion, plain words, no glass outside the two
   frost places.
5. The Widgetbook catalog is replaced by previews of every kit part and screen, rendered on the build server as one
   screen per image. All 7 languages move from the ARB files to the native string files once, and stay there.

---

## 0. Scope, inputs and how to read this file

**This section owns:** tokens (colour roles, packs, Styles, type, spacing, shapes, elevation, motion tokens, haptic
events), theming, the `KitPlatform` seam, the Flutter-to-Compose part map, max widths and the capacity contract, the
text-scale rule, adaptive layout, accessibility, RTL, localization and the string pipeline, how kit-only is enforced,
and previews and the catalog.

**This section does not own:** the navigation shell, scenes and the predictive-back policy (§01 §9.4 and the
screens/navigation section). It does not own the transcript hot path (§01 §9.3 and the chat section) or screen
layouts (feature sections). It only names the kit parts those sections use.

**Inputs read:** `01-architecture-decision.md` (binding); `map/ui-kit.md` (58 features, UI-KIT-01…58);
`map/screens-nav.md` §1, §5; `research/motion-native.md`, `kmp-cmp-status.md` §3, §6, §12.4, and `testing-perf.md`
§4–§5; `catalog/BEHAVIORS.md` (KIT 62 rows, L10N 24 rows); the Flutter source. **The source of truth is the final
Flutter state, `int/freeze-2026-11-01` at `fc5c1e78f`** (worktree `/home/eslam/Storage/Code/wt/oc_app-freeze`). It
contains all of `int/batch1-2026-11-01` (`65e3f70a3`, worktree `/home/eslam/Storage/Code/wt/oc_app-batch1`) plus 384
commits: the 1.3× frame cap (`KitChrome`), the row-title fix, large-text approval chip, app updates, Linux vitals, paste
image and partial-syntax streaming. Read in particular `lib/ui/kit/`, `lib/l10n/`, `docs/ux-system/kit-v2.md`
§8–§9, `screen-standard.md` and `test/kit/kit_capacity.json`. Also the demo `kmp-demo/shared/.../ui/*` and the owner
memory files (`design-standard-rule`, `component-max-width-rule`, `text-scale-cap`, `android-native-feel`,
`no-glass-rule`, `design-schools-direction`, `micro-animations-wanted`, `teach-what-is-hidden`, `judge-visuals-hard`,
`widgetbook-catalog`, `freeze-owner-decisions-2026-11-01`).

**Tags** (as in §01): **[V]** checked today against a primary source, **[R]** read in the repo, a map, research or
memory, **[U]** unverified, proved in slice 0 or in the named work package (§18).

**Words used:** *part* means one kit composable (`KitRow`). *Frame text* is chrome text, capped at 1.3×. *Reading
text* scales fully. *Kit owner* means the S owner of `:ui:design` + `:ui:kit`, who is also the design authority
(§01 §5.2). *Chat-kit owner* and *content-kit owner* own `:ui:kit-chat` and `:ui:kit-content`.

**Widget tests.** This section ports **none** of the 7,967 Flutter widget tests (owner: later; §01 row 18). The
kit gates in §8, §14 and §17 are the native kit's own definition of done. Each part's gates are written when that part
is built. They are also the zero-cost groundwork for the later widget-test phase: the capacity JSON, the stress
fixtures and the test tags carry over unchanged. If the owner counts these gates as part of "later", the fallback is
in §19 Q2.

---

## 1. Decisions in one table

Every row is final for agents unless §19 lists it as an owner question.

| # | Topic | Decision | Why |
|---|---|---|---|
| K1 | Kit modules | `:ui:design` (pure Kotlin tokens and policies), `:ui:kit` (foundation, frames, rows, inputs, modals, states, appearance, teaching), `:ui:kit-chat`, `:ui:kit-content`. Exactly as §01 §5.2. | §01 binding |
| K2 | Part names | **Same names as Flutter** (`KitRow`, `KitSheet`, `KitTurn`…). Every rename, merge and drop is one row of the machine-readable map `ui/kit/capacity/kit_renames.tsv` (§7.0); nothing else changes name. | The capacity JSON, the 111 `kit-api/*.md` specs, catalog rows and agent memory all key on these names |
| K3 | Material 3 | Built on the M3 1.5 line (CMP `material3` 1.13.0-beta01), used **only inside kit modules** as an implementation detail. No screen sees an M3 type. | §01 row 14; native feel for free |
| K4 | Colour model | `Roles` (24 roles as ARGB `Int`) in `:ui:design` is the truth. The M3 `ColorScheme` is derived from it for the few wrapped M3 parts. | Map UI-KIT-01 note; M3 lacks `surface1–3`, `partner`, `attention*` |
| K5 | Motion | Kit tokens, ported from Flutter `KitMotion`, plus `KitMotionScheme` (the numbers of `MotionScheme.standard()`) passed to `MaterialTheme`. Never `expressive()`. | §01 §9.4 |
| K6 | Icons | Phosphor glyphs become **generated `ImageVector`s**, extracted once from the same three Phosphor 2.1.0 TTFs the Flutter app ships (§4.11). Brand marks (79 SVG) go through the same generator. Fonts are not shipped for icons. | Same shapes as today; synchronous; `autoMirror`; about 1.5 MB less in the APK |
| K7 | Fonts | Geist and Geist Mono variable TTFs as kit compose resources, with weights set through `FontVariation.weight`. In the Arabic locale the sans family is the system sans, with letter spacing 0. Mono stays Geist Mono. | Same as Flutter `AppTheme.forLocale`; the demo proved variable Geist on Android [R] |
| K8 | Frame-text cap | One common `Density` wrapper caps `sp → dp` at 1.3× the unscaled size and lets the platform's non-linear scaling through below the cap (§9). | Owner rule 2026-10-31; works on every target |
| K9 | Sheets, dialogs, confirm, undo | **Declarative, screen state** (§01 §9.4). Rendered in-window by `KitSheetHost` (§01 §9.4), which also hosts dialogs, confirm and the undo bar, on the M3 1.5 in-window `BottomSheet`. Menus, tips and term bubbles use foundation `Popup`. | One inset model; built-in predictive back; shared elements work |
| K10 | Pickers | `KitDateTimePicker` wraps M3 1.5 `DatePicker` / `TimePicker` (input mode included) in kit chrome. It replaces 1,431 lines of custom Dart. Fallback: port the custom picker. | Android-native feel; judge by what it removes |
| K11 | Secret field | `KitField(kind = Secret)` is built on `BasicSecureTextField`, never prefilled (a stored value shows "Saved · Replace" and no input until Replace), no autofill, `IME_FLAG_NO_PERSONALIZED_LEARNING` (16 SI-4, N16-D16). Clipboard copies of secrets set Android 13+ `EXTRA_IS_SENSITIVE`. | Native privacy, no IME learning, Flutter parity (DESKTOP-WEB-CROSS-64) |
| K12 | Frost | In the kit, in three pieces. `KitFrostState` is hoisted by `KitScreen`. `Modifier.kitFrostSource(state)` goes on the content that scrolls behind (the transcript list) and records it into a `GraphicsLayer`; it is a kit modifier on the feature allowlist, so `:feature:chat` never touches `graphicsLayer`. `Modifier.kitFrost(role, state)` on the composer and the chat header draws that layer through a `RenderEffect` blur on API 31+. Compose cannot blur "what is behind" without a source, which is why the source exists. Exactly two roles (composer, chat header). **Fallback if P7 fails: a solid surface.** Haze is not a fallback: `12-package-registry` X-21 bans it. | §01 D10, §7.6; §12 X-21 |
| K13 | Live backgrounds, illustration scenes | Not in the switch release (§01 D11). The prefs are kept. `KitStateView` uses its icon tile. The `KitIllustration` API name is reserved. | §01 D11 |
| K14 | AI Team and FactsBook parts | After the switch (§01 D6), into `:ui:kit-content`, package `…ui.kit.content.team`. Their strings are converted now (§13.3). | §01 D6 |
| K15 | Strings | One-off ARB → CMP XML conversion into `:ui:words`, one file per feature per language. Android `res` is generated from `strings_os.xml`. **7 shipped languages** plus two qa-only pseudolocales (§13). | §01 row 5, §7.5 |
| K16 | Locale switching | `oc.appLocale` stays the source of truth. It is applied through `LocaleManager` on API 33+ and through `Locale.setDefault` + configuration override on API 26–32, both before the first string read (§13.6). | Keeps B-L10N-001/002 behaviour; P6 proves CMP follows it |
| K17 | Text direction of reading text | User and agent content (prompts, replies, tool output words) uses `TextDirection.Content`, chosen per paragraph. Frame text follows the app locale. Technical values are always LTR. | An English reply in an Arabic UI reads left to right. Owner check in §19 Q4. |
| K18 | Kit-only enforcement | Gradle (only kit modules see material3), Konsist import allowlist and literal bans in `:quality`, the manifest gate, and the capacity gate (§14). | §01 §6.3 |
| K19 | Previews and catalog | `@Preview` in `commonMain` for every part state. Roborazzi records them on the VPS. The owner-facing static catalog is built from those Android renders. The `:desktopApp` gallery is for agents. Widgetbook retires with Flutter. | §01 row 16; owner "one screen per image" |
| K20 | Golden host | VPS only (§01, testing-perf §5.2). | Deterministic |
| K21 | No red | No red role exists. A colour-literal scan with exactly two exemptions (`PacksSignal.kt`, `MarkupInk.kt`), ported from `test/no_red_scan_test.dart`. | Owner 2026-10-16, R2 |
| K22 | Press feedback | Platform ripple (`RippleDrawable` under Compose) at 12 % of the part's ink, bounded to its shape. **No press-scale** (the demo's `pressScale` with bouncy springs does not survive). | Owner native feel; the demo lesson |

---

## 2. Owner rules and where each one is enforced

| Owner rule (memory or map id) | Enforced by | Test or check |
|---|---|---|
| Kit only (R1, `design-standard-rule`) | Gradle dependency rule; Konsist feature import allowlist; literal bans (§14) | `:quality` `KitOnlyRuleTest`, `LiteralBanTest` |
| No red (R2) | No red role in `Roles`; neutral destructive fill in `KitButton`; colour scan | `NoRedScanTest` (B-KIT-056) |
| Amber = the person is needed (LOOK-4, LOOK-24) | Enforced by **meaning**, not by a short file list. Parts reach amber through the kit's attention APIs: `TextTone.Attention`, `StatusTone.Attention`, `KitChipTone.Attention`, `KitSheetTone.Attention`, `KitNeedsYou` and the style's `NeedsYouLook`. Raw `attention*` role reads are allowed only in the files on `ui/kit*/capacity/attention_allowlist.txt`, seeded from the Dart files that read them on the freeze tip (`grep -rlE 'attention' lib/ui/kit`: 42 files, mapped to their native files) and shrink-only. The approved amber meanings, each from shipped code: needs-you marks and request cards; the stuck Stop chip (owner 2026-10-30); the Stop-and-send pill; a step or live line waiting on the person; the context ring from 70 % to 95 %; the app-update dot. | Konsist `AttentionRoleUseTest` (allowlist + API-only rule); review checklist §14.7 |
| No glass, frost in 2 places (R3) | `FrostRole` enum has exactly `Composer`, `ChatHeader`; no other blur API exported | `FrostPolicyTest` (B-KIT-018); Konsist bans `blur(`/`RenderEffect` outside `AndroidFrost.kt`. These are **switch-release** rules: the owner-gated Glass trial after the switch (§18.1) adds one allowlisted file behind an owner flag |
| Android-native feel (R4) | Ripple, stretch, haptics and predictive back come from AndroidX defaults (§4.9, §5.6); `LocalOverscrollFactory` is never replaced | Konsist bans `LocalOverscrollFactory provides`, `ripple(` outside `KitPress.kt` |
| Micro-motion 150–250 ms, reduced motion (R5) | `MotionTokens` and `MotionPolicy`; every part's KDoc `Motion:` line | `MotionPolicyTest`, `ReducedMotionSettlesTest` (B-KIT-023, B-KIT-054) |
| Hard max widths (R6) | `Modifier.kitMaxWidth(role)` inside every part; capacity JSON | Capacity gate (§8) |
| Frame text 1.3×, reading text full (R7) | `KitFrameText` scope inside frame parts; root cap 2.5× | `TextScaleCapTest` (B-KIT-052, B-KIT-059) |
| Simple and natural, plain words, no raw errors (R8) | Kit APIs take `Failure` and `Redacted`, never a raw error `String` (§7 notes on `KitStateView` / `KitNotice`) | API shape plus `:tools:strings` glossary checks (B-L10N-021…023) |
| Obvious options, no new levels, name the target, steer not queue (R9) | `KitScreen` has fixed slots and no "extra bar" slot; `KitTopBar` limits icons; copy rules in `:tools:strings` | `KitScreenAudit` (debug); glossary checks |
| Flat lists (R10) | `StyleTokens.flatLists = true` for Solid, Paper, Signal | Style resolver tests (B-KIT-037, B-KIT-048) |
| Teach what is hidden (R11) | `KitSwipeAction` requires a `menuTwin`; long-press parts take an optional `tip: TipId`; registry in `:engine:settings` | B-L10N-020; manifest gate checks for a `Hidden:` KDoc line |
| Arabic RTL (R12) | Directional APIs only; painters branch on `layoutDirection`; 12 auto-mirrored glyphs | `RtlRuleTest` (Konsist), RTL screenshots (B-KIT-001) |
| Screen standard (R13) | `KitScreenAudit` plus semantics checks on screen previews (§15.5) | `ScreenStandardCheck` |
| Styles (R14) | `Style` enum of exactly 5 at the switch release (the post-switch Glass trial, §18.1, may add a sixth only if the owner keeps it); resolver; contrast gate across styles × knobs × packs × brightness | B-KIT-037…048 |
| Judge visuals hard, one screen per image (R15) | Catalog renders one full screen per image; Solid pixel parity check against Flutter (§15.6) | Owner and auditor review |

---

## 3. Modules, packages and files

### 3.1 Modules (from §01 §5.2, restated with kit detail)

| Module | Plugin | Kotlin packages | Owner | Starts |
|---|---|---|---|---|
| `:ui:design` | `oc.kmp.library` (no Compose) | `io.github.eslamasabry.oc.ui.design.{color,style,type,tokens,motion,layout,text,effects}` | S kit owner | slice 0 (WP K0; tokens frozen at the end of slice 0) |
| `:ui:words` | `oc.kmp.compose` (resources only) | `…oc.ui.words` + generated `…oc.ui.words.res.Res` | S l10n owner; feature files F | slice 0 (WP W0 spike: 2–3 feature files, en/ar), full in slice 1 |
| `:ui:kit` | `oc.kmp.compose` | `…oc.ui.kit.{theme,platform,foundation,motion,frame,nav,row,input,choice,modal,state,status,appearance,teaching,widget}` | S kit owner | slice 0 (WP K0: the P7 parts and the 20 frozen signatures) |
| `:ui:kit-chat` | `oc.kmp.compose` | `…oc.ui.kit.chat.{composer,transcript,markdown,request,agent}` | S chat-kit owner | slice 2 |
| `:ui:kit-content` | `oc.kmp.compose` | `…oc.ui.kit.content.{code,diff,log,viewer,image,qr,terminal,web,data}` (later `team`) | S content-kit owner | slice 3 |
| `:ui:testing` | `oc.kmp.compose` (test only) | `…oc.ui.testing` | S kit owner | slice 0 (WP K0: the P7 harness) |
| `:desktopApp` | `oc.desktop.application` | `…oc.desktop.gallery` | S kit owner | slice 0 |
| `:tools:strings` | `oc.jvm.library` (CLI) | `…oc.tools.strings` | S l10n owner | slice 0 (W0 spike), full in slice 1 |
| `:android:os-strings` | `android.library` (resources only, no code) | none (namespace `io.github.eslamasabry.oc.android.osstrings`) | S l10n owner | slice 0 (W0: en/ar), slice 1 (all 7) |
| `tools/glyphs/` (not a Gradle module) | Python 3 script, run once; its output is committed | none | S kit owner | slice 0 |

These starts differ from §01 §5.2, which puts `:ui:design`, `:ui:kit`, `:ui:testing` and `:ui:words` at slice 1 and has
no `:android:os-strings`. Slice 0's P6 and P7 proofs and the end-of-slice-0 freeze of `KitPlatform` and 20 signatures
need them, so §22 raises the change request; the slice-0 count grows from about 12 modules to about 17.

Module namespaces drop the hyphen: `io.github.eslamasabry.oc.ui.kitchat` and `io.github.eslamasabry.oc.ui.kitcontent`.
The `build-logic` namespace function must apply `replace("-", "")`, because the §01 sketch's
`path.replace(':', '.')` would produce an invalid `…ui.kit-chat`. Kotlin packages are independent of namespaces
(§01 §5.2), so the chat kit's package can be `…oc.ui.kit.chat`.

### 3.2 File layout (create exactly this; one public part per file, the file named after the part)

```
ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/
  color/Argb.kt                 value class Argb(val v: Int); hex helpers; lerp; withAlpha
  color/Contrast.kt             wcagContrast(), relativeLuminance(), deltaE2000() (Sharma–Wu–Dalal)
  color/Roles.kt                data class Roles (24 roles) + derived getters
  color/RoleDerivation.kt       deriveRoles(), readableOn(), guardAccent(), withAccent(), withPartner(), saver()
  color/Packs.kt                enum PackId (31), Pack, PackPalette, hand-spelled packs (Graphite, Catppuccin, Gruvbox, Solarized)
  color/PackRecipe.kt           generate(sourceColours) — the one recipe for the 26 generated packs
  color/PacksGenerated.kt       the 26 packs as data (four source colours per brightness)
  color/PacksSignal.kt          Signal's fixed colours (one of the two red exemptions)
  color/MarkupInk.kt            the 4 picture-pen inks (the other red exemption)
  color/Accents.kt              AccentSwatch list (7) + Graphite accents (4)
  color/Dynamic.kt              DynamicSource (ARGB tones from the platform) → Pack
  color/StatusTone.kt           StatusTone {Neutral, Progress, Ok, Attention, Failure} → tone + glyph id
  style/Style.kt                enum Style {Solid, Soft, Brutal, Paper, Signal}
  style/Knobs.kt                Corners, Depth, Density, Edges, Knobs (nullable = style default)
  style/StyleTokens.kt          StyleTokens, StyleType, LivePolicy, StyleGlow, NeedsYouLook
  style/SurfaceRequest.kt       SurfaceRole, SurfaceState, SurfaceRequest
  style/SurfaceDecoration.kt    fill/edge/shadows/innerEdge/faceShift as pure data
  style/StyleSpec.kt            interface StyleSpec { tokens; resolve(); onColor(); resolveFilledLayer(); adjustRoles() }
  style/{Solid,Soft,Brutal,Paper,Signal}Spec.kt
  style/StyleResolver.kt        styleSpec(style), effectiveKnobs(), highContrast/Saver overrides
  type/TypeRole.kt              enum TypeRole (10 roles), enum TextTone (7)
  type/TypeScale.kt             TypeSpec(sizeSp, lineHeightSp, weight, trackingSp) per role; firm table; per-style tweaks
  type/SentenceCase.kt
  tokens/Space.kt               space1..space6
  tokens/Tokens.kt              data class Tokens (every size of UI-KIT-04) + compact variant
  tokens/Shape.kt               enum KitShapeId {Square, Tile, Code, Button, Panel, Card, Dialog, Sheet, Pill, Circle} → radius
  tokens/Elevation.kt           ShadowSpec; Solid dark = none
  motion/MotionTokens.kt        durations (ms), curve ids, spring params
  motion/MotionPolicy.kt        MotionLevel {Full, Calm, Off}, MotionSignals, resolve()
  motion/Haptic.kt              enum KitHaptic {Send, Done, Commit, LongPress, Select, ToggleOn, ToggleOff} (§03 §12.2)
  layout/WindowClass.kt         WindowClass {Compact, Medium, Expanded, Large}, short height rule
  layout/Layout.kt              every width constant (reading 720 …) — the only width literals in the codebase
  layout/CapRole.kt             enum CapRole (13 capped + Fill, Wrapper, Scope) → cap dp + alignment
  text/Bidi.kt                  isolateLtr(), isolateAuto(), firstStrong() (U+2066–2069)
  text/TextScalePolicy.kt       AppTextSize {Smallest .85, Small .92, Normal 1.0, Large 1.15}; ROOT_CAP 2.5; FRAME_CAP 1.3; caps table
  effects/Appearance.kt         data class KitAppearance (everything KitTheme needs)
  effects/FrostPolicy.kt        FrostRole, FrostStrength, FrostHold, frostSpec(), blurRadiusPx(sigmaDp, density)
  effects/LivePolicy.kt         live-look caps per style (kept for D11)
ui/design/src/commonTest/…      one test class per file above (§17)

ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/
  theme/KitTheme.kt  KitColors.kt  KitTypography.kt  KitFonts.kt  KitLocals.kt  MaterialBridge.kt (internal)
  platform/KitPlatform.kt  KitContentSlots.kt  ExternalLinkUriHandler.kt  KitSystemSignals.kt
  foundation/KitText.kt  KitWordText.kt  KitSelectable.kt  KitLtr.kt  KitIcon.kt  KitGlyphs.kt (generated)  KitMarks.kt (generated)
  foundation/KitSurface.kt  KitPanel.kt  KitFloatingSurface.kt  KitDivider.kt  KitTappable.kt  KitPress.kt
  foundation/KitMaxWidth.kt  KitFrameText.kt  KitTextScale.kt  KitInsets.kt  KitReach.kt  KitLeadColumn.kt  KitOptical.kt
  foundation/KitCopy.kt  KitTime.kt  KitLinkScope.kt  KitTypingGuard.kt  KitSemantics.kt (test-visible semantics keys)  KitAssert.kt (internal)
  foundation/KitFrost.kt        KitFrostState, Modifier.kitFrostSource(state), Modifier.kitFrost(role, state)
  motion/KitMotion.kt  KitMotionScheme.kt  KitHaptics.kt  KitSwap.kt  KitSpin.kt  KitReveal.kt  KitEntrance.kt  KitPresence.kt  KitAnimatedRows.kt  KitAnimatedValue.kt  KitDim.kt  KitRefresh.kt  KitTransitions.kt
  frame/KitScreen.kt  KitScreenAudit.kt  KitTopBar*.kt  KitPageHeading.kt  KitSystemBars.kt  KitScrollArea.kt  KitJumpPill.kt  KitScrollFade.kt  KitArrival.kt
  nav/KitNavRail.kt  KitSidebar.kt  KitNavDestination.kt  KitTabStrip.kt  KitTabSwitcher.kt  KitSessionTabs.kt  KitBreadcrumb.kt
  row/… input/… choice/… modal/… state/… status/… appearance/… teaching/… widget/…   (one file per part, §7)
ui/kit/src/commonMain/composeResources/font/  geist_variable.ttf  geist_italic_variable.ttf  geist_mono_variable.ttf  geist_mono_italic_variable.ttf  OFL.txt
ui/kit/src/androidMain/kotlin/…/kit/platform/AndroidKitPlatform.kt  AndroidFrost.kt  AndroidSystemSignals.kt  AndroidFormats.kt
ui/kit/src/desktopMain/kotlin/…/kit/platform/DesktopKitPlatform.kt  DesktopScrollbar.kt
ui/kit/src/commonTest/…          logic and desktop compose tests
ui/kit/src/androidHostTest/…     Robolectric + Roborazzi (capacity gate, preview captures)
ui/kit/src/screenshots/          goldens (recorded on the VPS only)
ui/kit/capacity/kit_capacity.json  kit_capacity_allowlist.json  kit_manifest_allowlist.json  literal_allowlist.txt
ui/kit/capacity/kit_renames.tsv  attention_allowlist.txt   (§7.0, §2)
ui/kit/api/<Part>.md             the 111 frozen specs, ported (§16.3)
ui/kit/KIT.md                    the part index (replaces the kit.dart doc table)
ui/kit/JETPACK_AHEAD.md          §01 §7.6 ledger
```

`:ui:kit-chat` and `:ui:kit-content` follow the same pattern. Each has its own `capacity/` folder, `api/` folder and
`src/screenshots/`.

---

## 4. Tokens (`:ui:design`, pure Kotlin, no Compose)

Rule 0: **a value lives in exactly one place.** Kit parts read tokens. Screens read nothing but kit parts and
`KitSpace` spacing. A Flutter number below is the spec. When this file and the Dart source differ, **the Dart source
at `int/freeze-2026-11-01` (`fc5c1e78f`, worktree `wt/oc_app-freeze`) wins**, and the agent records the difference in
the PR. That is the map's precedence rule: code beats docs. If the freeze branch moves, the coordinator re-records its
SHA here at M0. Batch1 (`65e3f70a3`) is an older snapshot: a part missing there (for example `KitChrome`) is not
missing from the app.

### 4.1 Colour roles

```kotlin
// ui/design/color/Roles.kt
@JvmInline value class Argb(val v: Int)            // 0xAARRGGBB
fun argb(hex: Long) = Argb(hex.toInt())

data class Roles(
    val dark: Boolean,
    val ground: Argb, val surface1: Argb, val surface2: Argb, val surface3: Argb,
    val hairline: Argb,                               // translucent; never re-alpha'd
    val text1: Argb, val text2: Argb, val text3: Argb,
    val accent: Argb, val onAccent: Argb,
    val partner: Argb, val onPartner: Argb,
    val attention: Argb, val attentionFill: Argb, val onAttentionFill: Argb,
    val attentionSurface: Argb, val attentionLine: Argb,
    val success: Argb, val scrim: Argb,
    val codeKeyword: Argb, val codeString: Argb, val codeType: Argb,
    val elevationShadow: Argb,                        // black 30 % in both brightnesses
) {
    val codeAdded get() = success
    val codeRemoved get() = text2
    val codeAddedSurface get() = success.withAlpha(0.10f)
    val codeRemovedSurface get() = text2.withAlpha(0.10f)
}
```

There is **no `danger` or `error` role**, and adding one fails `NoRedScanTest`.

**Graphite** (default pack, stored as `opencode`) values, copied from `lib/ui/theme_roles.dart`:

| Role | Dark | Light |
|---|---|---|
| ground | `#0B0C0E` | `#F3F3F1` |
| surface1 | `#141518` | `#FAFAF8` |
| surface2 | `#1B1C20` | `#FFFFFF` |
| surface3 | `#26282D` | `#E9E9E6` |
| hairline | `0x12FFFFFF` | `0x14000000` |
| text1 / text2 / text3 | `#F3F3F1` / `#A3A5AB` / `#8C8F96` | `#111214` / `#50535A` / `#62656C` |
| accent / onAccent | `#3DDC8A` / `#03140B` | `#087F43` / `#FFFFFF` |
| partner / onPartner | `#C7A6FF` / `#111214` | `#6D4AFF` / `#FFFFFF` |
| attention | `#FFB547` | `#9A5700` |
| attentionFill / onAttentionFill | `#FFB547` / `#1A1000` | same |
| attentionSurface / attentionLine | amber 9 % / 30 % | `rgba(255,166,0,.12)` / `rgba(190,110,0,.30)` |
| success | `#3DDC8A` | `#087F43` |
| scrim | black 58 % | black 58 % |
| codeKeyword / codeString / codeType | `#C7A6FF` / `#FFB88A` / `#7FD1FF` | `#6D4AFF` / `#B4480B` / `#0B6BA8` |

The demo's palette (`kmp-demo/.../Theme.kt`: surface1 `#1B1C20`, a `danger` red) was sampled from screenshots and
is **wrong**. Do not copy it.

**Contrast floors** (applied by `deriveRoles` and checked for all 31 packs × 2 brightnesses × 5 styles):

| What | Floor |
|---|---|
| text1 on ground and on every surface | 7:1 |
| text2, text3 on ground and every surface they sit on (incl. text3 on surface3 = disabled button) | 4.5:1 |
| accent on ground and surface1 (links are text) | 4.5:1; onAccent on accent 4.5:1 |
| attention on ground | 4.5:1 |
| icons, field edges at rest, off-switch boundaries | 3:1 (WCAG 1.4.11) |
| accent vs attention (`accentKeepsMeaning`, LOOK-39) | ≥ 30° hue and ΔE2000 ≥ 20, dark and light |

A colour is moved only as far as needed toward the text colour. Port `deriveRoles`, `readableOn`, `_guardAccent`,
`withAccent`, `withPartner` and `ThemeRoles.saver` line by line. Port the Sharma–Wu–Dalal ΔE2000 reference pairs as
test data.

**Saver** = the dark roles on `#000000` with surfaces moved one third toward black; the light roles are returned as
they are.

### 4.2 Packs, accents, Material You

- `enum class PackId` has 31 values with the **same names** as Dart `ThemePackId` (`opencode`, `catppuccin`,
  `gruvbox`, `solarized`, `dracula`, `nord`, `tokyoNight`, `oneDark`, `monokai`, `rosePine`, `everforest`, `kanagawa`,
  `ayu`, `nightOwl`, `github`, `palenight`, `synthwave`, `cobalt`, `midnight`, `paper`, `forest`, `ocean`, `sunset`,
  `sakura`, `lavender`, `mint`, `coffee`, `slate`, `amber`, `highContrast`, `dynamic`).
  - Stored by `.name` in `flutter.oc.themePack`. An unknown name reads Graphite.
  - Kotlin enum constant names may be upper case, but `storageName` must equal the Dart name. A test round-trips
    all 31.
- The 4 hand-spelled packs carry goldens. The 26 generated packs are data (four source colours per brightness: background,
  text, primary, secondary, optional success) run through `PackRecipe.generate`, so one test holds all 26 to the same floor.
- Picker order: "most used" first (Graphite, Nord, Catppuccin, Dracula, Tokyo Night, Gruvbox, Rose Pine, Solarized),
  then "See all". Material You is listed only when `KitPlatform.dynamicSource()` is not null.
- **Material You** (`PackId.dynamic`):
  - On API 31+, `AndroidKitPlatform.dynamicSource()` builds `dynamicDarkColorScheme(context)` and
    `dynamicLightColorScheme(context)` (M3, kit `androidMain`).
  - It hands `:ui:design` the ARGB of `surfaceContainerLowest` (background), `surfaceContainerLow` (navigation) and the
    rest of the scheme, mirroring Dart `dynamicThemePack`.
  - Success is fixed at `#86D8A5` dark / `#1E7A44` light.
  - Below API 31 and on desktop it returns null, and Graphite stands in.
  - Dart derived the scheme from `CorePalette.toColorScheme`, while Android's dynamic scheme reads system tones
    directly. A few tones may differ, so the contrast guard runs on the result either way [U, owner eyeball on the
    nubia].
- **Accent swatches** (`oc.appearanceAccent`, absent = Match theme; dark/light):

  | id | dark | light |
  |---|---|---|
  | mint | `#3DDC8A` | `#087F43` |
  | teal | `#3CCFCF` | `#0D7377` |
  | blue | `#5AB0FF` | `#1F6FEB` |
  | violet | `#C7A6FF` | `#6D4AFF` |
  | pink | `#F2A7E6` | `#AD2E99` |
  | mono | `#F1F2F4` | `#262B36` |
  | silver | `#B9BDC6` | `#5A606C` |

  - The accent drives `partner`. `accent` stays the pack's own colour.
  - `withPartner` guards it again.
  - Graphite also offers green, blue, teal and violet for its main accent (`graphiteAccents`).
- **Appearance mode** `{System, Light, Dark, Saver}` is stored as `oc.appearance`. The default is **Dark**. Saver
  turns motion off, live off and Style to Solid, uses true black, and leaves the stored choices untouched (B-KIT-008).

### 4.3 Styles and Fine-tune

```kotlin
// ui/design/style/StyleSpec.kt
enum class Style(val storageName: String) { Solid("solid"), Soft("soft"), Brutal("brutal"), Paper("paper"), Signal("signal") }
enum class Corners { Round, Soft, Sharp }; enum class Depth { Flat, Raised }
enum class Density { Comfortable, Compact }; enum class Edges { Hairline, None }
data class Knobs(val corners: Corners? = null, val depth: Depth? = null, val density: Density? = null, val edges: Edges? = null)

enum class SurfaceRole { Content, Control, Track, Floating, Sheet, Ground, Field, Code, Divider, StateLayer, Toggle }
data class SurfaceState(val chosen: Boolean = false, val pressed: Boolean = false, val disabled: Boolean = false,
                        val focused: Boolean = false, val nested: Boolean = false)
data class SurfaceRequest(val role: SurfaceRole, val shape: KitShapeId, val state: SurfaceState, val level: SurfaceLevel)

data class Shadow(val dxDp: Float, val dyDp: Float, val blurDp: Float, val color: Argb, val inset: Boolean = false)
data class SurfaceDecoration(
    val fill: Argb?, val edgeWidthDp: Float, val edge: Argb?, val shadows: List<Shadow>,
    val innerEdge: Argb?, val faceShiftDp: Float, val radiusDp: Float,
)

interface StyleSpec {
    val tokens: StyleTokens
    fun resolve(r: SurfaceRequest, roles: Roles, knobs: Knobs): SurfaceDecoration
    fun onColor(r: SurfaceRequest, roles: Roles): Argb                 // words on a chosen/filled surface
    fun resolveFilledLayer(r: SurfaceRequest, roles: Roles): SurfaceDecoration?
    fun adjustRoles(roles: Roles): Roles  // may change only ground, surface1-3, hairline, scrim, elevationShadow; accent guard re-runs after
}
```

| Style | Defaults (corners, depth, density, edges) | Identity (port the Dart spec file verbatim) |
|---|---|---|
| Solid (default) | Round, Flat, Comfortable, Hairline | Matte. At defaults, `resolve` returns each part's own decoration, so Solid is pixel-identical. `flatLists = true`; keeps nested surfaces. Dark has no floating shadow (`6b7e6b09a`). |
| Soft | Round, **Raised**, Comfortable, **None** | Surfaces take the ground colour and are shaped by paired soft shadows (raised at rest, pressed in when chosen). `dependsOnDepth`. Depth Flat turns Edges on. Live look at most Subtle. Firm type. |
| Brutal | **Soft** corners, Raised | 2 dp ink edge; hard offset shadow with no blur toward the bottom-end: 4 content / 3 control / 6 floating, 0 when chosen, pressed or flat, **mirrored in RTL**. Cream paper ground in light. Display weight 800; mono labels; `flattensNested`; divider width 2; glow = hard 2 dp ring; keeps itself under high contrast. |
| Paper | **Sharp** (radius 0) | No shadows, no blur. Rows sit on the page between fine ink rules; only actionable things are boxed. Page title 24; display weight 750; mono labels. Choosing Paper holds the live look off (`liveHeldByStyle`); glow = a thin rule. |
| Signal | Sharp (radius 4) | The track down the start edge, lamps, segment timer, raised hardware keys, printed slip and engraved labels (§7, UI-KIT-16). The only red outside the markup pen, in `PacksSignal.kt`. |

Knob maths (B-KIT-038):

- Soft corners keep about 60 % of the radius. Sharp uses the style's `sharpRadius` (Paper 0, Signal 4, default 2).
  Stadiums and circles never change.
- Compact density: rows 48/56, button 48, section gap 16, label gap 6. **Touch targets never shrink.** (56 = Dart
  `rowHeightTwoLine - space2`; the code wins, P7 D3.)
- Platform high contrast (§5.7) forces depth Flat and edges Hairline. It turns Soft into Solid, but Brutal, Paper and
  Signal keep themselves (`keepsUnderHighContrast`).
- **Saver** makes the *effective* Style Solid **and the effective knobs none** (Dart `KitEffects.underSaver`:
  `fineTune.isNone`, `surfaceStyle == solid`). The stored Style and knobs stay untouched and return when Saver ends.
- A style change resets the knobs. That is a *write* rule, owned by `:engine:settings` (§5.2).
- The UI exposes only **Corners** and **Spacing** (owner 2026-10-22). Depth and Edges stay in the model (hooks),
  because "each style keeps its own depth and edges".
- **Read rules.** The render path reads only `oc.effectsCorners` and `oc.effectsDensity` for Fine-tune, and only the
  glow on/off and glow style. The stored `oc.effectsDepth`, `oc.effectsEdges`, `oc.effectsGlowColours` and
  `oc.effectsGlowSpeed` are **never read** (Flutter writes them but never reads them; §05 marks them I). `Knobs.depth`
  and `Knobs.edges` are therefore always the style default at the switch.
- `SaverTest` pins "Saver → Solid, knobs none, stored values untouched"; `AppearanceMappingTest` pins that the four
  dead keys never reach `KitAppearance`.

### 4.4 Type scale

Fonts: `AppSans` = Geist variable, `AppMono` = Geist Mono variable, both SIL OFL. Copy the four TTFs from
`assets/fonts/geist/` byte for byte and record their SHA-256 in `ui/kit/composeResources/font/PROVENANCE.md`.

| Role (`TypeRole`) | Size / line height (sp) | Weight | Tracking (sp) | Family |
|---|---|---|---|---|
| LargeTitle | 28 / 34 | 500 | −0.28 | sans |
| Title | 22 / 28 | 500 | 0 | sans |
| Headline | 16 / 22 | 500 | −0.17 | sans |
| Body | 16 / 24 | 400 | 0 | sans |
| RowTitle | 16 / 22 | 500 | 0 | sans |
| Secondary | 13 / 18 | 400 | 0 | sans |
| Label | 13 / 18 | 500 | 0 | sans |
| Caption | 13 / 18 | 500 | 0 | sans |
| Button | 16 / 20 | 500 | 0 | sans |
| Mono | 13 / 19 | 400 | 0 | mono |

- These are the **code** values after the 2026-10-30 "unslop" pass. Docs that say 32/24/17 and weight 650 are stale
  (map precedence warning).
- **Firm type** (Soft, Brutal, Paper, Signal; `StyleType.firmWeights`): titles 650; headline, label, caption and
  button 600; secondary 500. Brutal display 800. Paper display 750 with page title 24. Signal display 700 with page
  title 22 and engraved capital labels (Arabic: no capitals, tracking 0).
- Integer sizes only (LOOK-13). Tabular figures through `KitText(tabular = true)` → `fontFeatureSettings = "tnum"`.
  Sentence case only (LOOK-15).
- **Tones** (`TextTone`): Primary(text1), Secondary(text2), Tertiary(text3), Accent, OnAccent, Attention, Success.
  Always opaque (LOOK-14); disabled text uses text3.
- `KitTypography` (kit) builds one `TextStyle` per role and caches it per (style, locale, density). Every role sets
  `lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None)` and
  `platformStyle = PlatformTextStyle(includeFontPadding = false)`. Proportional is Flutter's default leading
  distribution, and the app sets no theme-wide `TextHeightBehavior`, so glyphs sit where they sit today.
  `Alignment.Center` (Flutter's `TextLeadingDistribution.even`) is used **only** inside `KitField` and
  `KitSearchField`, the two Dart parts that set `even` (`kit_field.dart:817`, `kit_search_field.dart:319`).

`KitFonts.kt`:

```kotlin
@Composable internal fun rememberKitFonts(arabic: Boolean): KitFontFamilies {
    val sansWeights = intArrayOf(400, 500, 600, 650, 700, 750, 800)
    val monoWeights = intArrayOf(400, 500, 600)
    val sans = if (arabic) FontFamily.SansSerif else FontFamily(
        sansWeights.flatMap { w -> listOf(
            Font(Res.font.geist_variable, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w))),
            Font(Res.font.geist_italic_variable, FontWeight(w), FontStyle.Italic, FontVariation.Settings(FontVariation.weight(w))),
        ) })
    val mono = FontFamily(monoWeights.flatMap { w -> listOf(
        Font(Res.font.geist_mono_variable, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w))),
        Font(Res.font.geist_mono_italic_variable, FontWeight(w), FontStyle.Italic, FontVariation.Settings(FontVariation.weight(w))),
    ) })
    return remember(arabic, sans, mono) { KitFontFamilies(sans, mono) }
}
```

- Arabic rule (Dart `AppTheme.forLocale`): sans = system sans, letter spacing 0 on every role. On Android the system
  falls back to Noto Naskh/Sans Arabic.
- Latin glyphs missing from Geist (none expected) fall back per glyph through Android's font stack.
- Desktop (later): bundle Noto Sans Arabic in `:desktopApp`, because fontconfig coverage varies [U].
- **Arabic line-height check** (P7): body and row title with diacritics (`مُحَمَّدٌ`, `إِنْ شَاءَ ٱللَّٰهُ`) must not
  clip in Roborazzi. If they clip, the Arabic roles get line height +2 sp, and only the Arabic roles.

### 4.5 Spacing and sizes

`Space`: `space1..space6 = 4, 8, 12, 16, 20, 24` dp. Screens use only these, through `KitSpace.s1…s6`.

`Tokens` (dp unless noted; Comfortable / Compact):

| Token | Value | Token | Value |
|---|---|---|---|
| gutter | 16 | sectionGap | 24 / 16 |
| labelGap | 8 / 6 | panelPadding | 16 |
| rowHeightOne / Two | 56 / 64 (48 / 56) | choiceRowMin | 56 |
| buttonHeight | 52 / 48 | minTarget | 48 (never smaller) |
| chipHeight | 32 (48 hit area) | iconTile | 28, radius 9 |
| smallIcon | 20 | navHeight | 60, radius 22 |
| leadColumn | 40 (text rail 56; conversation compact 48) | statusLineMin | 52 |
| handle | 36 × 5 in a 22 band | loadingBar | 2 |
| progressBar | 4, radius 2 | skeletonRow | 64 |
| confirmTile | 44, icon 22, tint α .16, radius 12 | requestTile | 36, radius 9 |
| needsYouRing | 4 at α .06; start edge 4; line α .5; dot 8 | requestCardMaxShare | .45 (.60 from 2.5× text) |
| markSlot | 32, ring 10, dot 12 | avatar | tile 28 / mark 44; badge 16 ring 2 |
| qrMax | 240, quiet zone 4 modules | scannerWindow | 240, bracket 28 |
| meter | 9 bars × 6 wide, 12–20 tall | badge | 18 |
| swatchMinWidth | 112, preview 56 | graphNode | 156; column gap 24; row gap 48; max 6 lanes |
| logFoldedLines | 12 lines | terminalBubbleMaxShare | .40 |
| disabledAlpha | .38 | staleAlpha | .60 |
| duotoneWash | .20 | composerRadius | 26 (18 on wide) |
| bubbleRadius | 20, tail 6 | | |

### 4.6 Shapes

| `KitShapeId` | Radius (Round) | Used by |
|---|---|---|
| Square | 0 | dividers, code gutters |
| Tile | 9 | icon tiles, request tile |
| Code | 14 | code block, diff, log |
| Button | 14 | buttons, fields (field 14), popover 14 |
| Panel | 18 | panels, row groups (non-flat styles) |
| Card | 22 | needs-you / request card |
| Dialog | 24 | dialog panel |
| Sheet | 30 (top corners) | sheets |
| Pill | stadium | chips, tags, jump pill |
| Circle | circle | avatars, round buttons |

- Rail radius 20 (modal side rails).
- Corners knob: Soft about ×0.6; Sharp = the style's `sharpRadius`.
- Hairlines are **one physical pixel**, snapped (`1f / density` dp, drawn at integer pixel offsets). The focus ring is
  two physical pixels. Never re-alpha the hairline role.

### 4.7 Elevation

- No M3 tonal elevation: `surfaceTint = Color.Transparent`, and every wrapped M3 part's elevation is 0.
- One shadow role, `elevationShadow` (black 30 %), with at most one floating shadow `(0, 6, blur 16)`. Solid dark has
  none.
- The styles' shadows (Soft paired, Brutal hard offset) come only from `SurfaceDecoration.shadows`, drawn by
  `Modifier.kitSurface` with `drawBehind`.
  - Never `Modifier.shadow`: its platform elevation shadows ignore the style rules and cannot do inset or hard offset.
- The page never casts a shadow (B-KIT-004, B-KIT-041).

### 4.8 Motion tokens

```kotlin
// ui/design/motion/MotionTokens.kt  (ms unless noted; names = Dart KitMotion unless the comment names another source)
object MotionTokens {
    const val QUICK = 150; const val STANDARD = 250; const val ROW_RETURN = 300
    const val ROW_OPEN = 450                 // not in KitMotion: Flutter FadeForwardsPageTransitionsBuilder.kTransitionMilliseconds,
                                             // used by Dart KitAndroidPageTransitionsBuilder (kit_page_transitions.dart)
    // No EXIT token: every Flutter reverse slot used QUICK (150). A part leaving uses QUICK.
    const val ENTRANCE = 900; const val CELEBRATION = 1400; const val BREATH = 4000      // drawings and loops (D11)
    const val ESCALATE_AFTER = 8000; const val PRESS_HOLD = 100; const val UNDO_WINDOW = 8000
    const val COPIED_HOLD = 2000; const val LOG_POLL = 2000; const val TYPING_SETTLE = 300; const val TERMINAL_COPIED = 2000
    const val STOP_IGNORE_AFTER_SEND = 800   // not in KitMotion: the composer's `stopQuietAfterSend` (fix 49d067f2d)
    // step light: lap 2000, calm lap 4000, breathe 1600, flash 300, fade 200, tick 600, spring 260, sweep 2200
    // context ring: value 600, colour 320, halo 700, drain 900, spin 1200; hue fade 450
    // style cross-fade 200 on style change, 120 on state change
    const val MARK_FROM = 0.9f               // Dart `markFrom`: a mark never appears from nothing; port its use verbatim
}
```

Flutter's named curves are **not** the androidx curves of the same name. Compose's `EaseOutCubic` is
`(0.33, 1, 0.68, 1)` and `EaseInCubic` is `(0.32, 0, 0.67, 0)`; Flutter's (pinned SDK `curves.dart`) are the values
below. `KitMotion.kt` defines every curve from Flutter's control points, and Konsist bans the androidx `EaseOutCubic`,
`EaseInCubic`, `EaseInOutQuad` and `EaseOutQuad` names everywhere (§14.5).

| Dart curve | Compose easing (in `KitMotion`) | Use |
|---|---|---|
| `enter` easeOutCubic | `Enter = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)` | anything going 0 → 1, including a part leaving that way; word fade, page fades |
| `exit` easeInCubic | `Exit = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)` | reverse slot only |
| `stepEase` easeInOutQuad | `StepEase = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)` | step light |
| `stepOut` easeOutQuad | `StepOut = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)` | step light |
| `emphasized` easeInOutCubicEmphasized | `PathEasing` built from Flutter's ThreePointCubic points (0.05,0)(0.133333,0.06)(0.166666,0.4)(0.208333,0.82)(0.25,1) | page and row transforms |
| `rowReturnPath` | the same `PathEasing`, reversed | chat → row |
| `pulse` easeInOut | `CubicBezierEasing(0.42f, 0f, 0.58f, 1f)` | breathing |
| `steady` linear | `LinearEasing` | sweeps |
| `land` easeOutBack | `CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)` | drawings only (D11) |

Springs. Flutter states springs as (stiffness, damping coefficient) with mass 1. Compose takes a damping
**ratio**, ζ = c / (2√k). Converted:

| Dart spring | Compose `spring(dampingRatio, stiffness)` | Overshoot | Use |
|---|---|---|---|
| `lensLead` (560, 32) | (0.676, 560) | 5.6 % | tab lens head |
| `lensTrail` (210, 23) | (0.794, 210) | 1.7 % | tab lens tail |
| `lensDragLead` (700, 36) | (0.680, 700) | 5.4 % | tab lens head while dragged |
| `lensDragTrail` (260, 26) | (0.806, 260) | 1.4 % | tab lens tail while dragged |
| `lensLift` (260, 20) | (0.620, 260) | 8.3 % | tab lens lifting out of the bar while dragged, and settling back (`kit_nav.dart`) |
| `replyGrow` (700, 53) | (1.0, 700) | 0 | live reply box growth |
| standard spatial fast/default/slow | (0.9, 1400) / (0.9, 700) / (0.9, 300) | 0.2 % | M3 parts, sheets, container transform, drag release |
| standard effects fast/default/slow | (1.0, 3800) / (1.0, 1600) / (1.0, 800) | 0 | alpha and colour on M3 parts |

The five tab-lens springs with ζ < 0.9 exist today and the owner has felt them. They are ported **as is** for parity.
§01 §9.4's "no overshoot" governs the M3 motion scheme and all *new* motion. The owner can flatten the lens springs to
ζ 0.9 (§19 Q5). **The sent bubble has no spring:** `kit_message_lift.dart` runs a `STANDARD` (250 ms) tween on `Enter`
from scale 0.92 (§7.8).

`MotionPolicy` (pure, unit-tested; motion-native §9.1):

```kotlin
enum class MotionLevel { Full, Calm, Off }
data class MotionSignals(val systemScale: Float, val powerSave: Boolean, val severeHeat: Boolean)
fun resolveMotion(s: MotionSignals, user: MotionLevel, saver: Boolean): MotionLevel = when {
    s.systemScale == 0f || user == MotionLevel.Off || saver -> MotionLevel.Off
    s.severeHeat || s.powerSave || user == MotionLevel.Calm -> MotionLevel.Calm      // MOT-13
    else -> MotionLevel.Full
}
// reduced = level == Off; travels = level == Full; loopsIn(loops) = loops && level == Full
```

Rules carried over word for word:

- MOT-1: durations and curves come only from `KitMotion`.
- MOT-2: no blur, scale or fade-scale in transitions, except the bubble lift and the chat growing out of its row.
- MOT-5: animate paint and transforms only. No layout animation in scrolling lists, except `KitReveal` and the
  approved chat folds.
- MOT-6: a drawing's entrance plays once; at most one ambient loop per screen, and only while waiting.
- MOT-7: under Off, the next frame is final.
- MOT-11: haptic list (§4.9).
- MOT-13: battery saver or SEVERE heat means Calm.
- MOT-15: while a reply runs, exactly one thing moves.

### 4.9 Haptics

The map is §08 §6.2 (default of D8-1): today's constants, read from the Flutter embedding's
`PlatformPlugin.vibrateHapticFeedback`. `AndroidKitPlatform` plays them with `view.performHapticFeedback(constant)`.

| `KitHaptic` | Meaning (Dart `KitHaptics`) | Flutter call today | Android constant | Compose type |
|---|---|---|---|---|
| Send | the person's words leave; also a copy (Dart `KitCopy` calls `KitHaptics.send`) | `lightImpact` | `VIRTUAL_KEY` | `VirtualKey` |
| Done | a waited-for finish, once; skipped under Off | `successNotification` | `CONFIRM` on API 30+, none below | `Confirm` |
| Commit | a confirmed stop / destructive / discard | `mediumImpact` | `KEYBOARD_TAP` | `KeyboardTap` |
| LongPress | a long-press opens a menu or selection | `Feedback.forLongPress` | `LONG_PRESS` | `LongPress` |
| ToggleOn / ToggleOff | a switch flips | `selectionClick` (`CLOCK_TICK`) | `TOGGLE_ON` / `TOGGLE_OFF` on API 34+, `CLOCK_TICK` below | `ToggleOn` / `ToggleOff` |
| Select | a segment, slider step or wheel value | `selectionClick` (`CLOCK_TICK`) | `SEGMENT_TICK` on API 34+, `CLOCK_TICK` below | `SegmentTick` |

- Haptics never fire on scroll, plain taps, failures, sheets or dialogs (MOT-11). **Copy does fire `Send`**, as Dart
  `kit_copy.dart` does today (parity; §08 §6.3 is corrected in §22).
- **No in-app Vibration switch exists today.** Dart `KitEffects.haptics` defaults to true and is never stored; there is
  no Vibration row and no `oc.*` haptics key. At the switch, haptics follow only the system Touch feedback setting
  (honoured because `View.performHapticFeedback` is used), and `KitAppearance.haptics` is always true. A visible
  Vibration row is an owner question (§19 Q11); if added, it needs a new device-wide key named by the `oc.<what>` rule.
- `combinedClickable` is always given `hapticFeedbackEnabled = false`. The kit performs `LongPress` itself, so one
  path plays every haptic.
- Swipe-threshold haptic (`GestureThresholdActivate`): not on day 1 (owner question §19 Q6).

### 4.10 Status tone map

`StatusTone` → text tone + one glyph, so no state is told apart by colour alone:

| Tone | Text tone | Glyph |
|---|---|---|
| Neutral | Secondary | empty ring |
| Progress | Accent | sync arrows |
| Ok | Success | check |
| Attention | Attention | warning triangle |
| Failure | Primary | neutral error glyph, never red |

### 4.11 Icons and marks (`tools/glyphs/extract.py`, run once)

- **Source:** the three TTFs in `assets/fonts/phosphor/` (from `phosphor_flutter-2.1.0`, archive SHA-256
  `8a14f238…1fde`, `provenance.json`). Also the 196 `IconData(` code points in `lib/ui/app_iconography.dart`,
  `docs/design/iconography/glyph-sources.json`, **and the duotone second layers in `lib/ui/kit/kit_icon.dart`
  `_duotoneBackgrounds`** (`0xe17f` → `0xe17e`, `0xe25b` → `0xe25a`, `0xe0d1` → `0xe0d0`, all in the Duotone font). An extractor that reads only the first two sources misses the background layer.
- **Script:** `tools/glyphs/extract.py` (Python 3, fontTools `SVGPathPen`, run with `python3 -I`). For each code point
  and font it writes the glyph outline flipped to y-down on a 24-unit viewport. It emits
  `ui/kit/src/commonMain/kotlin/…/foundation/KitGlyphs.kt`, with one `val Back: ImageVector by lazy { … }` per glyph,
  names equal to the Dart `AppIconography` field names. Duotone emits two paths, the secondary at α .20.
- **Mirroring:** exactly the 12 Dart `matchTextDirection` glyphs get `autoMirror = true`: `back`, `externalLink`,
  `chevronRight`, `chevronDown`, `chevronUp`, `chevronLeft`, `forward`, `login`, `nested`, `reply`, `returnKey`, `undo`
  (B-KIT-001).
- **Marks:** the 79 `assets/marks/*.svg` become `KitMarks.kt`, monochrome and tinted. On Android, compose resources
  cannot load SVG [R kmp-cmp-status §6], so this is required. The brand mark
  `assets/branding/open-portal/mark.svg` becomes `KitBrandMark`. The marks need more than path data: all 79 use `<g>`
  groups, 21 use `fill-rule`/`evenodd`, one uses `transform` and one uses `clipPath`. `extract.py` handles them
  explicitly: path data through fontTools `svgLib.path.parse_path`, group and element transforms composed into
  ImageVector `group(...)` transforms, `evenodd` → `PathFillType.EvenOdd`, and `clipPath` → the group's
  `clipPathData`. `MarkParityTest` renders all 79 against a reference raster (cairosvg, run once, committed).
  **Alternative:** the Valkyrie CLI (`io.github.composegears.valkyrie` 0.5.2, §21), which already handles these SVG
  features; the kit owner picks one in K0 and records it.
- **Verification:** `GlyphProvenanceTest` re-hashes the TTFs against `provenance.json` and checks that every Dart
  name has a Kotlin glyph. `GlyphParityTest` (Roborazzi) renders 20 sample glyphs next to the same code point drawn
  from the TTF; the difference must be ≤ 0.5 % of pixels.
- **Fallback** if the outlines fail parity: ship the three TTFs as compose resources and draw glyph text, the demo's
  approach. Then `KitIcon` must clear semantics text and mirror through `graphicsLayer(scaleX = -1f)`.
- Sizes are 20, 22 or 24 only. Leading and state icons grow with text up to 1.5×, rounded to a whole physical
  pixel. Regular is the action weight; duotone only for the selected nav destination; fill only for Stop.
- New icons are found with the `agent-glyphs` skill and stay Phosphor. Brand marks stay monochrome.

---

## 5. Theming

### 5.1 The root: `KitTheme`

```kotlin
// ui/kit/theme/KitTheme.kt
@Composable
fun KitTheme(
    appearance: KitAppearance,          // :ui:design, built by :ui:shell from :engine:settings state (§5.2)
    platform: KitPlatform,              // AndroidKitPlatform / DesktopKitPlatform / FakeKitPlatform (tests)
    localeTag: String,                  // resolved app locale, e.g. "ar"
    content: @Composable () -> Unit,
)
```

`KitTheme` does the following, in order. Nothing else in the app does any of it.

1. Collects `platform.signals` with `collectAsState()`. It resolves `MotionLevel` (§4.8), high contrast (§5.7), the
   24-hour clock and fine pointer.
2. Resolves the pack (dynamic or static) and the brightness. System mode reads `isSystemInDarkTheme()`; Saver is dark.
   Then it applies the accent (`withPartner`), the style (`styleSpec(style).adjustRoles`), Saver, and the guard
   re-run. The result is a `Roles`, memoised by `remember(inputs)`.
3. Builds `KitColors` (Compose `Color`s from `Roles`, `@Immutable`), `KitTypography` (fonts + type scale + style type
   tweaks + locale), `Tokens` (knobs applied) and `StyleSpec`.
4. Provides the locals below with `CompositionLocalProvider`.
5. Wraps the content in `MaterialTheme(colorScheme, typography, shapes, motionScheme)` from `MaterialBridge` (§5.4).
   Only wrapped M3 parts read it.
6. Calls `platform.SystemBars(darkContent = !roles.dark, ground = colors.ground)`. Android sets bar icon colours
   through `WindowCompat.getInsetsController`, because the app's own dark choice is not a configuration change
   (motion-native §5).

| Local | Kind | Type | Read by |
|---|---|---|---|
| `LocalKitPlatform` | static | `KitPlatform` | kit parts only |
| `LocalKitAppearance` | static | `KitAppearance` | kit parts only |
| `LocalKitColors` | static | `KitColors` | kit parts only |
| `LocalKitTypography` | static | `KitTypography` | kit parts only |
| `LocalKitTokens` | static | `Tokens` | kit parts only |
| `LocalKitStyle` | static | `StyleSpec` + effective `Knobs` | kit parts only |
| `LocalKitMotion` | static | `KitMotionState(level, reduced, travels, stillFrame, debugTimeScale)` with derived `ambientLoops` and `progressLoopSpeed` (§08 §2.7) | kit parts only |
| `LocalKitWindow` | dynamic | `KitWindowInfo(cls, short, finePointer, widthDp, heightDp)` | kit parts; `:ui:shell` scenes |
| `LocalKitHaptics` | static | `KitHaptics` (system Touch feedback only at the switch; §4.9) | kit parts only |
| `LocalKitFrost` | static | `KitFrostState?` (hoisted by `KitScreen`; null outside a frost screen) | `kitFrostSource`, `kitFrost` |
| `LocalKitPreviewOverride` | static | `KitPreviewOverride?` (appearance, locale, text scale, width; §15.1) | `KitPreview` only |
| `LocalDensity` | static | `AppTextDensity` (in-app Text size × system, root cap 2.5×; §9) | everything |
| `LocalLayoutDirection` | static | from `localeTag` (ar → Rtl) | everything |
| `LocalIndication` | static | kit ripple | kit parts only |
| `LocalUriHandler` | static | `ExternalLinkUriHandler` (never `launchUrl`) | Markdown links, kit text links |
| `LocalTextSelectionColors` | static | handles = accent, background = accent α .30 | selection |
| `LocalKitBottomInset` | dynamic | `KitBottomInset` (dock, composer, keyboard; published by `KitScreen`) | undo bar, jump pill, tips |

`static` locals are right for theme values: they change rarely, and reading them costs no tracking. A theme change
recomposes the tree once. Surfaces animate their own change: `Modifier.kitSurface` runs `animateColorAsState` on
fill, edge and shadow colour over 200 ms (style change) or 120 ms (state change), read in the draw phase. Text colours
switch at once, as in Flutter.

### 5.2 Where appearance comes from (layering)

- `:engine:settings` owns the **stored choices and write rules**:
  - all keys in map UI-KIT-14 (`oc.appearance`, `oc.themePack`, `oc.appearanceAccent`, `oc.textSize`,
    `oc.effectsMotion` + the folded `oc.effectsCelebrations`, `oc.effectsActivityGlow`, `oc.effectsGlowStyle/Colours/Speed`,
    `oc.effectsBackgroundOn`, `oc.effectsLiveHeldByStyle`, `oc.effectsLiveStyle`, `oc.effectsLiveIntensity`,
    `oc.effectsSurfaceStyle`, `oc.effectsCorners`, `oc.effectsDensity`, `oc.glass`, `oc.appLocale`,
    `oc.appearanceGuide`, `oc.tips`);
  - "a style change resets knobs", "Paper and Signal hold the live look off", the celebration fold, and "unknown names
    read defaults" (retired `glass`/`crystal` *Style* names read as `solid`). These rows (B-KIT-002, -006, -040, -047)
    are owned and tested by `:engine:settings`.
  - **`oc.glass` is live, not dead.** It is the frost strength the owner asked for on 2026-10-30 (Dart
    `GlassChoiceStore`, `lib/state/glass_choice.dart`): values `off`, `weak`, `balanced`, `strong` stored by name; a
    missing or unknown value reads `balanced`; device-wide (no profile id, never swept). It maps to
    `KitAppearance.frost`. Do not confuse it with the retired `oc.glassLiquid*` keys or the retired Glass *Style*.
    §05 and §07 currently mark it dead; §22 raises the correction.
  - There is **no haptics key** (§4.9).
- `:ui:design` owns the **render-time meaning**: `KitAppearance`, the resolver and the policies.
- `:ui:shell` maps `AppearanceState` (engine) → `KitAppearance` (design) in `ui/shell/theme/AppearanceMapping.kt`,
  with an exhaustive `when` per enum. Engines may not see `:ui:design` (§01 §6.1), so this is the one join.
  `AppearanceMappingTest` fails if the two enums drift, or if the engine's write-time style table (live held off,
  intensity cap) disagrees with `StyleTokens.live`.

```kotlin
// ui/design/effects/Appearance.kt
data class KitAppearance(
    val mode: AppearanceMode,           // System, Light, Dark, Saver
    val pack: PackId, val accent: AccentId?,
    val textSize: AppTextSize,          // Smallest .85, Small .92, Normal 1.0, Large 1.15
    val motion: MotionLevel,            // user choice; KitTheme combines with signals
    val haptics: Boolean = true,        // always true at the switch: no stored key exists (§4.9, §19 Q11)
    val glow: GlowChoice,               // Off, Classic, SoftRing (the page offers these three)
    val style: Style, val knobs: Knobs,
    val frost: FrostStrength,           // Off, Weak, Balanced (default), Strong — from oc.glass
    val liveBackground: LiveChoice,     // kept for D11; the switch release draws plain ground
)
```

### 5.3 `KitColors`

`@Immutable data class KitColors` holds one Compose `Color` per role in §4.1, plus the code colours and derived diff
tints. It is built by `Roles.toKitColors()`. Kit parts read `LocalKitColors.current.text2`. **Features never read
colours.**

### 5.4 Material bridge (internal, for the M3 parts the kit wraps)

`MaterialBridge.kt` ports Dart `schemeFromRoles` (`lib/ui/theme_roles.dart`, the function after `ThemeRoles`)
verbatim. The table below is transcribed from that function on the freeze tip; if they ever differ, the Dart wins.
`MaterialBridgeTest` compares the bridge against values generated once from the Dart function for Graphite dark and
light (a small Dart script run in the Flutter worktree; its JSON is committed as test data), not against this table.

**Base.** Dart starts from `ColorScheme.fromSeed(seedColor: accent, brightness)`. Compose M3 has no common `fromSeed`,
so the bridge starts from `darkColorScheme()` / `lightColorScheme()` and sets every slot the Dart sets. The slots the
Dart leaves to the seed (the remaining `*Fixed*` variants) are not read by any wrapped part [U: K0 lists the slots the
wrapped `BottomSheet`, `DatePicker`, `TimePicker`, `Slider`, `PullToRefreshBox` and `TooltipBox` read]. The overrides
(`α` = alpha; "over" = `alphaBlend`):

| M3 slot | Value | M3 slot | Value |
|---|---|---|---|
| primary / onPrimary | accent / onAccent | surface / onSurface | ground / text1 |
| primaryContainer | accent α .16 (dark) / .14 (light) **over surface1** | onSurfaceVariant | text2 |
| onPrimaryContainer | text1 | surfaceDim / surfaceBright | ground / surface3 |
| primaryFixed / onPrimaryFixed | accent / onAccent | surfaceContainerLowest / Low | ground / surface1 |
| secondary / onSecondary | text2 / ground | surfaceContainer | **surface2** |
| secondaryContainer / on | surface3 / text1 | surfaceContainerHigh | dark: **mix(surface2, surface3, .5)**; light: surface2 |
| tertiary / onTertiary | attention / onAttentionFill | surfaceContainerHighest | surface3 |
| tertiaryContainer | **attentionSurface over surface1** | outline | text3 |
| onTertiaryContainer | text1 | outlineVariant | **hairline over surface1** (opaque) |
| **error / onError** | **text1 / ground** (nothing is red) | inverseSurface / inverseOnSurface | text1 / ground |
| errorContainer / on | surface3 / text1 | inversePrimary | accent |
| shadow | black | scrim | **black** (not the scrim role; M3 applies its own alpha) |
| surfaceTint | `Color.Transparent` | background / onBackground, surfaceVariant | not set by Dart; Compose: background = ground, onBackground = text1, surfaceVariant = surface1 (no wrapped part reads them) |

- Shapes: extraSmall 9, small 14, medium 18, large 22, extraLarge 30.
- `motionScheme = KitMotionScheme(off = level == Off)`: the standard numbers, with `snap()` under Off
  (motion-native §2.2).
- Typography maps bodyLarge = Body, bodyMedium = Body, bodySmall = Secondary, labelLarge = Button,
  labelMedium = Label, titleMedium = RowTitle, titleLarge = Title, headlineSmall = Title,
  displaySmall = LargeTitle. These only matter inside wrapped M3 parts.

### 5.5 Dynamic colour

See §4.2. `AndroidKitPlatform.dynamicSource()` is called once per `KitTheme` composition, keyed on `uiMode`. Below
API 31 it returns null.

### 5.6 System bars, edge-to-edge, overscroll

- `MainActivity.onCreate` calls `enableEdgeToEdge()`. That belongs to `:androidApp` (§01), but the kit depends on it.
- `KitSystemBars` (kit `androidMain`, through `KitPlatform.SystemBars`):
  - transparent status bar, with icon brightness opposite the theme;
  - navigation bar drawn in `ground` by `KitScreen`'s bottom inset;
  - `window.isNavigationBarContrastEnforced = false`, so 3-button navigation gets no grey scrim over the opaque
    composer (motion-native §5).
- Overscroll: Android defaults (stretch on API 31+, glow on 26–30). **Never** `LocalOverscrollFactory provides …`.
  Inner horizontal scrollers (code, diff, mini table) use `rememberOverscrollEffect()?.withoutVisualEffect()` when a
  double stretch shows.
- Fling: `ScrollableDefaults.flingBehavior()`, untouched.

### 5.7 High contrast

`KitSystemSignals.highContrast` is true when either:

- `Settings.Secure "high_text_contrast_enabled" == 1`, observed with a `ContentObserver`. This is the signal Flutter's
  Android engine reads for `highContrast` [U: confirm in the engine source while porting `AndroidSystemSignals`]; or
- on API 34+, `UiModeManager.getContrast() >= 0.5f`, the Android 14 contrast level.

High contrast forces Solid (except Brutal, Paper and Signal), depth Flat and edges Hairline, and holds frost solid.

### 5.8 Theme switching and the guided Appearance page

- Every appearance choice saves immediately in `:engine:settings` and returns `Outcome`. A refused save puts the
  choice back and says so (Dart `SettingsFailure(storageFailed)`).
- `KitTheme` recomposes with the new `KitAppearance`. Surfaces cross-fade over 200 ms. Under Off the change is instant.

---

## 6. The `KitPlatform` seam (only kit parts read it)

```kotlin
// ui/kit/platform/KitPlatform.kt   (commonMain)
@Stable
interface KitPlatform {
    val family: KitPlatformFamily                                  // Android, Desktop, Ios
    val signals: StateFlow<KitSystemSignals>
    fun dynamicSource(): DynamicSource?                            // Material You tones, API 31+ only
    val isDebug: Boolean                                            // from AppBuildInfo.debuggable; debug, test and preview builds = true (kitAssert, §16.2)
    @Composable fun pressIndication(ink: Color): Indication        // Android: M3 ripple(bounded) at pressedAlpha .12
    fun haptic(kind: KitHaptic)                                    // Android: view.performHapticFeedback(constant) per §4.9 / §08 §6.2 through the
                                                                    // activity's decorView (honours the system Touch feedback setting); desktop: no-op.
                                                                    // A plain function, not @Composable: as built in P7 (D5)
    @Composable fun SystemBars(darkContent: Boolean, ground: Color)
    fun frostEffect(spec: FrostSpec, density: Float): FrostEffect?  // Android API 31+: RenderEffect blur + saturation; null = draw solid (§7.2)
    // (no frameDensity member: P7 showed text sizing cannot go through a custom Density; frame text uses frameDp, §9.2)
    fun Modifier.highFrameRate(active: Boolean): Modifier          // Android: preferredFrameRate(High) [§7.6 JETPACK_AHEAD if not in CMP]
    fun formatMoment(epochMs: Long, style: MomentStyle, localeTag: String, is24h: Boolean): String  // Latin digits (nu-latn), §12.1 rule 8
    fun formatNumber(value: Double, style: NumberStyle, localeTag: String): String   // decimalPattern, currency (B-L10N-012); Latin digits
    fun clipEntry(text: String, sensitive: Boolean): ClipEntry      // Android 13+: EXTRA_IS_SENSITIVE when sensitive; text is already redacted (KitCopy)
    val links: ExternalLinkUriHandler                               // from :android:platform's opener; never launchUrl
    val content: KitContentSlots                                    // supplied by :ui:kit-content's androidMain
}

data class KitSystemSignals(
    val animatorScale: Float, val powerSave: Boolean, val severeHeat: Boolean,
    val highContrast: Boolean, val is24Hour: Boolean, val finePointer: Boolean,
    val accessibleNavigation: Boolean,   // Android: AccessibilityManager.isTouchExplorationEnabled + listener (Flutter's
                                         // MediaQuery.accessibleNavigation); KitUndoBar keeps its offer open while true
)

@Stable
interface KitContentSlots {                                         // declared in :ui:kit, implemented in :ui:kit-content
    @Composable fun Terminal(params: TerminalSlotParams, modifier: Modifier)     // Termux TerminalView via AndroidView
    @Composable fun Scanner(params: ScannerSlotParams, modifier: Modifier)       // CameraXViewfinder + zxing-cpp
    @Composable fun WebPreview(params: WebPreviewSlotParams, modifier: Modifier) // hardened WebView, one origin
}
```

Implementations:

| Class | Module / source set | Notes |
|---|---|---|
| `AndroidKitPlatform(context, links, content, buildInfo)` | `:ui:kit` `androidMain` | `animatorScale` from a `ContentObserver` on `Settings.Global.ANIMATOR_DURATION_SCALE` (live, not read once; that was the demo's bug), `ACTION_POWER_SAVE_MODE_CHANGED`, `PowerManager.addThermalStatusListener` (≥ `THERMAL_STATUS_SEVERE`), high contrast (§5.7), `DateFormat.is24HourFormat` on `ACTION_TIME_CHANGED`, `InputManager` for a connected mouse, `AccessibilityManager.addTouchExplorationStateChangeListener` |
| `AndroidKitContentSlots()` | `:ui:kit-content` `androidMain` | the only place that depends on Termux `terminal-view`, CameraX viewfinder, zxing-cpp and `android.webkit` |
| `DesktopKitPlatform()` | `:ui:kit` `desktopMain` | hover/press fill instead of ripple; haptics no-op; `finePointer = true`; frost null; slots draw "not on this computer" states (capabilities off) |
| `FakeKitPlatform(signals = …)` | `:ui:testing` | fixed signals; records haptics; records links opened; `dynamicSource()` returns a fixed palette |

`:androidApp` builds the platform object once in `MainActivity` and passes it to `App()`. `:ui:shell` passes it to
`KitTheme`. Features never see it (§01 §7.3).

### 6.1 `JETPACK_AHEAD.md` rows the kit opens on day 1 (§01 §7.6)

CMP 1.13.0-beta01 already resolves Jetpack Compose runtime, ui and foundation **1.13.0-beta01** and Material3
1.5.0-beta01 on Android [V, CMP 1.13 "what's new"], so the version column names what the pinned CMP brings.

| API | androidx artifact (as resolved by CMP 1.13.0-beta01) | Used by | Common fallback | Moves to `commonMain` when |
|---|---|---|---|---|
| `RenderEffect` blur on a recorded `GraphicsLayer` for frost | platform API 31 + Compose ui 1.13.0-beta01 | `AndroidFrost.kt` (`:ui:kit`) | solid surface | never (platform-only effect; desktop stays solid) |
| `Modifier.imeNestedScroll()` (`@ExperimentalLayoutApi`) | foundation-layout 1.13.0-beta01 | transcript (`:ui:kit-chat`), only if the nubia spike passes and a tip is registered | plain `imePadding` | CMP exports it as stable |
| `Modifier.preferredFrameRate` | ui 1.13.0-beta01 | `KitPlatform.highFrameRate` while a live block reveals | no-op | CMP exports it [U] |
| ~~`FontScaleConverterFactory` frame cap~~ | — | dropped: P7 proved the per-size `frameDp` path in common code (§9.2) | — | — |

The earlier "Compose 1.13 selection-while-streaming fix" row is dropped: that fix ships in the pinned 1.13.0-beta01.
P7 checks selection over a streaming block once; if it still breaks, `KitSelectable` disables selection on the live
block only.

---

## 7. Every Flutter kit part and its Compose part

### 7.0 Reading the table, and the renames

- **Module:** **K** `:ui:kit`, **KC** `:ui:kit-chat`, **KN** `:ui:kit-content`, **D** `:ui:design` (pure logic),
  **S** `:ui:shell`, **E** an engine (the logic moves out of the kit), **—** not ported (reason given).
- **Slice:** when the part must exist (§01 slice numbers). **0f** = signature frozen at the end of slice 0 (the 20
  parts in §18.2 plus the foundation types and internals they take).
- **Built on:** the Compose or M3 primitive underneath. "Kit draw" means foundation layout + `Modifier.kitSurface` +
  `KitText`, with no M3 component.
- Every row inherits the defaults: same states (`States:` vocabulary), same capacity contract entry, same test tags as
  the Dart `Key`s, a preview per state, `KitMaxWidth` per §8.

**Renames, merges and drops: one machine-readable map.** `ui/kit/capacity/kit_renames.tsv` has one row per Dart
name that does not survive unchanged, with the columns `dart_name`, `action` (`rename`, `merge-into`, `drop`,
`defer`), `native_name` and `reason`. `:tools:capacity-port` (§8.3) and `KitManifestTest` (§14.4) both read it. Every
Dart name in `kit_capacity.json` (221 parts on the freeze tip) and in the Dart kit manifest must either exist natively
under the same name or have a row; a name with neither fails both tools. The seed rows, from §7.1–§7.9:

| Dart name | Action | Native name | Why |
|---|---|---|---|
| `KitGlass` | rename | `KitFloatingSurface` | "Glass" is a historical name for an opaque floating surface; the owner has banned glass |
| `KitChrome` (freeze tip) | rename | `KitFrameText` (internal to frame parts) | the 1.3× frame cap (§9) |
| `showKitSheet` / `showKitFramedSheet` | rename | `KitSheet(visible, …)` / `KitSheet(framed = true)` | declarative screen state (§01 §9.4) |
| `showKitConfirm` | rename | `KitConfirm(spec: KitConfirmSpec?, …)` | same reason |
| `showKitInputDialog` / `showKitAlert` | rename | `KitInputDialog` / `KitAlert` | same reason |
| `showKitMenu` | rename | `KitMenu(expanded, anchor, …)` | same reason |
| `showKitUndo` | rename | `KitUndoBar(offer: KitUndoOffer?)` | same reason |
| the other 15 `showKitX` keys in the capacity JSON (viewer, diff, gallery, markup, request sheet, technical details, task sheet, project file sheet, terminal link sheet, choice sheet, date, time and date-time pickers, server trust, term), plus `showKitUpdateSheet` (freeze tip) | rename | `KitX(visible or spec, onDismiss, …)` | same reason |
| `KitPageRoute` / `pushKitPage` / `replaceWithKitPage` | merge-into | `AppNavigator` (S) + `KitTransitions` | Nav3 owns routes (§01 §9.4) |
| `KitStatusLineSlot`, `KitStatusScope`, `KitStatusContribution` | merge-into | `KitStatusSlot` | one slot per window, owned by the shell |
| `KitSince`, `KitSincePhase`, `KitSinceTicks` | rename | `rememberKitSince(start)` | a state holder, not a widget |
| `KitEffects`, `KitEffectsScope` | merge-into | `KitAppearance` (D) + `KitTheme` (K) | one root |
| `KitContextRegion` | rename | `Modifier.kitContextMenu(menu)` | a modifier in Compose |
| `KitInk`, `KitInkLayer`, `KitPressTracker` | merge-into | `KitPress` / `KitTappable` (internal) | ripple is the platform's |
| `KitAnimatedBox` | merge-into | `Modifier.kitSurface` | paint-phase colour animation |
| `KitScrollbar`, `KitOwnScrollbar` | merge-into | `KitScrollArea` | desktop-only thumb |
| `KitScrollBehavior` | drop | — | Compose defaults; nothing to port |
| `KitDraft` | rename | `KitDraftBinding` | the store moves to `:engine:composer` |
| `KitBottomInset`, `KitClearance` | merge-into | `KitInsets` (`LocalKitBottomInset`) | |
| `KitPace` | merge-into | `KitAnimatedValue` | |
| `KitSurfacePadding`, `KitDividerInset` | merge-into | `KitSurface`, `KitDivider` | parameters |
| `KitMaxWidth`, `KitDim`, `KitMessageLift`, `KitPageOrigin` | rename | `Modifier.kitMaxWidth`, `Modifier.kitDim`, `Modifier.kitMessageLift`, `Modifier.kitPageOrigin` | modifiers in Compose |
| `KitTokens`, `KitShape`, `KitWindow`, `KitLayout`, `KitBidi`, `AppStatusTone` | rename | `Tokens`, `KitShapeId`, `WindowClass`, `Layout`, `Bidi`, `StatusTone` | pure `:ui:design` types |
| `KitRedact` | rename | `Redactor` (`:core:security`), called by the kit (§7.1) | one redactor |
| `kitTypingInField` | rename | `KitTypingGuard` | |
| `KitTextHalo`, `KitLiveBackground` (+ looks), `KitLiveLookPreview`, `KitScene` + the 15 scenes | drop (D11) | — | not in the switch release; `KitIllustration` name reserved |
| `KitIllustration` | defer (D11) | reserved `KitIllustration` | |
| `team/*`, `KitBoardLane(s)`, `KitTaskCard`, `KitWorkGraph`, `KitFactRow`, `KitProvenance` | defer (D6) | same names, later | AI Team / FactsBook after the switch |

`K2`'s "same names" rule covers every Dart name not in the file. The kit owner extends the file when a part lands;
the coordinator reviews it like code.

### 7.1 Foundations (UI-KIT-01…13)

| Dart part(s) | Native | Mod | Slice | Built on | Notes and traps |
|---|---|---|---|---|---|
| `ThemeRoles`, `deriveRoles`, `readableOn`, `_guardAccent`, `withAccent`, `withPartner`, `ThemeRoles.saver`, `deltaE2000`, `accentKeepsMeaning` | `Roles` + `RoleDerivation` + `Contrast` | D | 0f | pure Kotlin | B-KIT-003, -009, -056, -061, -062 |
| `schemeFromRoles`, `AppTheme`, `AppTheme.shaped`, `AppTheme.forLocale` | `KitTheme`, `MaterialBridge`, `KitTypography` | K | 0f | `MaterialTheme` (wrapped parts only) | `AppTheme.maxTextScale` 2.5 → `TextScalePolicy.ROOT_CAP` |
| `ThemePackId`, `ThemePack`, `ThemePalette`, `generatedThemePack`, `dynamicThemePack`, `appearanceAccents`, `graphiteAccents` | `PackId`, `Pack`, `PackPalette`, `PackRecipe`, `Packs.dynamic`, `AccentSwatch` | D | 1 | pure | storage names unchanged |
| `MarkupInk` | `MarkupInk` | D | 5 | pure | red exemption #2 |
| `AppStatusTone` | `StatusTone` | D | 1 | pure | §4.10 |
| `KitText` (roles, `.mono`, `KitMonoCut`, selectable, `pageTitle`, `tabular`) | `KitText` | K | 0f | `BasicText` with a `TextStyle` from `KitTypography` | `KitMonoCut.Middle` keeps both ends of a path at 200 % (B-KIT-052); `kind = Frame/Reading` (§9) |
| `KitWordText` | `KitWordText` | K | 1 | `TextMeasurer`: cuts at the last whole word that fits + "…" | never mid-word, no full stop before the ellipsis (`a319bd04e`) |
| `KitSelectable`, `KitSelectMode` | `KitSelectable` | K | 1 | `SelectionContainer` + `DisableSelection` | toolbar Copy and Select all only (Flutter parity; §19 Q7). Mechanism: since Compose 1.9 smart-selection items are on by default, so `OcApplication.onCreate` sets `ComposeFoundationFlags.isSmartSelectionEnabled = false` before any Compose code loads [U: flag name and effect checked in P7 on foundation 1.13.0-beta01]; if extra items still show, the kit provides a `LocalTextToolbar` with Copy and Select all only |
| `KitLtr` | `KitLtr` | K | 1 | `CompositionLocalProvider(LocalLayoutDirection provides Ltr)` + `TextDirection.Ltr` | technical blocks |
| `KitTextHalo` | — | — | — | — | only for a bright live look (D11) |
| `KitText.sentenceCase` | `sentenceCase()` | D | 1 | pure | |
| `KitTokens`, `KitShape`, `KitSurfaceLevel` | `Tokens`, `KitShapeId`, `SurfaceLevel` (+ `LocalKitTokens`) | D/K | 0f | pure / local | |
| `KitSurface`, `KitSurfacePadding` | `KitSurface` | K | 0f | `Box` + `Modifier.kitSurface(request)` | the one box; wrapper role (cap goes to its content) |
| `KitPanel` | `KitPanel` | K | 1 | `KitSurface` | rare in Solid (flat lists) |
| `KitGlass` | `KitFloatingSurface` | K | 1 | `KitSurface(role = Floating)` | opaque surface2 + hairline + at most one shadow |
| `KitDivider`, `KitDividerInset` | `KitDivider` | K | 0f | `Canvas` line, pixel-snapped | width from the style (Brutal 2) |
| `KitSurfaceLook`, `KitSurfaceDecoration`, `kitSurfaceBox/Sliver/Material` | `StyleResolver` (D) + `Modifier.kitSurface` (K) | D/K | 1 | `drawBehind` (fill, shadows, edge) + `graphicsLayer` (face shift) | resolver hooks H1–H11 ported; 200/120 ms cross-fade |
| `KitCodeSurface` | `KitCodeSurface` | KN | 3 | `KitSurface(role = Code)` | code stays opaque in every style |
| `KitWindow`, `KitLayout` | `WindowClass`, `Layout` (D) + `LocalKitWindow` (K) | D/K | 0f | `LocalWindowInfo.containerSize` | §10 |
| `KitMaxWidth` | `Modifier.kitMaxWidth(role)` | K | 0f | custom `layout {}` | §8 |
| `KitReach`, `KitLeadColumn`, `KitOptical` | same | K | 1 | layout modifiers | one text rail per surface (`97c8fb98f`) |
| `KitBottomInset`, `KitClearance` | `KitInsets` (`LocalKitBottomInset`, `Modifier.kitPublishBottomInset`) | K | 1 | `onSizeChanged` + insets modifiers | never read `WindowInsets.ime` in composition |
| `KitMotion` | `MotionTokens` (D) + `KitMotion` specs (K) | D/K | 0f | `tween`/`spring`/`PathEasing` | §4.8 |
| `KitSwap` | `KitSwap` | K | 1 | `AnimatedContent(fadeIn/fadeOut)` keyed by content key | keeps the leaving child's state during the fade (`8bff010c9`) |
| `KitSpin` | `KitSpin` | K | 1 | `rememberInfiniteTransition` read in `graphicsLayer` | still dot under Off |
| `KitAnimatedBox` | inside `Modifier.kitSurface` | K | 1 | animated colours, draw phase | paint only |
| `KitDim` | `Modifier.kitDim(dimmed)` | K | 1 | `graphicsLayer { alpha }` | never on text; Konsist bans `kitDim` on `KitText` call sites |
| `KitAnimatedValue`, `KitPace` | `KitAnimatedValue` | K | 1 | `animateFloatAsState`, snapping on first composition | |
| `KitReveal` | `KitReveal` | K | 1 | `AnimatedVisibility(expandVertically + fadeIn)` | the **only** layout animation allowed |
| `KitEntrance` | `KitEntrance` | K | 1 | `graphicsLayer { alpha; translationY }` | rise only at Full |
| `KitAnimatedRows` | `KitAnimatedRows` | K | 1 | `Column` + keyed `AnimatedVisibility` | short lists only; long lists use `Modifier.animateItem` inside the kit list helpers |
| `KitPresence` | `KitPresence` | K | 1 | `AnimatedVisibility(fade)`, not composed while hidden | |
| `KitRefresh` | `KitRefresh` | K | 2 | M3 `PullToRefreshBox` + custom indicator drawing the portal brackets | one per screen |
| `KitAndroidPageTransitionsBuilder`, `KitPageTransitionsBuilder`, `KitPageOrigin` | `KitTransitions` (specs) + `Modifier.kitPageOrigin(key)` / `kitPageOriginTarget(key)` | K + S | 1 | Nav3 `transitionSpec`/`popTransitionSpec`/`predictivePopTransitionSpec`; `SharedTransitionLayout` + `sharedBounds` | the §01 §9.4 policy; open 450 ms, row return 300 ms; disabled during a back gesture |
| `KitHaptics` | `KitHaptic` (D) + `KitHaptics` (K) | D/K | 0f | `LocalHapticFeedback` through `KitPlatform` | §4.9 |
| `KitTappable`, `KitPressTracker`, `KitTappableRole` | `KitTappable` (+ internal `PressHold`) | K | 0f | `Modifier.combinedClickable(interactionSource, indication = kit ripple, role, onClickLabel, onLongClickLabel, hapticFeedbackEnabled = false)` **alone**: it is already focusable and hoverable, so no extra `focusable()`/`hoverable()` (that would add a second Tab/D-pad stop). Hover fill and the focus ring are drawn from the shared `interactionSource` | 48 dp hit area; pressed visible at least 100 ms; a disabled tappable carries its reason (`KitEnabled.Disabled(reason)`, §16.2); menu opened by right-click, long-press or Shift+F10 (B-KIT-051); `KitTappableTest` asserts one focus stop per tappable |
| `KitInk`, `KitInkLayer` | `KitPress` (internal) | K | 0f | `ripple(bounded = true, color = ink)` + `RippleConfiguration(rippleAlpha = pressed .12)` | under Off: flat pressed fill, same tree (no remount) |
| `AppIconography`, `AppIcons`, `AppGlyph` | `KitGlyphs` (generated) + `KitIcons` (one glyph per verb: copy, run, stop, send, queue, retry, externalLink) | K | 0f | `ImageVector` | §4.11 |
| `KitIcon` (`s/m/l`, tone, `.status`, `.mark`) | `KitIcon` | K | 0f | `Image(imageVector, colorFilter = tint)` | decorative by default (`contentDescription = null`); grows ≤ 1.5× with text |
| `KitBrandMark` | `KitBrandMark` | K | 1 | `Image(KitMarks.Portal)` | accent tint |
| `KitVectorLogo`, `KitVectorLogoLoad` | `KitVectorLogo` | K | 3 | `KitMarks` by slug; unknown slug → initials avatar | no network SVG |
| `KitBidi` | `Bidi` | D | 1 | pure | B-L10N-006 |
| `KitTime` | `KitTime` | K | 1 | `KitPlatform.formatMoment` | 12/24 h follows the phone; B-KIT-033 elapsed words live in `:ui:words` |
| `KitCopy` | `KitCopy(text, redact: Boolean = true, sensitive: Boolean = false)` | K | 1 | `Redactor.text(text)` when `redact`, then `LocalClipboard.setClipEntry(platform.clipEntry(safe, sensitive))` + a live-region "Copied" | **masks by default**, as Dart `KitCopy.copy` does (`redact = true`); the 25 Dart call sites that pass `redact: false` on purpose (code blocks, message text and the like) are ported by name and listed in `api/KitCopy.md`; plays `KitHaptic.Send` (Dart parity, §4.9); never a snackbar; the control shows a check for 2 s; `KitCopyTest`: a registered secret is masked by default |
| `KitRedact` | `Redactor` | E (`:core:security`) | 0 | pure | **the kit calls it**: `:ui:kit`, `:ui:kit-chat` and `:ui:kit-content` depend on `:core:security` (§01 §6.1 already allows it for the first two; §22 asks for `:ui:kit-content`). Code block, log panel, terminal output, diff, viewer (screen text and find), tool row, background work line, `KitField`, `KitNotice` and `KitDetailsFold` redact before they display, copy or "open full", exactly the 12 Dart kit files that call `KitRedact` on the freeze tip; G12 port = `KitRedactionGateTest` (§17) |
| `kitTypingInField` | `KitTypingGuard` | K | 2 | focus state of kit fields | bare-key shortcuts stand down |
| `KitLinkScope` | `KitLinkScope` | K | 2 | local `UriHandler` override | lets a screen claim local dev-server links first |
| `KitSystemBars` | `KitSystemBars` | K | 1 | `KitPlatform.SystemBars` | §5.6 |
| `KitScrollBehavior` | — | — | — | Compose defaults | nothing to port |
| `KitScrollArea`, `KitScrollbar`, `KitOwnScrollbar` | `KitScrollArea` | K | 2 | Android: no thumb; desktop: `VerticalScrollbar` in `desktopMain` | mouse is not a drag device, so selection works |
| `KitContextRegion` | `Modifier.kitContextMenu(menu)` | K | 2 | `pointerInput` secondary button | no-op on touch; long-press stays the touch path |

### 7.2 Appearance and teaching (UI-KIT-14…20)

| Dart part(s) | Native | Mod | Slice | Built on | Notes |
|---|---|---|---|---|---|
| `KitEffects`, `KitEffectsScope` | `KitAppearance` (D) + `KitTheme` (K) | D/K | 0f | | storage in `:engine:settings` |
| `KitSurfaceStyle`, `KitStyleSpec`, `KitStyleTokens`, `KitKnobs`, `kitStyleSpec`, `kitStyleTheme`, `KitStyleType`, `KitLivePolicy`, `KitStyleGlow`, `KitNeedsYouLook` | `Style`, `StyleSpec`, `StyleTokens`, `Knobs`, `styleSpec()`, `adjustRoles`, `StyleType`, `LivePolicy`, `StyleGlow`, `NeedsYouLook` | D | 1 (Solid) / 4 (other four) | pure | B-KIT-037…048 |
| Solid / Soft / Brutal / Paper spec files | `SolidSpec` … `PaperSpec` | D | 1 / 4 | pure | |
| Signal: `kitSignalColors`, `KitTrack`, `KitTrackPainter`, `kitPaintTrack`, `KitTrackDecoration`, `kitSignalHead`, `kitSignalRing`, `kitSignalStartTick`, `kitSignalTies`, `kitSegmentTimer`, `kitSignalKey`, `kitSignalStopKey`, `kitSignalNeedsYou`, `kitSignalSample` | `SignalSpec` (D) + `SignalPainters` (K, `DrawScope` functions) + `Modifier.kitTrack()` | D/K | 4 | `drawBehind`, mirrored by `layoutDirection` | lamps breathe only when loops are allowed; B-KIT-042 |
| `kitSignalSlip`, `kitSignalInSlip` | `KitSignalSlip` | KC | 4 | kit draw | request cards in Signal |
| `KitLiveBackground`, `KitLiveStyle`, `KitLiveClock`, `KitLivePalette`, `KitLiveMemory`, 20 looks, `KitLiveLookPreview`, `live_mock_chat` | — at the switch | — | after switch (D11) | (AGSL `RuntimeShader` API 33+ or Canvas at 6–12 fps with the shared clock and 30 s idle rest) | prefs kept; B-KIT-002/-020/-047 still tested in `:engine:settings` |
| `KitFrost`, `KitFrostHeader`, `KitFrostStrength`, `KitFrostHold`, `KitFrostRole` | `KitFrostState` + `Modifier.kitFrostSource(state)` + `Modifier.kitFrost(role, state)` + `FrostPolicy` | K/D | signatures 0f; built 2 | Flutter's `BackdropFilter` blurs whatever is behind; Compose has no backdrop, so `kitFrostSource` (on the transcript list, through `LocalKitFrost`) records its content each frame into a `rememberGraphicsLayer()` held by `KitFrostState`, and `kitFrost` draws the region of that layer under its own bounds through `KitPlatform.frostEffect` (`RenderEffect.createBlurEffect` + saturation `ColorMatrix`, API 31+); seeded 3 % noise tile; 1 px light line; solid below API 31 and when held | D10; strengths: Weak σ6 sat 1.2 tint .62 (header .72), Balanced σ10/1.4/.70 (.80), Strong σ16/1.6/.76 (.85); dark tint −10 %; held solid by Saver, Off, high contrast and non-Solid styles; B-KIT-018. **σ is Flutter's blur sigma in logical pixels**; `RenderEffect` takes a radius in physical pixels (Skia: σ ≈ 0.57735·r + 0.5), so `blurRadiusPx = (σ·density − 0.5) / 0.57735` (`FrostPolicyTest` pins it; P7 compares against the Flutter frost golden side by side). Fallback if P7 fails: solid (no Haze; §12 X-21) |
| `GlassChoiceControl` (Settings › Appearance, under the Style cards; `appearance_screen.dart:998–1009`) | `KitSegmented` with 4 values (Off, Weak, Balanced, Strong) under the Style question, label = Dart `frostGlassQuestion` | K (part) + F (screen) | 4 | `KitSegmented` | stored in `oc.glass` (§5.2); disabled with its reason under Saver, motion Off, high contrast and non-Solid styles (the same holds as `FrostPolicy`); the owner asked for this control on 2026-10-30. §07 X7 wrongly drops it (§22) |
| `KitGuide` (+ Item, Group, Question, Next) | `KitGuide` | K | 4 | kit draw + `LazyRow` chips | one decision at a time; `oc.appearanceGuide` lives in the engine |
| `KitOptionCard` | `KitOptionCard` | K | 4 | kit draw | chosen = ring + check |
| `KitAccentRow`, `KitAccentSwatch`, `KitSwatch`, `KitSwatchGrid`, `KitThemePreview` | same | K | 4 | kit draw; arrow-key focus group | B-KIT-049 |
| `KitFineTunePanel`, `KitFineTuneKnob` | same | K | 4 | `KitSegmented` | two knobs shown (Corners, Spacing); B-KIT-017 |
| `KitStepSlider`, `KitSliderStep` | `KitStepSlider` | K | 4 | M3 `Slider(steps)` + "A" glyphs | `onValueChange` required in M3 1.5 [R] |
| `KitCarousel` | `KitCarousel` | K | 4 | foundation `HorizontalPager` with `contentPadding` peek + dots | every card its own target |
| `KitTip`, `KitTipBubble` | `KitTip` | K | 2 | `Popup` anchored by `Modifier.kitTipAnchor(TipId)`; live region polite | never amber; fades after about 4 s; any touch fades it |
| `KitTour`, `KitTourStep`, `KitTour.offer` | `KitTour`, `KitTourOffer` | K | 4 | in-window overlay with a cutout scrim (`BlendMode.Clear`) | highlight glides (jumps under Off) |
| `TipController`, `teachingRegistry` | `TeachingRegistry`, `TipController` | E (`:engine:settings`) | 2 | pure | max 2 showings, one at a time, 2 min cooldown; B-L10N-020; includes the freeze tip's `whats-new` tour (`Teaching.whatsNew`; "Show me" for Updates opens Settings › This app › Updates, `fc8164b8f`) |

### 7.3 Frames and navigation (UI-KIT-21…25)

| Dart part(s) | Native | Mod | Slice | Built on | Notes |
|---|---|---|---|---|---|
| `KitScreen` (+ `twoPane`, `threePane`, `KitScreenWidth`, status slot, pinned search, loading bar, pinned bottom) | `KitScreen` | K | 0f | `Box` + `Column`; panes via `Row` (the shell's Nav3 list-detail scene handles route-level panes; `KitScreen.twoPane` is in-screen selection) | fixed slots, no extra bar slot (R9); hosts `KitSheetHost`; debug `KitScreenAudit`: ≤ 1 primary, ≤ 1 status line, ≤ 1 refresh, ≤ 1 visible Details fold |
| `KitTopBar`, `KitTopBarExit`, `KitTopBar.shell`, `KitShellControls` | same | K | 0f | kit draw (not M3 `TopAppBar`) | compact: title + ≤ 2 icons + overflow; medium ≤ 3; wide: labelled actions; wraps at 1.3×; frame text |
| `KitTopBarPill` | same | K | 2 | kit draw | "Connected" gives way to the server name; tests `>= 1.29f`, not `>= 1.3f` |
| `KitTopBarScope`, `KitTopBarLink`, `KitTopBarBranch`, `KitTopBarOrigin`, `KitTopBarCentred`, `KitTopBarTail` | same | K | 2–5 | kit draw | |
| `KitPageHeading` | same | K | 1 | `KitText(LargeTitle)` + one quiet line | |
| `KitNav`, `KitNavRail`, `KitNavDestination`, `KitNavLayout` | `KitNavRail` (80 dp) + `KitSidebar` (296 dp, up to 400 or ⅓ window with large text) + `KitNavDestination` | K (parts) + S (scenes) | 1 | kit draw | compact has no bar (owner 2026-10-20); destinations absent, never disabled; needs-you badge via `KitNeedsYou.badge` |
| `KitTabSwitcher`, `KitTabStrip`, `KitTab` | same | K | 2 | `LazyRow` + lens springs (§4.8); bodies kept alive with `SaveableStateHolder` | fade-through 250 ms; one Tab stop, arrows mirrored in RTL; B-KIT-050 |
| `KitSessionTabs`, `KitSessionTab` | same | K | 5 | `LazyRow` | shown from two sessions |
| `KitBreadcrumb` | same | K | 5 | kit draw | current crumb not tappable |
| `KitStatusLine`, `KitStatusKind`, `KitStatus` | `KitStatusLine(status: KitStatus?)` | K | 0f | kit draw | priority choice is in `:engine:attention`'s combiner; the kit draws one `KitStatus`; never a card; B-KIT-035 |
| `KitStatusLineSlot`, `KitStatusScope`, `KitStatusContribution` | `KitStatusSlot` | K + S | 1 | | the shell owns the one slot per window |
| `KitSince`, `KitSincePhase`, `KitSinceTicks` | `rememberKitSince(start)` | K | 1 | `produceState` with one recomposition at 8 s, then minute ticks | |
| `KitStatusMark`, `KitMarkState`; `KitTaskMark` | same | K | 1 | `Canvas` | spinner becomes a still dot under Off |
| `KitStatusTag`, `KitStatusTagTone` | same | K | 1 | kit draw | a word, never a control |
| `KitStatusAnswer`, `KitAnswerTone`, `KitAnswerScale` | same | K | 3 | kit draw | |
| `KitJumpPill`, `KitJumpPillLayer`, `KitJumpEdge` | same | K | 2 | kit draw; `derivedStateOf` on list state | chat threshold **480 dp** (Flutter's 480 is logical pixels, `chat_scroll.dart:125`), held as `Layout.JUMP_PILL_THRESHOLD`, compared after `toPx()`, never a raw pixel literal |
| `KitScrollFade` | same | K | 2 | `drawWithContent` + gradient mask (`BlendMode.DstIn`) | never a hard cut mid-word |
| `KitArrival`, `KitArrivalScope` | same | K | 4 | `LazyListState.animateScrollToItem` + accent wash once | stable anchor ids, never route strings (the Dart doc example `effects-vibration` names a row that does not exist; use real anchors such as the Keep running steps) |
| `KitUpdateDot` (freeze tip) | same | K | 4 | `Canvas` dot on the top-end corner of its child | amber dot on the Settings gear and the Settings rail destination while an app update waits; fades in over `QUICK` (none under Off); excluded from semantics and taps (the host's label says "Settings, update available"); an approved amber meaning (§2) |

### 7.4 Modals and overlays (UI-KIT-26…32)

All in-window, through `KitSheetHost`. `KitScreen` places one host; `:ui:shell` places one root host above
`NavDisplay` for app-level overlays (undo, safe-mode notices). The topmost overlay owns Back through
`NavigationBackHandler`, which composes after `NavDisplay` and so wins.

**Name alignment with §03:** §03's sketch writes `KitSheetHost(visible = …, onDismiss = …) { … }` for a screen's sheet.
The part API is owned here: a screen calls `KitSheet(visible, onDismissRequest, title, …) { … }`, and `KitSheetHost` is
the host that `KitScreen` and the shell place. The coordinator should align §03's sketch to this when it next edits it.

| Dart part(s) | Native | Mod | Slice | Built on | Notes |
|---|---|---|---|---|---|
| `showKitSheet`, `KitSheet`, `KitSheetStep`, `KitSheetTone`, `KitSheetHeight`, `showKitFramedSheet` | `KitSheet(visible, onDismissRequest, title, subtitle?, height, actions, dirty, draft, dismissible, framed, body)`, `KitSheetStep` | K | 0f | M3 1.5 in-window `BottomSheet` (`rememberBottomSheetState`) + kit scrim; medium: capped 640; expanded+: centred 560 panel, `Full` → 400–480 end sheet | handle; header ≤ 4 words with Close at the end; pinned `KitActionBlock`; keyboard lifts the actions; no sheet on a sheet (a second view replaces the first); the predictive back preview is M3's own; B-KIT-032. **"Closes when answered elsewhere"** (Dart `routes: RequestRoutes`): in the declarative model the screen's ViewModel observes the request, sets `visible = false` when another device or the server answers it, and shows `KitReceipt` "Answered on <server>". The kit has no parameter for it; that half of B-KIT-032 is a ViewModel test (§17) |
| `KitDraft` | `KitDraftBinding(text, onTextChange)` | K (binding) + E (`:engine:composer` store, `oc.draft.<target>.<profileId>`) | 2 | | a multiline field in a sheet must pass `draft` (G48 → manifest gate) |
| `showKitConfirm`, `KitConfirmSheet`, `KitConsequences`, `KitConsequenceMark` | `KitConfirm(spec: KitConfirmSpec?, onConfirm, onCancel)`, `KitConsequences` | K | 0f | `KitSheet` on compact; 480 dialog on wide | kinds Neutral/Stop/Destructive/Discard; confirm = strong neutral fill; typed name; Enter confirms only Neutral; debug asserts title ends "?" or "؟" and label ∉ {OK, Yes, Continue, Confirm}; B-KIT-015 |
| `showKitInputDialog`, `showKitAlert` | `KitInputDialog`, `KitAlert` | K | 1 | in-window centred panel 480 + scrim | input focused and selected on open; IME action submits; text kept across rotation (`rememberSaveable`); B-KIT-016 |
| `KitMenu`, `KitMenuItem`, `KitMenuGroup`, `showKitMenu`, `KitMenuPanel`, `kitMenuLayout` | `KitMenu(expanded, onDismiss, anchor, groups)`, `KitMenuPanel`, `MenuLayout.arrange()` | K | 1 | foundation `Popup` + kit draw, max 320 | ordering logic is pure (B-KIT-022); destructive items last after one divider |
| `showKitUndo` | `KitUndoBar(offer, onUndo, onTimeout)` | K + S (one host) | 2 | kit draw in the root overlay; reads `LocalKitBottomInset` | one at a time; 8 s, stretched by `LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(UNDO_WINDOW, containsControls = true)` (the user's accessibility timeout); persists under accessible navigation (`KitSystemSignals.accessibleNavigation` → no timeout + Dismiss, Dart `MediaQuery.accessibleNavigationOf`) |
| `showKitUpdateSheet`, `KitUpdateChoice` (freeze tip) | `KitUpdateSheet(visible, version, note, onChoice)` | K | 4 | `KitSheet` | declarative; "Version X is ready", one plain line from the release, a What's new link, Update now / Open Settings; Back chooses nothing (owner `app-updates-two-layers`) |
| `KitSwipeAction` | `KitSwipeAction(id, onAct, menuTwin: KitMenuItem)` | K | 2 | M3 `SwipeToDismissBox` (wrapped) | `menuTwin` is a required parameter (A11Y-5); no destructive tint |
| `KitDetailsFold`, `showKitTechnicalDetails` | `KitDetailsFold(details: Redacted, notes, values)`, `KitTechnicalDetails` | K | 1 | `KitReveal` | the only place raw technical details (a `Failure`'s `Redacted` details) appear, already redacted; LTR, copyable (`KitCopy`, redacting); last and collapsed |
| `KitTechnicalValue` | same | K | 1 | `KitLtr` | |
| `KitOneTimeCode` | same | K | 3 | large mono, LTR, Copy (`sensitive = true`) | GitHub device flow |
| `KitTerm`, `showKitTerm` | `KitTerm` | K | 4 | `Popup` bubble, max 320, 48 dp target | |
| `KitFloatingPanel` | same | K | 4 | fill part; dim ground; tap ground closes | widget overlay |
| `KitFloatingAction` | same | K | 3 | kit draw 56 dp | one per screen |

### 7.5 Rows, buttons and inputs (UI-KIT-33…39)

| Dart part(s) | Native | Mod | Slice | Built on | Notes |
|---|---|---|---|---|---|
| `KitRow` (+ `.menu`, `.swipe`, `.server`, `.unavailable`, `destructive`, `enabled` + reason) | `KitRow` | K | 0f | `KitTappable` + `Row` | 56/64 dp; title 1 line (wraps from 1.3×; file names break after `_`/`-`); ≤ 1 supporting line (2 from 1.3×, 3 from 2.0×); a disabled row says its reason once to TalkBack (`387bcc08e`); B-KIT-027 |
| `KitRowIcon`, `KitRowValue`, `KitChevron`, `KitRowChip`, `kitCurrentSpan`, `kitRecommendedSpan`, `kitBreakableFileName` | same (`KitSpans.current()` etc.) | K | 0f | | the trailing value goes under the title when it cannot share the line (`c9356d90c`) |
| `KitRowGroup`, `KitSliverRowGroup`, `kit_row_group_seam`, `KitGroupNote` | `KitRowGroup` (Column) + `LazyListScope.kitRowGroup { }` | K | 0f | | Solid: flat, full width, inset hairlines; other styles: panel with 18 dp corners |
| `KitSwitchRow`, `KitRisk` | `KitSwitchRow` | K | 1 | kit-drawn switch track (styles need hooks) + `toggleable(role = Switch)` | `Select` haptic; risky step unfolds inline; locked "Always included" refuses false |
| `KitExpandRow` | same | K | 1 | `KitReveal` | |
| `KitRowMenu` | same | K | 1 | `KitIconButton` + `KitMenu` | the one overflow per bar or section, never per row |
| `KitSectionLabel` | same | K | 1 | `KitText(Label)` + `heading()` semantics | |
| `KitFeedItem`, `KitFeedRunning`, `KitFeedStateTone` | same | K | 2 | `KitRow` | Chats home rows; one compact rhythm (judge-visuals-hard) |
| `KitAction`, `KitButtonRole`, `KitButton` | same | K | 0f | `KitTappable` + kit draw | primary = accent fill; secondary = surface3; tertiary = text1 words; destructive = neutral; `working` only for the tap in flight; labels wrap to 2 lines; frame text; B-KIT-011 |
| `KitActionBlock`, `KitActionStack`, `KitInset`, `kitHeroWaitingFill/Words` | same | K | 0f | `Column`/`Row` | ≤ 1 primary (assert); stacks on compact; one end-aligned row from medium; destructive tertiary forces the stack |
| `KitIconButton` (+ `.copy`, `KitIconMotion`) | same | K | 0f | `KitTappable` + `KitIcon`; tooltip via M3 `TooltipBox` on fine pointer | label required (assert); 48 dp target; B-KIT-019 |
| `KitChip`, `KitChipKind`, `KitChipTone` | same | K | 0f | kit draw 32 dp in a 48 dp target | always carries a word; frame text |
| `KitChipWrap` | same | K | 1 | `FlowRow` with run spacing that keeps targets clear | |
| `KitFilterChip`, `KitFilterChips` | same | K | 2 | `LazyRow` + `KitScrollFade` | `needsYou` is the only amber chip |
| `KitField` (+ kinds Text, Multiline, Mono, Path, Url, Number, Secret; `KitNumberFormatter`) | `KitField` | K | 0f | `BasicTextField(TextFieldState)`; Secret → `BasicSecureTextField`; Number → `InputTransformation` mapping Arabic-Indic digits and the Arabic decimal separator to ASCII | label above (never floating); helper or error line as a live region; counter from 80 % of max; secret: reveal + Paste, no suggestions, `KeyboardOptions(autoCorrectEnabled = false, keyboardType = Password)`, no `contentType` and not important for autofill, `IME_FLAG_NO_PERSONALIZED_LEARNING` via `InterceptPlatformTextInput`, **never prefilled** (`SecretFieldPresence`: `Empty` → field; `Saved` → the line "Saved · Replace" and no input until Replace → `Editing`), value LTR and bidi-isolated, every keystroke registered with `KitRedact` before the host hears it (16 SI-4, B-SEC-204); "Checking…" escalates at 8 s |
| `KitSearchField`, `KitSearchNoMatch` | same | K | 2 | `KitField` + count line | 300 ms debounce + one count announcement; shown only above 8 items (B-KIT-030) |
| `KitAskField` | same | K | 3 | `BasicTextField` | mic while empty, Send once typed; note line never amber |
| `KitStartBox` | same | KC | 2 | `BasicTextField(TextFieldState)` | new conversation box |
| `KitSegmented`, `KitSegment` | same | K | 1 | kit draw + `selectableGroup()` | 2–4 segments; stacks into choice rows when labels don't fit; B-KIT-031 |
| `KitChoiceList`, `KitChoice`, `KitChoiceOther`, `KitChoiceRow`, `KitChoiceMark`, `KitPickerRow`, `showKitChoiceSheet` | same (`KitChoiceSheet`) | K | 1 | `selectable(role = RadioButton)` / `toggleable(role = Checkbox)` | one tap = one callback; replaces dropdowns; B-KIT-014 |
| `KitChoiceTiles`, `KitChoiceMore`, `KitChoiceCards`, `KitHeroQuestion` | same | K | 2 | kit draw | |
| `KitDateTimePicker`, `showKitDatePicker/TimePicker/DateTimePicker`, `KitDateTimeRow` | `KitDateTimePicker` (in `KitSheet`), `KitDateTimeRow` | K | 4 | M3 1.5 `DatePicker` (`DisplayMode.Input` available) + `TimePicker`/`TimeInput`, coloured by the bridge (K10) | an invalid typed value shows a reason; AM/PM stays whole above the button while the keyboard is open (`e64657651`) |
| `KitWheel`, `KitWheelColumn` | same | K | 4 | `LazyColumn` + `SnapFlingBehavior` | capped text scale; `Select` haptic per value |

### 7.6 States and feedback (UI-KIT-40…43)

| Dart part(s) | Native | Mod | Slice | Built on | Notes |
|---|---|---|---|---|---|
| `KitStateView`, `.missing`, `KitStateSize` | `KitStateView(kind, title, body, actions, failure: Failure?, details: Redacted?, since, onSlow)` | K | 0f | kit draw, max 440 | **no free-text error parameter**: errors come as `Failure` and are mapped through `:ui:words`; error tone adds Copy details + Report a problem; network failures offer Try again / Switch server; B-KIT-034 |
| `KitNotice`, `.cost`, `KitErrorKind`, `KitReport`, `KitReportHook` | `KitNotice`, `KitReportHook` (set once by the shell) | K | 1 | kit draw | classification is pure (B-KIT-024) |
| `KitErrorFallback` | `KitErrorFallback` | K | 1 | kit draw on ground | used by the safe-mode and migration-failure screens; Compose has no error widget (§01 §9.6) |
| `KitServerTrust`, `showKitServerTrust` | `KitServerTrust` | K | 2 | `KitSheet` | fingerprint expanded, wrapped, LTR; closing never grants trust |
| `KitLoadingBar` | same | K | 0f | `Canvas` 2 dp | one per screen |
| `KitSkeletonRows` | same | K | 1 | kit draw, no motion, `clearAndSetSemantics {}` | |
| `KitProgress`, `KitProgressView` (`.staged`) | same | K | 1 | `Canvas` | never goes backwards within a job; B-KIT-025 |
| `KitProgressRow`, `KitProgressRing`, `KitTrailMark`, `KitWindowRow`, `KitPlanBlock` | same | K | 3–5 | `Canvas` | the window row fills with the share left |
| `KitContextRing`, `KitContextLevel`, `KitContextChart`, `KitContextBar`, `KitContextSheet` | same | KC | 3 | `Canvas` | accent below 70 %, **attention (amber) from 70 % to 95 %**, text1 from 95 %, never red (`kit_context_ring.dart`; map UI-KIT-41 "warning from 70"); a halo when 70 % or 95 % is crossed upward; tested from `test/kit/kit_context_ring_test.dart` rows. (B-KIT-058's "high context in primary text" is the AI Team `teamContextTone`, parked with D6.) |
| `KitLevelMeter` | same | KC | 5 | `Canvas` | decorative; the host says "Listening" |
| `KitReceipt`, `KitReceiptState` | same | K | 2 | kit draw | "Sent" is never "Done"; "Not confirmed yet" at 8 s |
| `KitNeedsYou`, `KitNeedsYouReason` | same | K | 1 | kit draw | the one attention marker, always with its word |
| `KitCapabilityExplainer`, `KitCapabilities`, `KitCapability`, `KitHost`, `KitEnableFlows`, `KitEnableRequest` | same | K | 3 | kit draw; registry data | `capabilities.json` copied into `ui/kit/src/commonTest/resources/`; 21 enable flows; B-KIT-013 |
| `KitChecklist`, `KitStep` | same | K | 3 | kit draw | marks come from the engine, never inferred |
| `KitLastKnown`, `KitLastKnownRow` | same | K | 2 | `KitRow` read-only | |
| `KitTranscriptExcerpt`, `KitExcerptMessage`, `KitSkeletonTranscript` | same | KC | 2 | kit draw | redacted and cut |
| `KitVitalsStrip`, `KitVital` (freeze tip) | same | K | 3 | kit draw | Linux › vitals head: at most 4 cells (`kitAssert`), equal widths; values one line (shrink to fit, never ellipsize); words one line, ellipsized; the summary wraps to 3 lines, then ellipsizes; cells are frame text (1.3×), the summary is reading text; under 340 dp or from 1.6× text, more than two cells wrap into rows of two; Reading cap; heat is never amber; a value change cross-fades (`QUICK`); not a live region |

### 7.7 Content (UI-KIT-44…49)

| Dart part(s) | Native | Mod | Slice | Built on | Notes |
|---|---|---|---|---|---|
| `KitCodeBlock`, `KitCodeKind`, `KitCodeHighlight`, `kitCommandLineStarts`, `kitCodeBreakable` | same; highlighting behind `CodeTokenizer` (SyntaxMP 0.3.0, §01 §8) | KN | 2 (plain) / 3 (highlight) | `BasicText` + `horizontalScroll` | LTR, mono, selectable, redacted; > 20,000 chars render plain; cache of 48; an unclosed fence stays plain while streaming |
| `KitDiffView`, `KitDiffFile/Line/LineKind/FileStatus/Gap/Selection`, `showKitDiff` | same (`KitDiffPage`) | KN | 5 | `LazyColumn` | unified on phone, side by side from expanded; a page, never a sheet on a sheet; lines redacted before display and copy |
| `KitLogPanel`, `KitLogLine/Level/End/Size`, `KitLogBuffer` | same; `LogBuffer` pure | KN | 3 | `LazyColumn` follow-the-tail | ring buffer of 2,000 lines (B-KIT-021); polls only while visible; lines redacted before display and copy |
| `KitViewer`, `showKitViewer`, `KitViewerContent`, `KitPdfPage` | `KitViewer` | KN | 5 | sheet, page or pane by window | PDF pages come from `:android:media`'s isolated renderer as bitmaps; screen text and find run on redacted text |
| `KitMarkdown`, `KitMarkdownBlockBuilder`, `KitMarkdownFileLinks`, `KitMarkdownImageKind`, `kitMarkdownBlankComments` | `KitMarkdown` over the `:core:markdown` block model | KC | 2 | `BasicText` per block | the chat section owns streaming (§01 §9.3); links go through `LocalUriHandler`; B-KIT rows in the CHAT area |
| `chat/kit_markdown_unfinished.dart` (freeze tip, `0932767f0`) | the unfinished-tail rule, pure, in `:core:markdown` (called by `KitMarkdown` while streaming) | KC (+ core) | 2 | pure | while a reply streams, half-written inline syntax on the **last line only** (`**bol`, `` `cod ``, `~~stri`, `[label](htt`, a cut table row) is closed or hidden before the tail block parses, so raw marks never show and the word fade never sees words change size; fences keep their own rule; a finished reply is drawn exactly (a lone trailing `*` stays) |
| `KitFindMark`, `KitStreamCaret` | same | KC | 2 | draw phase | wash .18 passive / .38 active, never a text colour |
| `KitImage`, `KitImageSource` | same | KN | 3 | Coil 3 `AsyncImage` | never a raw URL string |
| `KitAvatar` | same | K | 1 | kit draw (initials / icon); image variant takes a `Painter` from KN | never coloured by identity |
| `KitZoom`, `KitZoomController` | same | KN | 5 | Telephoto `zoomable` + visible zoom buttons | |
| `KitImageGallery`, `showKitImageGallery` | `KitImageGallery` | KN | 5 | `HorizontalPager` + Telephoto | lazy: the shown item plus neighbours |
| `KitImageMarkup`, `showKitImageMarkup` | `KitImageMarkup` | KN | 5 | `Canvas` + pointer input | four fixed `MarkupInk`s |
| `KitQr` | same | KN | 4 | qrose 1.3.1 | dark on light card in every theme; an empty payload is refused (B-KIT-026) |
| `KitScanner`, `KitScannerCamera`, `KitScannerFailure` | `KitScanner` | KN | 4 | slot → `CameraXViewfinder` + **zxing-cpp** (Google-free; owner 2026-11-01) | latches once; never stores or renders the decoded value |
| `KitTerminalView` (`.live`, `.output`, `.accessible`), `KitTerminalText` | `KitTerminalView` | KN | 3 (`.live` + `.accessible` for the sign-in terminal) / 5 | `.live` = slot → Termux `TerminalView` via `AndroidView`; `.output` = common `BasicText` with ANSI spans; `.accessible` = common, below | errors in text1 semibold, ANSI red mapped (B-XPLAT-011); `.output` redacted before display and copy; **accessible mode** (DESKTOP-WEB-CROSS-30) below |
| `TerminalKeyBar`, `TerminalBarKey` | same | KN | 5 | kit draw | sticky Ctrl/Alt; key caps capped at 1.3× |
| `KitTerminalOsc52`, `KitTerminalLinks`, `showKitTerminalLinkSheet` | same | KN | 5 | pure parsers + `KitSheet` | B-XPLAT-009, B-XPLAT-010 |
| `KitWebPreview`, `KitWebPreviewController` | same | KN | 5 | slot → hardened `WebView` | one origin; no JS bridge; storage cleared on open and close |
| `KitChart`, `KitChartWords`, `KitMiniTable`, `KitKeyValue`, `KitKeyValueRow` | same | KN | 5 | `Canvas` / `Row` | chart colours accent, success, neutral, never amber |
| `KitFactRow`, `KitProvenance` | same | KN | after switch (D6 FactsBook) | | |
| `KitWorkGraph` + node/edge/geometry | same | KN | after switch (D6) | | geometry is pure and tested |

**Terminal accessible mode (DESKTOP-WEB-CROSS-30).** A screen reader cannot read the character grid, so:

| Piece | Rule |
|---|---|
| The grid | the `AndroidView` holding `TerminalView` is wrapped in `Modifier.clearAndSetSemantics { contentDescription = "Terminal"; stateDescription = <title> }`; no grid cell reaches the semantics tree |
| The switch | a visible toolbar toggle "Accessible mode" (`terminal-accessible-mode` test tag) on every live terminal (local shell, server shell, sign-in); it turns on by itself when TalkBack or Switch Access is on (`KitPlatform.signals.screenReader`), and the person can turn it off |
| Transcript | `TerminalModel.transcript` (17 §2.6): plain text with ANSI CSI/OSC sequences stripped by `TranscriptSanitizer` (port of `_transcriptSanitizer`), capped at the newest **100,000 characters**, redacted; shown as a selectable `KitText` list (one item per line, `mergeDescendants` per line) whose last 3 lines are a polite live region, throttled to one announcement per second |
| Input | a `KitField(Mono)` labelled "Send to terminal" with a Send button; Send writes `value + "\r"` and clears the field; the key bar's Ctrl/Alt stay available |
| Sign-in terminal | in accessible mode the "Open sign-in page" and "Copy code" buttons stay above the transcript (17 §1.5) |

Tests: `TerminalAccessibleModeTest` (Robolectric: no grid nodes; stripped and capped transcript; field sends value + CR;
auto-on with a screen reader) ports the 10 `terminal_accessibility_test` behaviours in slice 3, not in W14-18.

### 7.8 Chat parts (UI-KIT-50…53), single owner of `:ui:kit-chat`

| Dart part(s) | Native | Slice | Built on | Notes |
|---|---|---|---|---|
| `KitComposer`, `KitComposerDelivery`, `KitComposerVoice`, `KitVoicePhase`, `KitComposerFailure`, `KitComposerTray`, `KitComposerTip`, edge, glow, layer, caption, classic | `KitComposer(state: KitTextState, …)` | 2 / 3 | `BasicTextField(TextFieldState)` (`typealias KitTextState = TextFieldState` + `rememberKitTextState()`, so features need no foundation text import); `contentReceiver` for keyboard images; `Modifier.navigationBarsPadding().imePadding()` | the field owns text (§01 §9.2); Send fires on lift when it has a long-press menu (`ee0815e9f`); Stop ignores taps for 800 ms after a send (`49d067f2d`); **"Stop and send" is an amber pill** (`kit_composer_caption.dart`, `attentionFill`); the hint ellipsizes at large text (`a978542f3`); glow read only in `drawBehind`; one thing moves (MOT-15); frame text (Dart `KitField.composer` inside `KitChrome`) |
| `KitComposerChips` (`.model`, `.attachments`, `.suggestions`), `KitModelChipState`, `KitAttachment`, `KitSuggestion`, `KitComposerStatusStrip` | same | 3 | `LazyRow` + `KitScrollFade` | Stop sits at the end of the chip row while running; **the Stop chip turns amber when the step looks stuck** (owner 2026-10-30; `KitComposerStatusStrip` → `KitChipTone.Attention`); the approval chip shows as its glyph when Stop shares a phone's row at large text (`4addbfe22`); the model chip ellipsizes; frame text |
| `KitPasteImage`, `KitPasteImageMenu` (freeze tip, `9ac39cd68`, `92ed57977`) | `KitPasteImageMenu` (KC) | 3 | the composer field's text context menu (`BasicTextField` toolbar / `LocalTextToolbar`) | "Paste image" added next to Paste while the clipboard holds a picture; the clipboard check runs when the menu opens, never in the background; without a picture the menu is the platform's own, item for item; the image goes to the composer's attachments |
| `KitMicHoldDetector`, `KitMicPress`, `KitMicPressOutcome` | same | 5 | `pointerInput` with `awaitEachGesture`, followed through changes | tap / hold / drag-cancel |
| `KitPhraseChips` | same | 3 | `LazyRow` | |
| `KitTaskChip`, `showKitTaskSheet`, `KitTaskItem` | `KitTaskChip`, `KitTaskSheet` | 3 | | no level of its own |
| `KitProjectFileList`, `showKitProjectFileSheet` | `KitProjectFileList`, `KitProjectFileSheet` | 3 | | |
| `KitMessage`, `KitMessageKind`, `KitBubbleWidth`, `kitPromptBubbleFill/Decoration`, `KitMessageLift` | same (`Modifier.kitMessageLift`) | 2 | `BasicText`; lift = `graphicsLayer` scale from 0.92 → 1 over `STANDARD` (250 ms) on `Enter` (Dart `kit_message_lift.dart`; **no spring, no overshoot**); none under Off | bubble radii 20/20/6/20 |
| `KitTurn`, `KitTurnPhase`, `KitTurnSegment`, `KitTurnActivity`, `KitTurnLive`, `KitTurnStall`, `KitTurnFooter` | same | 2 | block items in the transcript `LazyColumn` | the live line sweep is drawn with a `Brush` in the draw phase |
| `KitToolRow`, `KitToolKind`, `KitToolStatus`, `KitStepCommand` | same | 2 | | a failure the agent got past stays folded and neutral; output redacted before display and copy (§7.1 `KitRedact`) |
| `KitStepTimeline`, `KitStepPreview`, `KitStepNodeState`, step light / node / signal | same | 3 | step light = `drawWithCache` sweep, no recomposition | a step or live-line dot waiting on the person is amber (`kit_step_light.dart`, `kit_turn.dart`) |
| `KitWorkLine`, `KitWorkState`, `KitWorkCounts`, `KitBackgroundWorkLine`, `KitBackgroundTask` | same | 2 | `KitReveal` | |
| `KitQueuedMessage` | same | 2 | | one bubble; steer, never queue where steering exists |
| `KitAgentStrip`, `KitAgent`, `KitAgentTint` | same | 3 | | identity by name, never colour |
| `KitRequestCard` + `KitRequestDecide`, `KitRequestAlwaysAllowStep`, `KitRequestChoose`, `KitRequestChooseMany`, `KitRequestReply`, `KitRequestGo`, `KitRequestInSheet`, `kitAnsweredRow`, `showKitRequestSheet`, `KitRequestSheetOutcome`, `KitRequestAlwaysAllow`, `KitRequestMessage`, `RequestRoutes` | same (`KitRequestSheet`) + the `FormSpec` renderer | 2 / 3 | kit draw | sealed answers; A / D and 1–9 on a hardware keyboard; height cap .45 (.60 at 2.5×) |
| `KitAgentCard`, `KitAgentChoice`, `KitAgentGrid`, `KitAgentGridSpan`, `KitAgentLine`, `KitSenseAsk`, `KitHandoffRecord`, `KitContinuedLine`, `KitAskLine` | same | 3 | kit draw | the agent-card contract is ported (§01 §3.3) |

### 7.9 AI Team, illustrations, widget panels (UI-KIT-54…56)

| Dart part(s) | Native | Mod | Slice | Notes |
|---|---|---|---|---|
| `team/*` (13 files), `KitBoardLanes`, `KitBoardLane`, `KitBoardColumn`, `KitTaskCard`, `KitTaskMeta`, `KitTaskFlag`, `KitPriorityGlyph` | same names | KN (`…content.team`) | after switch (D6) | team strings are converted now (§13.3) |
| `KitIllustration`, `KitScene`, the 15 scene files | reserved `KitIllustration` | K | after switch (D11) or dropped | `KitStateView` shows its icon tile |
| `KitSteerBox`, `KitSteerBoxPhase`, `KitWidgetPreview`, `KitWidgetStyleForm` | same | K (`…kit.widget`) | 4 | used by `:feature:widget-panels`; the home-screen widget redesign itself is later (owner) |

### 7.10 Gates and catalog (UI-KIT-57, 58)

These are ported as described in §8, §14, §15 and §17.

---

## 8. Component maximum widths and the capacity contract

### 8.1 Caps by role (`:ui:design` `CapRole`; the values are Flutter's `KitLayout` tokens)

Default alignment is Dart's (`kit_max_width.dart`): page-level roles keep `AlignmentDirectional.center`; only the
inline roles (chip, popover, lane) keep the start edge. A caller passes `CapAlign.End` only for a value that ends its
row.

| `CapRole` | Cap (dp) | Aligns when wider (`defaultAlign`) | Typical parts |
|---|---|---|---|
| Conversation | 700 | centre | KitMessage, KitMarkdown, KitTurn (paints 717 on purpose: its copy and more buttons reach 17 dp past), KitToolRow, KitWorkLine, KitComposer + chip rows, KitRequestCard, KitSteerBox |
| Reading | 720 | centre (a caller may pass start for words that start their line) | KitNotice, KitReceipt, KitField, KitSegmented, KitChecklist, KitActionBlock, cards, charts, KitStatusLine, KitText, KitVitalsStrip |
| List | 960 | centre | KitRow, KitRowGroup, KitChoiceList, KitMiniTable, KitCodeBlock, KitLogPanel, KitSearchField |
| Sheet | 640 | centre | KitSheet and every sheet-hosted part; pickers |
| Confirm | 560 | centre | KitConfirm (sheet), KitFloatingPanel content |
| Dialog | 480 | centre | KitAlert, KitInputDialog, KitConfirm as a dialog on wide windows |
| Popover | 320 | start (anchored) | KitMenu, KitTerm, KitTip (bubble 280) |
| Toast | 480 | bottom-start (positioned by the host past the start clearance, Dart `kit_undo.dart`) | KitUndoBar |
| State | 440 | centre | KitStateView, KitSearchNoMatch |
| Lane | 400 | start | KitBoardLane, KitTaskCard (later) |
| Control | 400 | centre (end only when the caller passes it for a value that ends a row) | KitButton, KitOneTimeCode, KitQr, KitWheel |
| Chip | 320 | start (end when the caller passes it) | KitChip, KitFilterChip, KitStatusTag, KitTaskChip, KitJumpPill, KitRowValue, KitTerm |
| Glyph | 96 | centre | KitIcon, KitIconButton, KitStatusMark, KitAvatar, KitFloatingAction, KitUpdateDot (rings that carry words use Reading) |
| Fill | none | — | KitScreen, KitTopBar, KitNavRail, KitSidebar, KitTabStrip, KitDivider, viewers, overlays |
| Wrapper | none | — | KitTappable, KitSurface, KitFloatingSurface, motion and scroll wrappers |
| Scope | none | — | KitTheme, KitLinkScope, status scopes |

Panes (LAY-5): list 296, detail 360–700, side 340.

### 8.2 How a part holds its cap

```kotlin
// ui/kit/foundation/KitMaxWidth.kt  — a port of Dart _RenderKitMaxWidth.performLayout
fun Modifier.kitMaxWidth(role: CapRole, align: CapAlign = role.defaultAlign): Modifier {
    val capDp = role.capDp ?: return this                     // Fill, Wrapper, Scope: no layout node at all
    return this.layout { measurable, constraints ->
        val cap = capDp.dp.roundToPx()
        val childMax = minOf(constraints.maxWidth, cap)
        val placeable = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, childMax), maxWidth = childMax))
        val width = constraints.constrainWidth(placeable.width) // wraps the child; grows only when the parent forces a min width
        layout(width, placeable.height) {
            val x = when (align) { CapAlign.Start -> 0; CapAlign.End -> width - placeable.width; CapAlign.Center -> (width - placeable.width) / 2 }
            placeable.placeRelative(x, 0)     // placeRelative mirrors Start/End in RTL
        }
    }
}
```

- The box **wraps its child** (Dart: `size = constraints.constrain(child.size)`). A chip in a `Row` or `FlowRow` keeps its
  intrinsic width and never takes the remaining space. Only a parent that forces a minimum width (a caller's
  `fillMaxWidth()`) makes the box wider than the child, and then the child sits at its alignment.
- `KitMaxWidthTest` (desktop compose, commonTest) pins: a `KitChip` inside a `Row` at 412 dp keeps its intrinsic width
  and its sibling still fits; `Modifier.fillMaxWidth().kitMaxWidth(Reading)` at 1,600 dp gives a 720 dp child placed at
  its alignment (centre; start and end mirrored in RTL); a role with no cap adds no layout node; and every role's
  `defaultAlign` at 1,600 dp matches §8.1.
- The cap is applied **inside** the part (its outermost modifier), so a caller cannot widen it. A caller's
  `Modifier.fillMaxWidth()` fills the slot, and the part's own content still stops at the cap.
- On a phone every cap except Glyph is wider than the content, so phones are untouched (Flutter's rule).
- A capped part follows its column: page-level parts centre, bars fill, a chip ends on the trailing edge
  (`d99028f52`). Caps never go on containers or grounds (`1d603c78d`).
- **Lazy lists:** the list itself fills the window, so the overscroll stretch and the scrollbar sit at the window
  edge. Each item applies its own cap. `KitScreen(width = Reading/List)` adds centred content padding computed from
  the window width.
- **Literal ban:** a `.dp` literal compared against a width anywhere outside `ui/design/layout/Layout.kt` fails
  `LiteralBanTest` (G15 port).

### 8.3 The capacity contract, ported

- Copy `test/kit/kit_capacity.json` and `kit_capacity_allowlist.json` (freeze tip) into `ui/kit*/capacity/`, split by
  module. The new `:tools:capacity-port` script (a Kotlin main in `:tools:strings`) applies `kit_renames.tsv` (§7.0):
  a `rename` row renames the key, `merge-into` merges its slots into the target's entry, `drop` and `defer` move the
  entry to `capacity/deferred.json` with the reason. A Dart key with no native part and no row fails the run, so no
  orphan entries survive.
- The schema does not change:
  - role cap;
  - each text slot's overflow rule: `wrap`, `ellipsis:N`, `fade:N` or `scroll` (`clip:N` and `visible:N` are
    violations);
  - each list slot's `beyond` rule: `grows`, `wraps`, `scroll`, `more`, `page`, `fixed` or `not-items`, with `shows`.
- Copy `test/support/stress_content.dart` to `ui/testing/src/commonMain/kotlin/…/StressContent.kt`. The fixtures:
  about 1,000 characters of prose (Arabic prose in RTL), a 300-character unbroken URL, CJK without spaces, emoji ZWJ /
  flags / keycaps, huge numbers, the empty string, pseudo-localisation (+40 % in brackets, accented), and lists of 50.
- **How the native gate measures.** Kit parts publish test-visible semantics (`KitSemantics.kt`), **static values
  only**, so nothing is written back from a text layout on the hot path (streaming transcript blocks included):
  - `KitPart` — the part name on the part's root node;
  - `KitTextFit` — on every `KitText`: the declared rule only (max lines and overflow kind: `wrap`, `ellipsis:N`,
    `fade:N`, `scroll`).

  The measured values (line count, `hasVisualOverflow`, whether the ellipsis or fade was drawn) are read **in tests
  only**, by calling the text node's `SemanticsActions.GetTextLayoutResult`, which Compose text nodes already expose.
  The capacity gate and `ScreenStandardCheck` (§15.5) share one helper, `textFitOf(node)`, in `:ui:testing`.
- **Gate** (`ui/kit/src/androidHostTest/…/KitCapacityTest.kt`, Robolectric `@GraphicsMode(NATIVE)`, JDK 21, sharded
  `-Pshard=1..8`):
  - It runs every part's capacity scene at text 1.0, **1.3** and 2.0 (1.3 is where row titles start to wrap,
    supporting lines go to 2, the frame cap begins and `1.2999999523 ≥ 1.29` matters):
    - at 280 dp with every fixture, LTR and RTL;
    - at 412 dp as written, pseudo-localised and with prose, both directions;
    - at 1,600 dp, LTR, as written and with prose, token, numbers and pseudo.
  - **The G6 port** (Flutter `test/kit/kit_overflow_matrix.dart`): the adaptive and row parts (sheet, confirm, dialog,
    undo, top bar, row family, chips, buttons, action block, field, segmented, status line, composer) also run at
    320×640, 360×740, 600×960, 800×1280, 840×1180, 1280×800 and 915×412, at text 1.0 / 1.3 / 2.0, LTR and RTL.
    600 and 840 are the window-class switches. `KitOverflowMatrixTest` is its own sharded Robolectric class.
  - It fails on:
    - any exception;
    - a `KitTextFit` that lost words without the declared ellipsis, fade or scroller;
    - `boundsInRoot.width` > cap at 1,600 dp (ink only);
    - a touch target < 48 dp at 412 dp, text 1.0 (`SemanticsNode.touchBoundsInRoot`);
    - a list slot drawing more than `shows`;
    - a public kit part with no contract entry.
  - The allowlist only shrinks. `-Pkit.capacity.write=true` tightens it and never adds.
- A desktop-JVM twin (`commonTest`, `runComposeUiTest`) runs only the 1,600 dp width checks, so agents get a T0
  signal without Robolectric. Android text metrics are the truth, so the Robolectric gate is the T1 gate.
- Each part's capacity scene is a separate `<Part>CapacityScene` in the test source set (§15.2), because previews in
  `commonMain` cannot see the test-only stress fixtures.

---

## 9. Text scale: frame text capped at 1.3×, reading text full

### 9.1 The model

- **System scale `s`** comes from Android, as a float (1.3 arrives as `1.2999999523`). Android 14+ applies
  **non-linear** scaling, so small sizes grow nearly ×s and large sizes grow less.
- **App Text size `a`** (`oc.textSize`): Smallest .85, Small .92, Normal 1.0, Large 1.15. It multiplies on top and
  eases on change (`STANDARD`, or instant under Off).
- **Root cap:** no text renders larger than 2.5× its spec size (Dart `AppTheme.maxTextScale`).
- **Frame cap:** frame text never renders larger than 1.3× its spec size (owner 2026-10-31, option A).
- **Reading text** follows `a × s` up to the root cap.
- **Fixed caps that stay** (Dart named constants): leading and state icons 1.5×, terminal text 2.0×, terminal key caps
  1.3×, monogram 1.3×, badges and counts 1.3×.

### 9.2 Implementation (rewritten from slice-0 P7, 2026-11-01)

**The earlier sketch here was wrong, and P7 measured why** [V `docs/native/slice0/P7-kit.md` D1 on
`native/m0-2026-11-01`]. It overrode `TextUnit.toDp()` in a custom `ScaledFontDensity`. Compose lays text out with the
layout coordinator as its `Density`, and the coordinator forwards only `density` and `fontScale`, so a custom `toDp`
never reaches text on Android, which re-derives sizes from `fontScale` through its own non-linear converter. With that
class, 16 sp at 200 % rendered at 20.2 dp instead of 20.8 dp. Desktop is linear, which hid the problem. Capping the
scale alone (the old "linear cap" fallback, or `FontScaleConverterFactory`) would leave frame titles up to 21 % smaller
than Flutter (28 sp → 28.8 dp against Flutter's 36.4 dp) and let 13 sp exceed 1.3×. What P7 built, and what this plan
now freezes:

```kotlin
// ui/kit/foundation/KitTextScale.kt (shape as built in P7; signatures freeze in contracts/kit-v1.md)

/** Root: KitTheme provides the app density with ONLY the font scale changed (a × s, capped at 2.5×). The uncapped
 *  app density is also kept in LocalKitAppDensity for frame text to measure against. */
@Composable internal fun scaledFontDensity(base: Density, multiplier: Float, cap: Float): Density =
    remember(base, multiplier, cap) { Density(base.density, minOf(base.fontScale * multiplier, cap)) }

/** Frame text: KitText(role = frame) computes the exact frame size per spec size, then lays its words out at that
 *  size under font scale 1, with line height and tracking scaled in proportion. */
internal object TextScalePolicy {
    const val ROOT_CAP = 2.5f
    const val FRAME_CAP = 1.3f
    /** dp a frame text of [spec] renders at: the platform's own (non-linear) size, never above FRAME_CAP × spec. */
    fun frameDp(spec: TextUnit, appDensity: Density): Dp = with(appDensity) {
        minOf(spec.toDp().value, spec.value * FRAME_CAP).dp
    }
}

/** Wrapper for NON-text content inside frame parts (icons sized from text): caps the font scale at 1.3. KitText in
 *  frame role does not rely on it; it uses frameDp above. */
@Composable fun KitFrameText(content: @Composable () -> Unit)

/** The effective scale at body size; kit layout decisions read this (`>= 1.29f`, never `>= 1.3f`). */
@Composable fun kitTextScale(): Float
```

Notes:

- The cap is relative to the **spec** size (the `sp` value), so frame text is ≤ 1.3 × spec whatever `a × s` is. Below
  the cap it keeps the platform's non-linear size exactly (the owner's "keeps Android's nonlinear scaling"), because
  `frameDp` asks the platform's own converter (through the uncapped app density) and only clips the result.
- Icons inside frame parts use `min(kitTextScale(), 1.5)` explicitly; `KitFrameText` caps any other content that
  converts text units.
- **Proven in P7** (Robolectric at SDK 37, fontScale 2.0): a frame `KitButton` label of 16 sp measures 20.8 dp, which is
  exactly Flutter's rule measured against Android's own non-linear converter; a reply body keeps the platform's size.
  Goldens: `native/ui/kit/src/screenshots/KitFrameText/`. Still owed: the side-by-side with the Flutter app on the
  emulator at 200 % (P7 open problem 1).
- The `JETPACK_AHEAD` row for `FontScaleConverterFactory` (§6.1) is **not needed** and is dropped.

### 9.3 Which parts are frame text

**The rule is mechanical: a part is frame text if and only if its Dart build is wrapped in `KitChrome` on the freeze
tip, or it is built on such a part.** That is Flutter's list (kit-v2 "Text scale: frame and reading"): `KitTopBar`,
`KitChip`, `KitButton`, `KitField.composer`, `KitComposerStatusStrip`, `KitSessionTabs` and the `KitSheet` title.
Everything else keeps the full scale or its own named Dart cap. The owner's rule exists for low-vision readers, so
the list is not extended silently: the extensions an earlier draft proposed are owner question §19 Q10.

| Frame text (≤ 1.3×; Dart `KitChrome`) | Reading text (full, ≤ 2.5×) | Own fixed cap (Dart named constants) |
|---|---|---|
| KitTopBar family (title, pill, scope, link, branch, origin), KitShellControls | KitMessage, KitMarkdown, KitTurn words, KitToolRow output, KitStepTimeline words, KitCodeBlock (reading mono) | KitIcon leading/state ≤ 1.5× |
| KitChip and the parts built on it (KitFilterChip(s), KitTaskChip, composer chips, phrase chips) | KitRow title and supporting line, KitSwitchRow, KitExpandRow, KitFeedItem | Terminal text ≤ 2.0× |
| KitButton and the parts built on it (KitActionBlock labels) | KitStateView, KitNotice, KitReceipt, KitStatusLine, KitChecklist, KitDetailsFold | Terminal key caps ≤ 1.3× |
| KitComposer field + hint (`KitField.composer`); KitComposerStatusStrip | KitField value, helper and error (non-composer kinds); KitStartBox; KitAskField; KitChoiceList | Badges and counts ≤ 1.3× |
| KitSheet title (header) | KitSheet subtitle and body; KitConfirm and KitAlert title and body; menu items; tips and tour cards; help pages | Avatar monogram ≤ 1.3× |
| KitSessionTabs | KitUndoBar message and action; KitJumpPill; KitBreadcrumb; KitSegmented labels; KitTabStrip; Settings rows; Appearance questions | KitNavRail / KitSidebar labels ≤ 2.0× (`navLabelMaxScale`) |
| KitVitalsStrip cells (its own 1.3 constant) | Log panel, diff, viewer bodies; KitVitalsStrip summary | — |

- Any new part declares its kind in its KDoc (`Text: frame` or `Text: reading`). The manifest gate (§14.4) fails a
  part with no `Text:` line. A new frame part is an owner-visible change: it goes in the PR summary with a 200 %
  capture.
- TalkBack labels are unchanged by the cap.
- Chips at 2× in en and ar never ellipsize mid-word: `KitWordText` cuts there.

### 9.4 Tests (B-KIT-052, B-KIT-059, B-KIT-060)

- `TextScalePolicyTest` (pure): the multiplier table, the root cap, and the `1.2999999523 ≥ 1.29` comparison.
- `TextScaleCapTest` (Robolectric, fontScale 1.0 / 1.3 / 2.0, `sdk = [33, 36]`): frame vs reading sizes, as in §9.2.
- `TextSizeChoiceTest` (`:engine:settings` + shell): the default, persistence, and application at the root and in
  Settings; main screens still work at Smallest.

---

## 10. Adaptive layouts: phone, tablet, desktop

### 10.1 Window classes (`:ui:design` `WindowClass`, measured on the **window**, never the device)

| Class | Width (dp) | Shell scene (owned by `:ui:shell`, §01 §9.4) | Kit behaviour |
|---|---|---|---|
| Compact | < 600 | single pane, **no bottom bar**; Conversations is home; search and gear in the top bar | sheets are bottom sheets; confirm is a bottom sheet; top bar title + ≤ 2 icons |
| Medium | 600–839 | floating **rail** 80 dp, icon + label always | sheets capped 640, centred; confirm capped 560; top bar ≤ 3 icons |
| Expanded | 840–1199 | **sidebar** 296 dp (up to 400 or ⅓ of the window at large text) | sheets become a centred 560 panel; `Full` sheets become a 400–480 end-side sheet; confirm becomes a 480 dialog; diffs side by side; labelled top-bar actions |
| Large | ≥ 1200 | sidebar; three-pane where a screen asks for it (list 296, detail ≤ 700, side 340) | as Expanded |
| **Short** | height < 480 (any width) | keeps the compact arrangement (phone landscape) | modal parts stay ≤ Medium behaviour |

- `LocalKitWindow` is computed in `KitTheme` from `LocalWindowInfo.current.containerSize` and density. `:ui:shell`
  may also use `currentWindowAdaptiveInfo()` (material3-adaptive 1.3.0) for its scenes, but kit parts read only
  `LocalKitWindow`, so one answer exists.
- **Panes:** `KitScreen.twoPane(list, detail)` folds to one column when the detail would be < 360. Route-level panes
  (Settings, Files) are Nav3 list-detail scenes in the shell. `KitScreen.twoPane` is for in-screen selection.
- **Chat** is always a pushed page, width-capped at 700 and centred from Medium up.
- **Foldables:** treated by window width only. Hinge-aware layouts are not on day 1 (§19 Q8).

### 10.2 Input on tablets and desktop (kit-v2 §8.3)

- Touch targets stay 48 dp everywhere. A fine pointer adds hover and tooltips, never smaller targets.
- Hover: `Modifier.hoverable` feeds the surface state (hover fill from surface steps). Hover never carries the only
  copy of information.
- **Keyboard:**
  - Tab follows the reading order.
  - The focus ring is always visible: 2 physical px, accent, drawn by `KitTappable` from `onFocusChanged`.
  - Esc closes the top overlay and obeys the draft and dirty rules.
  - Enter confirms only a Neutral confirm; it is the primary action in input dialogs.
  - Arrow keys move within segmented and choice groups and swatch grids, and Space selects.
  - Home, End, PgUp and PgDn work in lists.
  - Request cards: A (allow), D (deny), 1–9 (choices) once focused.
- Right-click and long-press open the same `KitMenu`. Shift+F10 and the Menu key open it from the keyboard.
- **Scrollbars:** desktop only, always visible where a scroll state exists. None on Android (platform default).
- **Hardware keyboards on Android tablets:** the same key handling, through `onPreviewKeyEvent` in the shell (F2, F4,
  Ctrl+B and the others in screens-nav §5.2).

### 10.3 Desktop specifics (compile now, product later)

`DesktopKitPlatform` gives `finePointer = true`, no ripple (hover/press fill), no haptics and no frost. The kit
gallery runs on it from slice 0. Compose Desktop on Linux has no screen reader (§01 D14) and renders through XWayland.
Desktop never blocks a change (§01 §7.1).

### 10.4 Tests

- `WindowClassTest`: the boundaries 599/600, 839/840, 1199/1200, and short height 479/480.
- `KitScreenLayoutTest` (desktop compose): the status slot, bottom block, width classes, and two- and three-pane fold
  at 360 (B-KIT-029).
- Roborazzi captures at 412×915, 915×412 (short), 800×1280, 1280×800 and 1600×1000 for the parts that adapt: sheet,
  confirm, dialog, undo, top bar, row, diff.
- `KitMaxWidthTest` (§8.2) pins each `CapRole`'s cap and default alignment at 1,600 dp, LTR and RTL, so a start-aligned
  column on tablets and PCs fails a test.

---

## 11. Accessibility

### 11.1 TalkBack semantics every part must set (checked by the manifest gate and the part's own tests)

| Concern | Rule | Compose API |
|---|---|---|
| Role | Every interactive part declares its role: Button, Switch, Checkbox, RadioButton, Tab, Image, DropdownList | `Modifier.semantics { role = … }` through `KitTappable(role = …)` |
| Labels | Icon-only controls need a label (assert in debug; manifest check). Decorative icons have none. | `contentDescription`; `KitIcon(decorative = true)` default |
| Click labels | Tap and long-press actions say what they do ("Open", "More actions for <name>") | `onClick(label = …)`, `onLongClick(label = …)` |
| Hidden gestures as actions | Every long-press menu item and every swipe action is also a custom action | `customActions = menu.items.map { CustomAccessibilityAction(it.label) { … } }` (B-KIT-051) |
| Disabled reason | A disabled control states why, once | `disabled()` + `stateDescription = reason` (from `KitEnabled.Disabled`) |
| State | Selected, checked, expanded, collapsed, working | `selected`, `toggleableState`, `stateDescription`, `expand()/collapse()` |
| Headings | Section labels, page titles and sheet titles | `heading()` |
| Live updates | The status line, receipts, field errors, search result counts, the "Copied" note, tips, the composer phase, tour cards | `liveRegion = LiveRegionMode.Polite` (Assertive never) |
| Panes | Sheets, dialogs, confirm, tour | `paneTitle = title` |
| Order | Reading order; the reverse-layout transcript sets traversal explicitly (chat section) | `isTraversalGroup`, `traversalIndex` |
| Grouping | A row reads as one item (title, supporting line, value, state) | `mergeDescendants = true` on `KitRow` |
| Skeletons, scenes, decorative marks | Hidden | `clearAndSetSemantics {}` |
| Colour alone | Never: every state has a word or a glyph (§4.10); needs-you always has its word | design rule; screenshot review |

- **Gesture twins and tips (DESKTOP-WEB-CROSS-29, owner rule "teach hidden gestures").** Every hidden gesture
  (long-press, swipe, double-tap, drag, edge gesture, keyboard shortcut) has three things before its part or screen
  merges: a **visible twin** (a button, row or menu item that does the same thing), the `customActions` entry above, and
  a **tip** registered in `TeachingRegistry` with en and ar copy and a Help › Tips row. The native ledger
  `native/config/gesture-ledger.json` (port of `docs/design/ui-ledger/gesture-audit.json`, 101 entries at the freeze
  tip) lists each gesture with `{id, where, twin, tipId}`; `GestureLedgerTest` (`:quality`, T2) fails when a
  `KitTappable(onLongClick)`, `KitSwipeActions`, `detectDragGestures`, `combinedClickable` or `KeyboardShortcut` call
  site is not in the ledger, or when a ledger entry has no twin or no tip. Tips that need the full teaching UI (tour)
  may land later; the twin and the registry entry may not.
- **Announcements.** `View.announceForAccessibility` is not used. Every announcement is a live region, which works
  on Android, desktop (Windows/macOS) and iOS later.
- **Touch targets.** 48 × 48 dp minimum through `KitTappable`'s hit area (chips draw 32 inside 48). No two 48 dp areas
  overlap. A destructive target keeps 8 dp from any other (LAY-9). The capacity gate checks 48 dp at 412 dp.
- **Contrast.** It is computed, not eyeballed: the §4.1 floors are tested for 31 packs × 2 brightnesses × 5 styles ×
  the knob combinations, plus Saver and dynamic samples (B-KIT-041, B-KIT-061, B-KIT-062). Field edges and
  off-switches reach 3:1 at rest in every style.
- **Text scaling.** §9. Reading text reaches 2.5×; supporting lines wrap rather than truncate.
- **Reduced motion.** §4.8. The system animator scale 0 always wins.
- **Keyboard and switch access.** §10.2. Switch Access uses the same focus order.
- **Copy and secrets.** Secret fields take no part in autofill (neither save nor offer) and tell the keyboard not to
  learn (`IME_FLAG_NO_PERSONALIZED_LEARNING`); they are never prefilled and show "Saved · Replace" for a stored value.
  `KitField(Secret)` uses `BasicSecureTextField`, which Android treats as a password field. Copies are marked
  sensitive. TalkBack reads "Password, saved" for a stored value, never the value or its length.
- **Desktop Linux** has no screen reader (D14). Windows and macOS map semantics through CMP's accessibility bridge.

### 11.2 Tooling

- `AccessibilityRulesTest` (Robolectric, per part preview) uses Compose's accessibility checks:
  `ComposeUiTest.enableAccessibilityChecks()`, the Android Accessibility Test Framework, available in ui-test 1.8+
  [U: confirm under Robolectric in P7; fallback = run on the emulator in T3]. Plus our own checks: touch-target size,
  a label on every role node, no clickable node without a role.
- **Manual pass:** the auditor-only agent (owner rule `independent-visual-auditor`) runs TalkBack on the emulator over
  the daily path (Conversations → chat → send → approve → settings) at T3 before each owner APK.

---

## 12. RTL and Arabic

### 12.1 Rules (port of LAY-8, gate G7)

1. **Directional APIs only.** Use `padding(start/end)`, `Arrangement.Start/End`, `Alignment.Start/End`,
   `TextAlign.Start/End`, `offset` (which mirrors), and `placeRelative`.
   - Outside `ui/kit/**/rtl_allowlist.txt` (shrink-only), these are banned by Konsist: `absolutePadding`,
     `absoluteOffset`, `AbsoluteAlignment`, `Arrangement.Absolute*`, `TextAlign.Left/Right`, `placeAbsolute`,
     `LayoutDirection.Ltr` provision (except `KitLtr`), and `Modifier.scale(-1f, …)`.
2. **Drawing is not mirrored by Compose.** Every kit `drawBehind`/`Canvas` that has a horizontal direction reads
   `layoutDirection` from the `DrawScope` and mirrors. That covers:
   - Brutal's hard shadow (bottom-end);
   - Signal's track, lamps and slip perforation;
   - the progress bar fill direction;
   - the needs-you start edge;
   - the bubble tail;
   - the step-timeline rail;
   - the scroll-fade edge;
   - the context ring (direction as in the Flutter Arabic golden).

   Each painter's test renders LTR and RTL and compares against a mirrored LTR capture.
3. **Glyphs:** the 12 auto-mirrored glyphs (§4.11). Nothing else mirrors (B-KIT-001).
4. **Back is at the start and Close at the end.** Predictive back shifts *away from the swipe edge*, not by layout
   direction, because in RTL either edge can start the gesture (motion-native §3.4).
5. **Technical values are LTR, isolated:**
   - paths, commands, URLs, ids, hosts and fingerprints go through `Bidi.isolateLtr(value)` (LRI … PDI) when inserted
     into a sentence;
   - `KitLtr` blocks (code, logs, diff, terminal, fields of kind Mono, Path, Url or Secret) are laid out LTR;
   - bidi control characters may appear only in `Bidi.kt` (the G7 port);
   - B-L10N-006 rows test `Bidi`.
6. **Text direction of content (K17).** Prompts, replies and tool words use `TextDirection.Content`, so each paragraph
   follows its first strong character. Frame copy uses the locale's direction.
7. **Arabic type:** system sans and letter spacing 0. Signal's engraved small capitals become plain bold with tracking
   0. Mono stays Geist Mono.
8. **Digits: Latin (Western) digits in every language, as the Flutter app shows them today.** Flutter's intl 0.20.3
   `ar` symbols use Latin digits for numbers (`NumberSymbols` `ar`: `ZERO_DIGIT '0'`) and dates (`DateSymbols` `ar`
   has no Arabic-Indic zero digit; only `ar_EG` and similar regions do), while Android's locale data may default `ar`
   to Arabic-Indic. So `formatNumber` and `formatMoment` format with the locale tag plus `-u-nu-latn` (for example
   `Locale.forLanguageTag("ar-u-nu-latn")`), and so do the `:android:*` notification and widget time formatters
   (§22 asks §11 to use the same helper). CMP templates insert `arg.toString()`, which is Latin already. The parity
   capture of an Arabic screen with numbers and times is the check.
9. **Input:** `KitField(Number)` turns Arabic-Indic digits and the Arabic decimal separator into ASCII
   (`KitNumberFormatter` port).
10. **Plural forms:** Arabic uses zero, one, two, few, many and other (§13.4).

### 12.2 Tests

- `RtlRuleTest` (Konsist): the API bans in rule 1.
- `RtlPainterTest` (Roborazzi): rule 2, one capture pair per painter.
- Every kit part's preview set includes an `ar` RTL capture (§15.3).
- `BidiTest` (pure): B-L10N-006.
- `ArabicDiacriticsTest`: §4.4.

---

## 13. Localization: from ARB to native strings, once

### 13.1 Languages

The brief said "8 locales". The source ships **7** (`AppLocaleStore.supportedLanguages = {en, ar, es, ja, pt, ru, zh}`),
and §01 says 7. This plan ships the 7. The pipeline does not depend on the number of languages, so an 8th language is
one folder plus a picker row (§19 Q1). The two qa-only pseudolocales (§13.7) are not shipped languages.

Counted on the freeze tip `fc5c1e78f` [R, 2026-11-01]. Batch1 had 369 fewer keys per language in en and ar (en 9,468,
ar 10,486). The converter's report is the final word, and it runs only after the last Flutter merge.

| Lang | Keys today (ARB) | Messages with a plural | `select` messages | Direction | Coverage vs en (9,837 keys) |
|---|---|---|---|---|---|
| en | 9,837 | 267 | 6 | LTR | 100 % (reference) |
| ar | 10,855 (1,018 orphans dropped) | 309 | 7 | **RTL** | 100 % |
| zh | 8,654 | 222 | 2 | LTR | ≈ 88 % |
| es | 2,695 | 80 | 1 | LTR | ≈ 27 % |
| ja, pt, ru | 661 each | 29 each | 0 | LTR | ≈ 7 % (the rest falls back to English) |

Placeholders in en: 1,562 `String`, 601 `int`, 38 `Object`, 14 `num`. 44 placeholders carry `format: decimalPattern`.
Plurals: counted per message (a message with two plurals counts once; batch1's 228 whole-message + 33 embedded = 261
by this count; the converter report gives the freeze split).

Android-native strings today: `res/values{,-ar}/strings.xml` (148 each), `widget_v2_strings.xml`,
`widget_v2_bridge_strings.xml`, `scheduled_prompts.xml` and `bb_idle_strings.xml`, in en and ar only.

### 13.2 Where strings live

```
ui/words/src/commonMain/composeResources/
  values/      strings_common.xml strings_kit.xml strings_failures.xml strings_os.xml
               strings_onboarding.xml strings_chats.xml strings_chat.xml strings_composer.xml strings_chat_pages.xml
               strings_servers.xml strings_phone.xml strings_agents.xml strings_project.xml strings_settings.xml
               strings_usage.xml strings_schedules.xml strings_about.xml strings_search.xml strings_widget_panels.xml
               strings_team.xml strings_factsbook.xml          # converted now, used after the switch (D6)
  values-ar/   the same file names
  values-es/ values-ja/ values-pt/ values-ru/ values-zh/      # the same file names where keys exist
```

Generated accessor class: `io.github.eslamasabry.oc.ui.words.res.Res`. In `ui/words/build.gradle.kts`:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.eslamasabry.oc.ui.words.res"
    generateResClass = always
}
```

- **Feature files are F-owned.** The feature agent edits only `strings_<feature>.xml`, in every language folder. The
  l10n owner owns `strings_common.xml`, `strings_kit.xml` (with the kit owner), `strings_failures.xml`,
  `strings_os.xml` and the tooling (§01 row 22).
- **Key names stay the ARB names** (`chatSendButton`, `e7ConnectionFailure1`), so a grep across Flutter and native
  finds both. Android resource names allow camelCase.
- `strings_os.xml` holds every string the OS reads from XML or that a receiver, service or widget shows:
  - the app label, shortcut labels, widget descriptions, notification channel names, tile labels;
  - the 148 native strings plus the widget, schedule and idle files above.

  **Their resource names stay exactly as they are today**, because pinned shortcuts and widgets reference them.
- **One copy, in one small library.** The `generateOsStrings` task lives in **`:android:os-strings`** (an
  `android.library` with resources only, owned by the l10n owner). It copies `strings_os.xml` from every `values*`
  folder of `:ui:words` into `build/generated/res/osStrings/values*/` and registers it with
  `androidComponents.onVariants { it.sources.res?.addGeneratedSourceDirectory(…) }`. `:androidApp` and every
  `:android:*` module that reads `@string/` depend on it (today's `res/layout` and `res/xml` hold 43 `@string/`
  references on the freeze tip, most in the widget layouts and widget info files that move to `:android:widgets`). An app module may reference a library's resources, so the manifest's app label and
  shortcut labels resolve too. One copy means no parity test between copies; this replaces §11's CR11-8 two-copy plan
  (§22). Android lint `MissingTranslation` runs on the generated copy.
- **`strings_os.xml` exists in all 7 language folders**, never only en and ar: the converter writes it for every
  shipped language (at least `app_name`, a name that is the same in every language, plus every OS string whose
  translation exists or that the l10n owner accepts from the report's ARB matches). That is what gives AGP a
  `values-xx` folder per language for the generated LocaleConfig (§13.6). `:tools:strings check` fails when a shipped
  language has no `strings_os.xml`.

### 13.3 The converter (`:tools:strings`, run once; the l10n owner runs and reviews it)

```bash
# Source: the freeze tip (int/freeze-2026-11-01, recorded SHA; fc5c1e78f today). Run only after the last Flutter merge.
./gradlew :tools:strings:run --args="convert \
  --arb /home/eslam/Storage/Code/wt/oc_app-freeze/lib/l10n \
  --dart-lib /home/eslam/Storage/Code/wt/oc_app-freeze/lib \
  --android-res /home/eslam/Storage/Code/wt/oc_app-freeze/android/app/src/main/res \
  --source-sha fc5c1e78f \
  --routes tools/strings/feature-routes.tsv \
  --out ui/words/src/commonMain/composeResources \
  --report build/strings/convert-report.md"
```

The converter refuses to run when the worktree's `HEAD` is not `--source-sha`, and writes the SHA into the report and
into a header comment of every generated file. If the freeze branch moves, the coordinator re-records the SHA at M0
and the conversion runs again.

Steps (each tested in `ConverterTest` with fixtures cut from the real ARB files):

1. **Parse** every `app_<lang>.arb` in key order. Check `@@locale`. Drop ar/zh/es/ja/pt/ru keys absent from en (the
   1,018 Arabic orphans, plus any others) and list them in the report.
2. **Route each key to a feature file:**
   - Search **every Dart file under `lib/` except `lib/l10n/`** for a word-boundary match of `.<key>`. There is no
     import filter: about 290 files are `part of` another library (every `chat/*.dart` part of `chat_screen.dart`,
     the `kit_sheet.dart` parts such as `kit_confirm_sheet.dart`), and a part file inherits its library's imports and
     never imports `app_localizations.dart` itself. An import filter would mark about 2,300 used keys (24 %) as
     unused; without it about 1 % stay unused [R: both rules run over the freeze tip's ARB and `lib/`].
   - **Route by the path of the file where the key is used** (for a part file, the part file's own path, not its
     library's).
   - Map each file path to a feature with `feature-routes.tsv`. Example rows: `lib/ui/kit/` → `kit`,
     `lib/ui/screens/chat/` → `chat`, `lib/widgets_overlay/` → `widget_panels`, team paths → `team`.
   - Keys starting `team` → `strings_team.xml`; keys starting `fb` → `strings_factsbook.xml`.
   - A key used by exactly one feature goes to that feature's file. A key used by several goes to
     `strings_common.xml`. A key used nowhere is **not converted** and is listed as unused, for the owner to confirm
     before the ARB freeze.
   - **The report fails the run when more than 2 % of en keys come out unused**: that share means the search missed
     files, not that the copy is dead.
   - **An unrouted path fails the run.** Nothing lands in `common` silently.
   - `ConverterTest` includes a fixture built from a real part file: `lib/ui/kit/kit_confirm_sheet.dart`
     (`part of 'kit_sheet.dart'`) using `kitConfirmKeepRunning` must route the key to `strings_kit.xml`.
3. **Placeholders:** ARB `{name}` becomes positional `%N$s` (String, Object, num) or `%N$d` (int).
   - N is the order of first appearance **in the English string**, and the same index map is applied to every
     language. A translation that reorders words keeps the right values.
   - The 44 `decimalPattern` placeholders become `%N$s`. They are listed in the generated `NumberArgKeys.kt`, and a
     Konsist rule makes every call site pass `kitNumber(n)` (which formats through `KitPlatform.formatNumber`).
4. **Plurals:** a whole-message ICU plural becomes `<plurals>`.
   - An embedded plural (33 in en) is expanded: the text around it is copied into every quantity item.
   - A message with two plurals is **refused**, like a nested plural (P6 found 7, not "a handful"); the l10n owner
     writes them by hand and the converter lists them.
   - Every `<plurals>` lists every category of its language; a missing one takes its exact `=N` branch, else `other`.
     A key that one language pluralises and another writes as plain text is promoted to `<plurals>` everywhere.
     Branches a language's rules can never pick (47 `one{…}` in zh) are dropped and listed. [V P6]
   - Exact selectors `=N` follow §13.4.
5. **`select` messages** (6 en, 7 ar) are **refused** by the converter (P6 decision, "fail loudly"); with
   `--skip-unsupported` it converts everything else and lists them. The l10n owner writes one
   `<string name="<key>__<branch>">` per branch by hand, and code chooses the branch with an exhaustive `when` (§01
   §7.5: "the 6 ICU `select` messages become code").
6. **Escaping:**
   - **Settled by P6 [V CMP `PrepareComposeResources.handleSpecialCharacters`]:** after DOM parsing CMP turns only `\n`,
     `\t`, `\uXXXX` and `\\` back into characters; `\'`, `\"`, `\@` and `\?` would reach the screen with their
     backslash. So there are two profiles:
     - **feature files (CMP only):** `'`, `"`, `@` and `?` stay as typed;
     - **`strings_os.xml` (CMP and Android `res`):** `'` and `"` become `\u0027` / `\u0022`; a leading `@` or `?` and
       leading, trailing or repeated spaces become `\uXXXX` (both CMP and aapt2 decode those); a literal `%` with no
       placeholder gets `formatted="false"`.
   - Both profiles: XML-escape `&`, `<` and `>`; newline `\n`, tab `\t`, backslash `\\`; bidi marks, zero-width and other
     invisible characters become `\uXXXX` so a reviewer sees them.
   - CMP inserts every argument with `toString()` whether the token is `%1$d` or `%1$s`; the 35 `decimalPattern` keys
     need a locale-formatted number passed in (`kitNumber(n)`).
   - Unused keys go to `unused/strings_unused.xml` outside `composeResources` (keys in = keys out); they do not ship.
7. **Android strings:** merge the 6 native XML files into `strings_os.xml`, keeping the names. Where an ARB key holds
   the same English text in more languages, the converter offers the match in the report. It never auto-copies. It
   always writes a `strings_os.xml` for **all 7** languages (§13.2), so every shipped language has its `values-xx`
   folder.
8. **Report:** the source SHA, counts per file and language, unused keys and their share, dropped orphans,
   multi-plural and select messages, decimalPattern keys, and routing conflicts.

After conversion **the ARB files are frozen with Flutter**. New copy goes into the XML only.

### 13.4 Plural categories and exact selectors

CMP `pluralStringResource` picks a CLDR category. An ICU `=N` selector is an *exact* match, which a category does not
always express.

| Lang | CLDR integer categories | `=0` | `=1` | `=2` |
|---|---|---|---|---|
| en | one {1}, other | exact | → `one` | exact |
| ar | zero {0}, one {1}, two {2}, few {3–10, 103–110…}, many {11–99…}, other | → `zero` | → `one` | → `two` |
| es | one {1}, many (1,000,000…), other | exact | → `one` | exact |
| pt | one {0, 1}, many, other | exact | **exact** (`one` also covers 0) | exact |
| ru | one {1, 21, 31…}, few, many, other | exact | **exact** (`one` also covers 21…) | exact |
| ja, zh | other | exact | **exact** | exact |

- Rule: wherever any language uses `=N` for a key, the converter emits `<string name="<key>__eq<N>">` in **every**
  language that has the key. Where `=N` maps cleanly to a category it uses that category's text, so a lookup never
  falls back to English inside an Arabic screen.
- It then generates one helper per such key in `ui/words/src/commonMain/kotlin/…/words/PluralsGenerated.kt`:

```kotlin
@Composable fun Plurals.filesLeft(count: Int): String = when (count) {
    1 -> stringResource(Res.string.filesLeft__eq1, count)
    else -> pluralStringResource(Res.plurals.filesLeft, count, count)
}
suspend fun PluralsText.filesLeft(count: Int): String = …   // the same rule, for notifications and widgets
```

- Counts on the freeze tip, per message: en `=0` ×30, `=1` ×202; ar `=0` ×57, `=1` ×222, `=2` ×66; zh `=0` ×24,
  `=1` ×136; es `=0` ×4, `=1` ×53; ja, pt, ru a handful each. (The converter report gives per-selector totals.)
- **Arabic must have all six categories** for every plural (the G27 port). P6's `PluralRules.kt` was checked rule by
  rule against CMP's CLDR lists for the 7 languages (ar 0, 1, 2, 3, 11, 100 → zero, one, two, few, many, other) [V P6].
- **A language borrowing English plural text** (P6 finding): when a key is missing in pt or ru, CMP reads English's
  `<plurals>` but picks the category with pt or ru rules (pt 0 → `one` → "1 draft queued"; ru 21 → `one`). The
  generated helpers resolve a key missing in the current language with the **English** rules. The on-device P6 proof
  includes pt, count 0, on an English-only key.
- The helper checks `__eqN` **before** `pluralStringResource` (pt `kitNeedsYouWaitingSpoken`: only `__eq1` has the right
  text for 1, because pt `one` also covers 0). P6's `report/exact-selectors.tsv` lists the 209 keys that need it.

### 13.5 How code reads words

- **In a composable:** `stringResource(Res.string.chatSend)`, `pluralStringResource(Res.plurals.x, n, n)`, or the
  generated `Plurals.x(n)` where exact selectors exist.
- **Outside composition** (`:android:*` notifications, widgets, tiles, workers): `getString(Res.string.x)` and
  `getPluralString(...)` (suspend), after the locale is applied (§13.6).
- **`:ui:words` hand-written logic** (pure, tested):
  - `FailureWords` (exhaustive `when` over `Failure`);
  - `ElapsedWords` (hours and days, never thousands of minutes; B-KIT-033);
  - `ScheduleWords` (B-L10N-014);
  - `PermissionTitleWords` (B-L10N-016);
  - `ModelLabelWords` (B-KIT-057);
  - `NodeRefusalWords` (B-L10N-005);
  - `LanguageCoverage` (generated; B-L10N-017);
  - `SupportedLanguages` (generated from the folder names; B-L10N-008).
- **Rules:** ViewModels hold ids, enums and `Failure` only (§01 §9.1). Kit parts take already-resolved `String`s. The
  kit resolves its own words (Close, Details, Copy, Try again) from `strings_kit.xml`.

### 13.6 Applying the app language (K16)

1. **Source of truth:** `flutter.oc.appLocale`, migrated in place (§01 §9.7); absent means follow the system.
2. **Resolution** (B-L10N-009), in `:ui:words` `LocaleResolution`, pure: walk the device locale list in order and
   take the first whose language is shipped (match by code, region or script, e.g. `zh-Hans-CN` → `zh`); otherwise
   English. **English leads every fallback list** (the Flutter trap `fedd85d8e`, where unmatched languages fell to
   Arabic).
3. **Apply** (`AppLocaleApplier` in `:android:platform`):
   - **API 33+:** `LocaleManager.setApplicationLocales(LocaleList.forLanguageTags(tag))`.
     - Applied on change, and once on the first native launch, so the system's "App languages" page shows the
       migrated choice.
     - At start, `applicationLocales` is read back. If the person changed it in system Settings,
       `oc.appLocale` is updated to match.
   - **API 26–32:** `Locale.setDefault(locale)` in `OcApplication.onCreate`, before the graph is built.
     `MainActivity.attachBaseContext` and each service wrap their context with `createConfigurationContext(config)`.
   - **Compose root:** `:ui:shell` uses CMP's documented `LocalAppLocale` pattern (configuration override, then
     `key(locale) { … }`) so that `stringResource` resolves the chosen language. `KitTheme(localeTag)` provides the
     matching `LocalLayoutDirection`.
   - **After the write is acknowledged** (B-L10N-002): `:android:background` re-posts visible notifications and
     `:android:widgets` re-renders. A refused write keeps the last acknowledged language.
4. **AGP:** `androidResources { generateLocaleConfig = true }` in `:androidApp`, plus `src/main/res/resources.properties`
   with `unqualifiedResLocale=en`. AGP builds the LocaleConfig from the `values-*` folders of the app module and its
   library module dependencies, so it lists all 7 languages only because `:android:os-strings` carries a
   `strings_os.xml` in all 7 folders (§13.2) and `:tools:strings check` enforces that. **No hand-written
   `xml/locales_config.xml`:** Android's docs say the build fails if a LocaleConfig file is created manually while
   `generateLocaleConfig` is on, so §03's `:androidApp` res tree drops that file (§22). `LocaleConfigTest`
   (`:androidApp` unit test) reads the merged manifest's generated config and expects exactly the 7 tags.
5. **Proof (§01 P6):** on an API 33 and an API 30 emulator, switching the language changes a screen and a notification
   at runtime, and Arabic plurals are right. **Fallback:** our own `LocalResourceEnvironment` override, one file per
   language.

### 13.7 Pseudolocales (qa and tests only)

- `:tools:strings pseudo` generates `values-en-rXA/` and `values-ar-rXB/`:
  - `en-XA`: every string accented, about 40 % longer, in brackets;
  - `ar-XB`: English forced right-to-left.
- The output goes into `ui/words/build/generated/pseudo/` and is added through
  `compose.resources { customDirectory(...) }` only when `-Poc.pseudo=true` (the qa build and the capacity gate).
- `PseudoLocaleReleaseTest` fails a release build that contains them.

### 13.8 Checks (`:tools:strings check`; T1 when `:ui:words` changes, always in T2)

1. Every key in a translation exists in `values/` (en), and no orphans exist.
2. Placeholders have the same indices and types in every language.
3. Plurals: every language has `other`, Arabic has all six categories, and each `__eqN` exists wherever the key exists.
4. The XML is well formed and escaped. No raw bidi control characters appear.
5. **Copy rules**, ported from `test/ui_glossary_test.dart` (B-L10N-021…023, -015):
   - glossary words ("conversation", not "session" or "chat", in labels);
   - banned synonyms;
   - no engine or technical words outside Technical and Field keys;
   - contradiction pairs (Working + stopped, Paused + idle, Connected + reconnecting);
   - worktree copy never says "OpenCode";
   - confirm titles end with "?" or "؟";
   - confirm labels are not OK, Yes, Continue or Confirm.

   Confirm keys are collected by Konsist from `KitConfirmSpec(title = Res.string.x, confirmLabel = Res.string.y)`
   call sites.
6. **Every new en key has an ar value** (the G27 port; other languages fall back to English).
7. **No hard-coded copy** (B-L10N-007): Konsist fails a string literal passed to a kit text parameter (`text =`,
   `title =`, `label =`, `body =`, `supporting =`, `placeholder =`, `contentDescription =`) in `:feature:*` or the kit.
   The shrink-only list is `quality/hardcoded_strings_allowlist.txt`.
8. Every tip and tour has en and ar copy (B-L10N-020).
9. Every shipped language folder has a `strings_os.xml` (the LocaleConfig depends on it, §13.6).

---

## 14. How "kit only" is enforced

### 14.1 Gradle (configuration time; §01 §6.3)

- Only `:ui:kit`, `:ui:kit-chat` and `:ui:kit-content` may declare `org.jetbrains.compose.material3:*`.
  `material3-adaptive*` may be declared by `:ui:shell` and the kit.
- `:feature:*` get `compose.runtime`, `compose.ui`, `compose.foundation` (for layout and lazy lists),
  `org.jetbrains.compose.ui:ui-tooling-preview` (screen previews, §15.1) and `:ui:words` (resources). Everything they
  draw comes from `:ui:kit*`.

### 14.2 Konsist rules in `:quality` (the native G16)

```kotlin
// quality/src/test/kotlin/io/github/eslamasabry/oc/quality/KitOnlyRuleTest.kt
class KitOnlyRuleTest {
    private val features = Konsist.scopeFromProject().files
        .filter { it.path.contains("/feature/") && it.path.contains("/src/commonMain/") }

    @Test fun `G16 features import only the allowlist`() =
        features.assertTrue { f -> f.imports.all { FeatureImports.allows(it.name, f) } }

    @Test fun `G16 features call no drawing input or animation modifiers`() =
        features.assertFalse { f -> BannedCalls.regex.containsMatchIn(f.text) }
}
```

[U: exact Konsist 0.18.1 call names; P9 plants violations and proves each one is caught.]

**Allowed imports in `:feature:*`** (from §01 §6.3, with the kit additions):

| Allowed | Notes |
|---|---|
| `androidx.compose.runtime.*` | state, effects, `CompositionLocalProvider` (not `LocalHapticFeedback` and the like) |
| `androidx.compose.foundation.layout.*` | `Box`, `Column`, `Row`, `Spacer`, `padding`, `fillMax*`, `weight`, `offset`, `wrapContent*`, `WindowInsets` modifiers |
| `androidx.compose.foundation.lazy.*` | `LazyColumn`, `LazyRow`, `items`, `rememberLazyListState` (not `animateItem`; see the call ban) |
| `androidx.compose.foundation.{rememberScrollState, verticalScroll, horizontalScroll, ScrollState}` | |
| `androidx.compose.ui.{Modifier, Alignment}`, `ui.unit.*`, `ui.semantics.*`, `ui.focus.*`, `ui.input.key.*` | |
| `androidx.compose.ui.platform.{LocalLayoutDirection, LocalDensity, LocalFocusManager, testTag}` | |
| `androidx.lifecycle.*` (viewmodel, `collectAsStateWithLifecycle`), the Metro ViewModel accessor | |
| `androidx.navigation3.runtime.*` | only in the feature's `Entries.kt` |
| `org.jetbrains.compose.resources.{stringResource, pluralStringResource}` + `io.github.eslamasabry.oc.ui.words.res.*` | |
| `androidx.compose.ui.tooling.preview.{Preview, PreviewParameter, PreviewParameterProvider}` | screen previews (§15.1); the `oc.kmp.feature` convention plugin adds `org.jetbrains.compose.ui:ui-tooling-preview` to `commonMain` (§22 raises the same line for §01 §6.3 rule 1 and §03) |
| `io.github.eslamasabry.oc.ui.kit.preview.*` | `KitPreview`, `KitSamples` |
| `io.github.eslamasabry.oc.ui.{kit,design,words,routes}.*`, engine public APIs, `core.{domain,model,common}`, `kotlinx.*` | |

**Banned, with the part to use instead** (a failure message names it):

| Banned | Use |
|---|---|
| `Text`, `BasicText`, `ClickableText`, `AnnotatedString` styling | `KitText`, `KitWordText`, `KitMarkdown` |
| `Icon`, `Image`, `painterResource`, `vectorResource` | `KitIcon`, `KitImage`, `KitAvatar` |
| `clickable`, `combinedClickable`, `selectable`, `toggleable`, `pointerInput`, `draggable`, `anchoredDraggable`, `indication` | `KitTappable`, `KitRow`, `KitSwipeAction`, `KitSwitchRow` |
| `background`, `border`, `clip`, `shadow`, `graphicsLayer`, `alpha`, `drawBehind`, `drawWithContent`, `blur`, `Canvas` | `KitSurface`, `Modifier.kitDim`, `Modifier.kitFrostSource` (the transcript under the frosted composer), a kit part |
| `animate*AsState`, `Animatable`, `AnimatedVisibility`, `AnimatedContent`, `Crossfade`, `animateContentSize`, `animateItem`, `tween`, `spring`, `updateTransition`, `rememberInfiniteTransition` | `KitReveal`, `KitSwap`, `KitPresence`, `KitEntrance`, `KitAnimatedValue`, `KitAnimatedRows` |
| `Dialog`, `Popup`, `ModalBottomSheet`, any `material3.*` | `KitSheet`, `KitConfirm`, `KitAlert`, `KitMenu` |
| `BasicTextField`, `TextField`, `TextFieldState` | `KitField`, `KitComposer` (`KitTextState`) |
| `LocalHapticFeedback`, `performHapticFeedback` | kit parts haptic themselves |
| `LocalUriHandler`, `openUri`, `Intent.ACTION_VIEW` | `KitLink` / `ExternalLinkUriHandler` via a kit part |
| `LocalContext`, `LocalView`, `ui.viewinterop.*`, `java.*`, `android.*` | `:android:*` services through ViewModels |
| `LocalOverscrollFactory`, `rememberOverscrollEffect` | the defaults (kit only) |

### 14.3 Literal bans (`LiteralBanTest`: text regex over `:feature:*` and kit part files)

| Pattern | Banned in | Use |
|---|---|---|
| `\b\d+(\.\d+)?\.dp\b` except `0.dp` | features; kit part files outside `literal_allowlist.txt` | `KitSpace.sN`, kit tokens |
| `\b\d+(\.\d+)?\.sp\b` | everywhere but `ui/design/type/` | `TypeRole` |
| `Color\(0x`, `Color\.(Red\|Blue\|Green\|Yellow\|Magenta\|Cyan\|Black\|White\|Gray)` | everywhere but `ui/design/color/` | roles |
| `\d+\.milliseconds`, `tween\(\d`, `delay\(\d` in UI code | features and kit parts (MOT-1) | `MotionTokens` |
| width comparisons against a literal (`maxWidth < 600.dp`, `widthDp >= 840`) | everywhere but `ui/design/layout/` | `WindowClass` |
| `String.format`, `System.`, `Thread.` | KMP modules (§01) | — |

### 14.4 The manifest gate (`KitManifestTest`, the G4 port)

For every public `@Composable fun Kit*` in `:ui:kit*` `commonMain`:

1. The KDoc has the lines `States:` (vocabulary `default, loading, empty, error, disabled, working, answered`, plus
   part-specific words declared in `KIT.md`), `Text:` (`frame`, `reading` or `none`), `Cap:` (a `CapRole`), `Motion:`,
   `Hidden:` (gestures + tip id, or `none`) and `Replaces:` (the Dart part, or `new`).
2. At least one `@Preview` per declared state, named `<Part>_<State>Preview`.
3. An entry in `capacity/kit_capacity.json`.
4. A spec in `api/<Part>.md` and a row in `KIT.md`.
5. Icon-only parts require a non-empty label parameter. Parts that can be disabled take `enabled: KitEnabled`
   (§16.2), so a disabled part without a reason does not compile.
6. Its Dart name exists in Dart under the same name, or `kit_renames.tsv` has a row for it (§7.0).

`kit_manifest_allowlist.json` only shrinks.

### 14.5 Rules inside the kit (`KitInternalRulesTest`)

- **Attention (LOOK-4, LOOK-24), by meaning.** Raw `attention`, `attentionFill`, `onAttentionFill`,
  `attentionSurface` and `attentionLine` reads appear only in `KitColors.kt`, `MaterialBridge.kt` and the files on
  `attention_allowlist.txt` (seeded from the 42 Dart kit files that read attention on the freeze tip, mapped to their
  native files; shrink-only). Every other part reaches amber only through `TextTone.Attention`,
  `StatusTone.Attention`, `KitChipTone.Attention`, `KitSheetTone.Attention`, `KitNeedsYou` or the style's
  `NeedsYouLook`, and Konsist allows those APIs only in files whose Dart counterpart uses them. A new amber use needs
  an owner-approved meaning (§2) and an allowlist line in the same PR.
- `RenderEffect` and `blur(` appear only in `AndroidFrost.kt` (switch release; §18.1's Glass trial adds one file behind
  an owner flag). `Modifier.shadow` appears nowhere. `ModalBottomSheet`, `Dialog(` and `AlertDialog` appear nowhere
  (the in-window overlay replaces them).
- `ripple(` appears only in `KitPress.kt`. `performHapticFeedback` and `LocalHapticFeedback` appear only in
  `KitHaptics.kt` and the platform classes.
- Raw `tween(`, `spring(`, `CubicBezierEasing(` and `PathEasing(` appear only in `KitMotion.kt` and
  `KitMotionScheme.kt`. The androidx curve names `EaseOutCubic`, `EaseInCubic`, `EaseInOutQuad` and `EaseOutQuad`
  appear nowhere: they are different curves from Flutter's of the same name (§4.8).
- `require(` and `check(` appear in no `@Composable` kit function: a throw in composition kills the process
  (§01 §9.6). Rules use `kitAssert` or the API shape (§16.2).
- `LocalOverscrollFactory provides` appears nowhere.

### 14.6 When a part is missing (the process every agent follows)

1. **The feature agent never draws it.** It adds a "Kit parts needed" row to its slice contract `contracts/<slice>.md`:
   - the part name;
   - its job in one sentence;
   - its states, `CapRole` and text kind;
   - its hidden gestures;
   - the Dart part it replaces, if any.
2. **The kit owner answers within the slice**, preferring a config option on an existing part (owner 2026-09-29:
   "behaviour lives in the kit's config"). Otherwise it builds a new part with spec, previews, capacity entry and
   tests.
3. **To keep work parallel**, the feature may place `KitPlaceholder("KitFoo")`. In debug it draws a labelled box at
   the declared cap. `KitPlaceholderReleaseTest` fails any release build that still contains one.
4. The coordinator does not merge a slice with a placeholder left in it.

### 14.7 Review checklist (coordinator and the independent visual auditor)

- One primary action per screen.
- ≤ 7 items in the first view.
- ≤ 2 lines per row.
- ≤ 3 text sizes.
- No new bar or row level.
- Actions name their target.
- Obvious options are visible rows.
- No raw error text.
- Technical text only under Details.
- Amber only for an approved needs-you meaning (§2); no red.
- Ripple, stretch, predictive back and haptics are native.
- 150–250 ms motion, still under Off.
- Hard caps hold at 1,600 dp.
- Frame text ≤ 1.3× at 200 %.
- Arabic RTL capture checked.
- Every hidden gesture has a tip and a visible path.
- Judged side by side with the picked concept or the Flutter screen (owner rule `judge-visuals-hard`).

---

## 15. Previews and the catalog (Widgetbook's replacement)

### 15.1 Previews

- Annotation: `androidx.compose.ui.tooling.preview.Preview` from CMP `org.jetbrains.compose.ui:ui-tooling-preview`
  1.13.0-beta01 [V Maven Central], written in `commonMain`. Android Studio renders common previews.
- Wrapper: `KitPreview(appearance = KitAppearance.Default, locale = "en", widthDp = 412) { … }`, a public composable in
  `:ui:kit` package `…ui.kit.preview`. It applies `KitTheme` with `PreviewKitPlatform` (fixed signals, no side
  effects). `KitSamples` in the same package holds realistic copy and fake models for previews.
- **Variants without editing previews.** `KitPreview` first reads `LocalKitPreviewOverride` (`KitPreviewOverride(
  appearance, locale, textScale, widthDp, signals)`); when it is set, its values replace the preview's own arguments.
  The capture harness (§15.3) and the desktop gallery (§15.4) provide it, which is how one preview renders dark + ar,
  other Styles, Saver, high contrast and text 2.0. Without the local (Android Studio), the preview's own arguments
  apply. (ComposablePreviewScanner 0.9's `@PreviewWrapper` is the alternative if the local proves awkward; it works
  automatically with the Android scanner.)
- **Every part:** one preview per declared state (`KitRow_DisabledPreview`) plus `_LongContentPreview`, which uses the
  longest realistic content.
- **Every feature screen:** previews of `XScreen(state = canned XUi, actions = FakeXActions)` for loading, empty,
  typical, many or long, needs setup or needs you, and error or offline (screen-standard §6), as far as the screen has
  them. These are §01 §9.1's fake-state previews.

### 15.2 Capacity scenes

The capacity harness needs the stress fixtures, which live in the test-only `:ui:testing`. Each part therefore also
has `<Part>CapacityScene(text: StressText, items: Int)` in its module's test source set. It is written by the part
author and called by `KitCapacityTest` (§8.3).

### 15.3 Capture matrix (Roborazzi 1.77.0, recorded and verified on the VPS only)

| What | Variants | Size |
|---|---|---|
| Every part preview | light + en; dark + ar (RTL) — Solid, Graphite, text 1.0 | 412×915 at density 2.625 (420 dpi) |
| `_Default` of the 20 frozen parts (§18.2) | + dark + en; light + ar | 412×915 |
| `_Default` and `_LongContent` of every part | + text 2.0 (en, light); + 1280×800 (en, light) | |
| `_Default` of the style-sensitive parts (surfaces, rows, buttons, chips, fields, segmented, switch, sheet, confirm, request card, composer, message, status line) | + Soft, Brutal, Paper, Signal × light/dark (en); **+ Saver; + high contrast × the 5 styles (dark, en)** | 412×915 |
| Every feature screen preview | light + en; dark + ar; long state also at text 2.0 | 412×915 |
| Daily-path screens (Conversations, chat, request, Settings, Appearance) | **+ Saver; + high contrast; + Soft, Brutal, Paper, Signal × light/dark; + the owner's own look (light, Midnight pack, blue accent; memory `design-schools-direction`)** | 412×915 |
| Adaptive parts (sheet, confirm, dialog, undo, top bar, diff) | + 915×412, 800×1280, 1600×1000 | |

These rows follow the owner rule `independent-visual-auditor` (every style × light/dark, plus Saver, high contrast and
RTL, after an app-wide look change). Estimate at the switch: about 330 parts → about 3,000 kit images, plus about 900
screen images. The Flutter suite held 4,937 goldens [R testing-perf].

Harness: `KitPreviewCaptureTest` (each kit module's `androidHostTest`; `@GraphicsMode(NATIVE)`; JDK 21) scans with
ComposablePreviewScanner 0.9.3's **Android** scanner:
`AndroidComposablePreviewScanner().scanPackageTrees("io.github.eslamasabry.oc.ui.kit")` [V version; U exact API].
The previews use `androidx.compose.ui.tooling.preview.Preview` (§15.1), and the scanner's README says those are found
by the Android scanner, including in `commonMain` packages. The `:common` and `:jvm` scanner modules
(`CommonComposablePreviewScanner`) scan the deprecated JetBrains `@Preview` and are removed in 0.10.0, so they are not
used. The harness provides `LocalKitPreviewOverride` for each variant (§15.1) and calls
`captureRoboImage("src/screenshots/<Part>/<Preview>-<variant>.png")`.

- Tasks: record and verify through the Roborazzi plugin. The exact task names depend on the AGP-KMP variant naming
  [U P7].
- Every Roborazzi bump is a separate re-record commit.
- Goldens are never recorded on the PC (§01).

### 15.4 Desktop gallery (`:desktopApp`, for agents; exists from slice 0)

```bash
./gradlew :desktopApp:run                                   # interactive (PC, optional)
./gradlew :desktopApp:run --args="--export build/gallery --part KitRow --locale ar --scale 2.0 --width 412"   # headless PNGs (VPS; xvfb-run fallback)
```

- It reads a **registry of preview functions generated at build time**: a small Gradle task in `:desktopApp`
  (`generatePreviewRegistry`) lists every `@Preview` function in the kit modules' `commonMain` (a source scan, or a
  KSP processor if the scan proves fragile) and writes `PreviewRegistry.kt` with one entry per preview. The
  deprecated ComposablePreviewScanner `:jvm` artifact is not used (removed in 0.10.0). The gallery provides
  `LocalKitPreviewOverride` from its toolbar.
- Toolbar: pack, mode, style, corners, spacing, locale (the 7 languages + pseudo), text scale (1.0 / 1.3 / 2.0), width
  presets (280 / 412 / 600 / 840 / 1280 / 1600), and an RTL toggle.
- Desktop rendering is Skia, not Android. The gallery is **never** used for visual sign-off, which stays on Android
  renders: Roborazzi and emulator captures (§01 row 16).

### 15.5 Screen-standard checks (the `[auto]` rules of `docs/ux-system/screen-standard.md`)

`ScreenStandardCheck` in `:ui:testing` runs on every feature-screen preview capture, in en and ar at text 1.0 and 2.0.
It reads the kit's test-visible semantics (`KitSemantics.kt`) and measures text through `textFitOf(node)`, which calls
each text node's `SemanticsActions.GetTextLayoutResult` (§8.3):

| Rule | How it is measured |
|---|---|
| At most one main action, visible without scrolling | count of nodes with `KitSemantics.PrimaryAction` = 1, inside the viewport |
| First view ≤ 7 items | nodes whose `KitPart` ∈ {KitRow, KitFeedItem, KitSwitchRow, KitExpandRow, card parts} intersecting the viewport ≤ 7 |
| A row ≤ 2 lines of text | sum of measured line counts (`textFitOf`) inside each `KitRow` ≤ 2 at text 1.0 |
| ≤ 3 text sizes | distinct `KitSemantics.TypeRole` values with on-screen text ≤ 3 |
| Nothing cut off | no text node lost words (measured overflow) without its declared `KitTextFit` rule |
| No red | already guaranteed by `NoRedScanTest`; nothing to measure |

- Results go to `status.json` (§15.6).
- A new screen must pass. Screens ported with a known violation go on `standard_baseline.json`, which only shrinks.

### 15.6 Owner-facing catalog (the Widgetbook successor)

- **Build:** `tool/catalog/native_catalog.sh` runs on the VPS after each merge to the integration branch, not on a
  timer, to save VPS time. It records captures into a temporary folder (not the goldens) and writes:
  - `index.html`: static and phone-friendly. **One full screen per image, one sentence each** (owner rule). It filters
    by part, screen, state and variant, and shows before/after pairs when a branch differs from the integration
    branch.
  - `status.json`: per part, whether it has a preview, captures, a capacity entry and a spec; the screen-standard
    results; and catalog-row coverage.
- **Serve:**
  - The script rsyncs the output to the PC at `/home/eslam/Storage2/catalog/native/current`.
  - A systemd user unit `oc-native-catalog-serve.service` serves it on `<PC tailnet address>:8767` (Tailscale only).
  - At the switch it moves to `:8766`, and Widgetbook (`oc-catalog-serve.service`, `oc-catalog-rebuild.timer`)
    retires.
- **Solid pixel parity (judge visuals hard).** For the 20 frozen parts and the daily-path screens, the script also
  places the Flutter golden (`test/goldens/kit/…` on the freeze tip: **412×915 pixels, DPR 1.0**) next to a native
  capture recorded for parity with Robolectric qualifiers `w412dp-h915dp-mdpi` (density 1.0), so the two match pixel
  for pixel with no resampling, plus a diff overlay. The normal catalog stays at 420 dpi. This is **a review list, not
  a gate**: the renderers differ in hinting and ripple. The independent auditor marks each difference "intended" or
  "bug" in `docs/qa/kit-parity-<date>/README.md`.

---

## 16. Writing a kit part (template and definition of done)

### 16.1 Signature rules

- Order:
  1. required data;
  2. `modifier: Modifier = Modifier`;
  3. optional data;
  4. state flags (`enabled: KitEnabled`, `working`);
  5. callbacks;
  6. a trailing content slot.
- Parameter types: plain Kotlin types, kit or design types, `@Immutable` models from `:core:model`, `Failure`,
  `Redacted`, `KitTextState`. **Never** a ViewModel, a `Flow`, an engine type or a protocol DTO.
- Text parameters take resolved `String`s (callers use `stringResource`). Kit-internal words come from
  `strings_kit.xml`.
- Test tags: kit-internal tags equal the Dart `Key` strings (`chat-send-button`, `chat-composer-field`,
  `kit-chip-remove`, …). Features pass their own tags through `modifier.testTag("…")`, but only where Flutter had a
  `ValueKey` (§01 §9.1).
- No `CompositionLocal` reads outside the kit's own locals. Animated values are read in the draw phase (motion-native
  §9.2).
- Lists: plain `List<T>`. Preserve instances in the caller. `compose-stability.conf` marks the model and design
  packages stable (§01 §9.3).
- **Never throw from composition.** §01 §9.6: an exception in composition kills the process, because Compose has no
  error widget. The Dart kit states its rules as about 160 `assert(...)` calls that run in debug only; Kotlin
  `commonMain` has no debug-only assert, and `require`/`check` throw in release too. So:
  - where the type system can carry the rule, it does (`KitEnabled` below; a required `menuTwin`; a non-empty label
    type for icon-only parts);
  - every other rule uses the kit-internal `kitAssert(condition) { message }` (`foundation/KitAssert.kt`): it throws
    only when `LocalKitPlatform.current.isDebug` (debug, test and preview builds) and logs once through `AppLog` in
    release;
  - `KitInternalRulesTest` bans `require(` and `check(` inside `@Composable` kit functions (§14.5).

### 16.2 Template

```kotlin
/** Enabled, or disabled with the reason a person hears once (A11Y: a disabled control says why). */
@Immutable sealed interface KitEnabled {
    data object Enabled : KitEnabled
    data class Disabled(val reason: String) : KitEnabled
}

/**
 * A tappable line in a list: what it is, one supporting line, and its value or chevron.
 *
 * States: default, disabled, working
 * Text: reading
 * Cap: List
 * Motion: pressed fill shows ≥ PRESS_HOLD; trailing value cross-fades (QUICK); Calm = fade only; Off = instant
 * Hidden: long-press opens [menu] (tip: none; menu items are also accessibility actions)
 * Replaces: KitRow (lib/ui/kit/kit_row.dart)
 */
@Composable
fun KitRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: KitRowLeading? = null,
    trailing: KitRowTrailing = KitRowTrailing.None,
    enabled: KitEnabled = KitEnabled.Enabled,       // a disabled row cannot be built without its reason
    menu: List<KitMenuItem> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    kitAssert(title.isNotBlank()) { "KitRow: a row needs a title" }  // throws in debug/test/preview only; logs in release
    KitTappable(
        onClick = onClick, menu = menu, enabled = enabled,
        role = KitTappableRole.Button,
        modifier = modifier.kitMaxWidth(CapRole.List).semantics(mergeDescendants = true) { kitPart("KitRow") },
    ) { /* Row: leading (40 dp lead column), texts (KitText RowTitle / Secondary), trailing */ }
}
```

### 16.3 Definition of done for one part (all in the same PR)

1. The part file, its KDoc lines (§14.4), the `api/<Part>.md` spec (ported from `docs/ux-system/kit-api/<Part>.md`
   where one exists; native changes marked "Native:") and a `KIT.md` row.
2. A preview per state, plus `_LongContentPreview`.
3. A capacity contract entry and a capacity scene, passing at 280, 412 and 1,600 dp, text 1.0, 1.3 and 2.0, LTR and
   RTL (plus the G6 sizes for adaptive and row parts, §8.3).
4. Roborazzi captures recorded on the VPS (§15.3 matrix).
5. A logic test for every catalog row it carries (§17), named after the row.
6. Accessibility: role, labels, disabled reason, state, live regions and custom actions (§11.1);
   `AccessibilityRulesTest` passes.
7. Reduced motion: under `MotionLevel.Off` and `MotionDurationScale(0f)` the part is idle after one frame.
8. RTL: directional APIs only; painters mirror; the Arabic capture has been looked at.
9. Text kind: frame parts wrap their text in `KitFrameText`.
10. A `JETPACK_AHEAD.md` row if it uses an Android-only API newer than CMP's mapping.
11. Formatting applied to changed files only (owner `agent-format-churn`).
12. One full emulator-screen image + one sentence attached to the PR when the part appears on a daily-path screen.

---

## 17. Catalog rows → native tests (the logic tests now; widget tests later)

Tests are named after the row (`` `B-KIT-038 soft corners keep about 60 percent`() ``) so the §01 ratchet counts them.
Module codes as in §7.0; **W** = `:ui:words`; **T** = `:tools:strings`; **Q** = `:quality`; **E:x** = an engine.

| Rows | Native test class | Mod | Level |
|---|---|---|---|
| B-KIT-003, -009, -061, -062 | `RolesFloorsTest`, `AccentTest`, `PacksTest`, `RoleDerivationTest` | D | unit |
| B-KIT-004, -005 | `ElevationTokensTest`, `ShapeTokensTest` + `KitThemeCaptureTest` | D + K | unit + screenshot |
| B-KIT-008 | `SaverTest` (D) + `SaverPrefsTest` (E:settings) | D, E | unit |
| B-KIT-017, -037, -038, -039, -041, -046, -048 | `FineTuneTest`, `StyleSetTest`, `KnobMathsTest`, `HighContrastStyleTest`, `StyleContrastTest`, `StyleTransitionTest`, `StyleHooksTest` | D | unit |
| B-KIT-042, -043, -044, -045 | `SignalCaptureTest`, `BrutalCaptureTest`, `PaperCaptureTest`, `SoftCaptureTest` | K | screenshot |
| B-KIT-002, -006, -040, -047, -059 (storage half) | `LiveHeldByStyleTest`, `MotionPrefMigrationTest`, `StyleResetsKnobsTest`, `LivePolicyWriteTest`, `TextSizePrefTest` | E:settings | unit / migration |
| B-KIT-018, -020 | `FrostPolicyTest` (incl. `blurRadiusPx`), `LivePolicyTest` | D | unit |
| B-KIT-052, -059 (render half), -060 | `TypeScaleTest` (D); `TextScaleCapTest`, `KitCapacityTest` (K) | D + K | unit + screenshot |
| B-KIT-023, -054 | `KitMotionPartsTest`, `ReducedMotionSettlesTest`, `KitTransitionsTest` (+ S `PredictiveBackPolicyTest`) | K, S | integration |
| B-KIT-001 | `GlyphMirrorCaptureTest` | K | screenshot |
| B-KIT-007, -010 | `KitThemeCaptureTest` | K | screenshot |
| B-KIT-011, -014, -015, -016, -019, -027, -029, -031, -032, -049, -050, -051 | `KitButtonTest`, `KitChoiceListTest`, `KitConfirmTest`, `KitDialogTest`, `KitIconButtonTest`, `KitRowPartsTest`, `KitScreenLayoutTest`, `KitSegmentedTest`, `KitSheetTest`, `KitSwatchTest`, `KitTabSwitcherTest`, `KitTappableTest` | K | screenshot / semantics |
| B-KIT-012 | `LiteralBanTest` + `KitSkeletonCaptureTest` | Q + K | unit |
| B-KIT-013 | `CapabilityExplainerTest` (registry equals `capabilities.json`) | K | unit |
| B-KIT-022 | `MenuLayoutTest` | K | unit |
| B-KIT-024, -025, -030, -034, -035 | `NoticeClassifierTest`, `StagedProgressTest`, `SearchThresholdTest`, `StateViewEscalationTest`, `StatusLineTest` (+ E:attention combiner) | K | unit |
| B-KIT-021, -026 | `LogBufferTest`, `QrPayloadTest` | KN | unit |
| B-KIT-036 | `StepLightCaptureTest` | KC | screenshot |
| B-KIT-033, -057 | `ElapsedWordsTest`, `ModelLabelWordsTest` | W | unit |
| B-KIT-053 | `KitThemeDefaultsTest` (missing appearance → defaults) | K | unit |
| B-KIT-055 | `TerminalCursorTest` (KN); its scene half waits for D11 | KN | screenshot |
| B-KIT-056 | `NoRedScanTest` (two exemptions) | Q | unit |
| B-KIT-028, B-KIT-058 | parked with D11 / D6 (B-KIT-058 is about AI Team states and `teamContextTone`, including its "high context in primary text" half) | — | screenshot |
| (no catalog row; Dart `test/kit/kit_context_ring_test.dart`) | `KitContextRingTest`: accent < 70 %, attention 70–95 %, text1 ≥ 95 %, never red; halo on crossing 70 % and 95 % upward | KC | unit + screenshot |
| G12 port (`test/redaction_test.dart`; SEC-2, SEC-4) | `KitRedactionGateTest`: register known secrets with the `Redactor`, render every technical part (code block, log panel, terminal output, diff, viewer + find, tool row, background work line, field, notice, details fold) and run every copy path; assert only the masked form appears on screen and on the clipboard. Plus `KitCopyTest` (masked by default; the named `redact = false` call sites) | K, KC, KN | screenshot / semantics |
| (no catalog row; Dart `kit_max_width.dart`) | `KitMaxWidthTest` (§8.2) | K | unit (desktop compose) |
| B-KIT-032 "closes when answered elsewhere" half | the owning screen's ViewModel test (request answered elsewhere → `visible = false` + receipt) | F | unit |
| B-L10N-001, -020 | `LocaleChoiceTest`, `TeachingRegistryTest` | E:settings | unit |
| B-L10N-002, -013 | `AppLocaleApplierTest` (`:android:platform`), `NativeTextLocaleTest` (`:android:background`, `:android:widgets`) | android | integration / contract |
| B-L10N-003, -004, -005, -008, -009, -010 (runtime), -014, -016, -017, -019, -024 | `FolderFactsWordsTest`, `UntitledWordsTest`, `NodeRefusalWordsTest`, `SupportedLanguagesTest`, `LocaleResolutionTest`, `PluralRulesTest`, `ScheduleWordsTest`, `PermissionTitleWordsTest`, `LanguageCoverageTest`, `SetupErrorWordsTest`, `VoiceManifestWordsTest` | W | unit |
| B-L10N-006 | `BidiTest` | D | unit |
| B-L10N-007 | `HardcodedStringTest` | Q | unit |
| B-L10N-010 (completeness), -011, -015, -021, -022, -023 | `DriftCheckTest`, `GlossaryCheckTest`, `ContradictionPairTest` | T | unit |
| B-L10N-012 | `KitNumberFormatTest` (Android platform formatter, ar and en) | K (androidHostTest) | unit |
| B-L10N-018 | `CapabilityWordsTest` (W) + a screen capture at 320 dp and 2.5× (feature) | W + F | screenshot |

---

## 18. Work packages, order, owners, acceptance

### 18.1 Packages

Agent-week estimates are first guesses, based on about 75k Dart lines in scope (out of 106.9k, minus D6 and D11 parts)
at a Compose ratio of about 0.6. **They are replaced by the measured P10 throughput at M0** [U].

| WP | Slice | Owner (model) | Contents | Depends on | Estimate | Acceptance |
|---|---|---|---|---|---|---|
| K0 | 0 | kit owner (Opus) | `:ui:design` skeleton: `Roles` + Graphite + `Contrast`, type scale, `Tokens`, `Shape`, `MotionTokens` + `MotionPolicy`, `WindowClass`, `CapRole`, `TextScalePolicy`. `:ui:kit`: `KitTheme`, `KitPlatform` (Android, Desktop, Fake, Preview), `KitText`, `KitIcon` + the glyph generator, `KitSurface` (Solid), `KitTappable` + ripple + haptics, `KitFrameText`, `KitMaxWidth`, `kitAssert`, `KitSheet` on the in-window `BottomSheet`, the frost spike (`KitFrostState` + `kitFrostSource` + `kitFrost`). `:ui:testing` harness, the Roborazzi VPS setup, the `:desktopApp` gallery skeleton | §01 P1 | 2–3 aw | §01 **P7** passes: one part tested three ways (desktop compose test, Robolectric, Roborazzi light/dark en/ar); the frame cap at 200 % keeps non-linear scaling below the cap; ripple and haptic on the emulator; frost side by side with the Flutter frost golden; the 20 signatures (§18.2) frozen in `contracts/kit-v1.md`; `GlyphProvenanceTest` and `KitMaxWidthTest` green |
| W0 | 0 | l10n owner (Sonnet) | the **P6 spike**: `:tools:strings convert` on 2–3 feature files (one of them a `part of` file) from the freeze tip, en + ar; `:ui:words` with those files; `:android:os-strings` with en + ar; CMP plural selection in `ar` (zero…other for 0, 1, 2, 3, 11, 100); `\'` and `\uXXXX` unescaping; locale switch of a screen and a service-posted notification on API 33 and API 30 | §01 P1 | 0.5–1 aw | §01 **P6** |
| W1 | 1 | l10n owner (Sonnet) | the full conversion: `:tools:strings` convert + check + pseudo; `feature-routes.tsv`; all 7 languages from the freeze tip at its recorded SHA, after the last Flutter merge; `strings_os` for all 7 in `:android:os-strings`; generated LocaleConfig; `PluralsGenerated`; `SupportedLanguages`; `LanguageCoverage`; `AppLocaleApplier` (with the `:android:platform` owner) | W0, K0 | 1.5–2 aw | `convert-report.md` reviewed (unused share ≤ 2 %, unused keys listed for the owner); `check` green; `LocaleConfigTest` lists 7 languages; screen + notification switch language at runtime on API 33 and 30 |
| K1 | 1 | kit owner + 1 kit agent (Sonnet) under the owner | the 31 packs + accents + dynamic colour; frames (`KitScreen`, top bar, status line and slot, nav rail and sidebar, page heading, system bars); the row family; buttons, action block, icon button, chips; `KitField` (all kinds); segmented, choice list, switch and expand rows; modals (sheet, confirm, dialog, menu, undo, details fold); states (state view, notice, loading bar, skeleton, progress, receipt, needs-you); motion parts; capacity, manifest, Konsist and literal gates live | K0 | 6–7 aw | every part meets §16.3; gates green with empty or shrink-only allowlists; pixel-parity review list for the 20 parts done by the auditor |
| KC2 | 2 | chat-kit owner (Opus for turn, transcript and markdown; Sonnet for the rest) | `KitMessage` + lift, `KitTurn` family, `KitToolRow`, `KitWorkLine`, `KitQueuedMessage`, `KitMarkdown` blocks + `KitStreamCaret` + `KitFindMark`, `KitRequestCard` + sheet + `FormSpec` renderer, `KitComposer` v1 (field, Send/Stop, chips row, delivery, frost), `KitStartBox`, transcript excerpt and skeleton | K1; `:core:markdown` | 5–6 aw | the §01 **P3** transcript benchmark; chat parts meet §16.3; the composer rides the IME frame by frame on the emulator |
| K2 | 2 | kit agent (Sonnet) | tip, refresh, jump pill, tab strip and switcher, search field, filter chips, scroll fade, last known, server trust, top-bar pill, scope, link, typing guard, link scope, scroll area | K1 | 2 aw | §16.3 |
| KN3 | 3 | content-kit owner (Sonnet) | code block + `CodeTokenizer` (SyntaxMP benchmark), log panel + buffer, image + avatar painter, QR (qrose), scanner slot (CameraX + zxing-cpp), terminal output view, capability explainer, checklist, progress row and ring, context ring and chart, agent cards (KC), task chip and sheet, project file list | K1 | 4 aw | §16.3; QR scan works on the emulator camera; no ML Kit in the dependency tree (`dependencyInsight`) |
| K4 | 4 | kit owner (Opus for the four styles) | Soft, Brutal, Paper, Signal specs + painters + captures; appearance parts (guide, option card, swatches, accent row, fine-tune, step slider, carousel); tour; date and time pickers on M3; wheel; widget panel parts; arrival | K1 | 4–5 aw | `StyleContrastTest` over every style × knob × pack × brightness; every style capture reviewed light and dark; the owner sees one screen per style per mode |
| KN5 | 5 | content-kit owner (Sonnet) | diff view + page, viewer, gallery, markup, zoom, live terminal slot + key bar + OSC 52 + links, web preview slot, charts and tables, session tabs, breadcrumb, mic hold, level meter | KN3 | 4 aw | §16.3; the terminal renders through the `PlatformViewHandle` seam (§01 P9) |
| — | after switch | — | live backgrounds (D11), scenes (D11), AI Team parts + work graph (D6), fact row (D6) | owner decision | — | — |
| G1 | after switch | kit owner (Opus, render-compare loop) | **The owner-gated Glass trial** (owner answer "A", 2026-11-01, memory `no-glass-rule`): one optional sixth Style, Glass: a `RenderEffect` blur plus AGSL refraction on API 33+, on the **top bar and sheets only**, solid fallback below API 33 and under Saver, motion Off and high contrast. It adds one allowlisted file (for example `AndroidGlass.kt`) behind an owner flag, its own contrast floors and Saver/high-contrast holds in `StyleContrastTest`, and uses real blur: `RenderEffect`, directly or through Haze (12 N-98, version pinned at that slice [U]; adopting it narrows 12 X-21 in the same commit), with refraction only through the platform AGSL `RuntimeShader` inside the kit. It starts only **after the switch** (15 §12); until then the only blur in the app is the two frost places (composer, chat header). The owner judges it on the phone, as they judged the demo; it ships only if they like it, otherwise the flag and file are deleted | the switch release; owner go | 1–2 aw | owner yes on the phone; emulator frame timing within the §13 budget; every gate stays green with the flag off |

Total to the switch: about 30–34 agent-weeks of kit and words work [U, re-planned at M0]. The Glass trial (G1) is
after the switch and not in this total.

### 18.2 The 20 signatures frozen at the end of slice 0 (§01 §10.3)

1. `KitTheme`
2. `KitText`
3. `KitIcon`
4. `KitSurface`
5. `KitTappable`
6. `KitDivider`
7. `KitScreen`
8. `KitTopBar`
9. `KitStatusLine`
10. `KitLoadingBar`
11. `KitRow` (with `KitRowLeading` / `KitRowTrailing`)
12. `KitRowGroup`
13. `KitButton` + `KitAction`
14. `KitActionBlock`
15. `KitIconButton`
16. `KitChip`
17. `KitField`
18. `KitSheet`
19. `KitConfirm`
20. `KitStateView`

The types they take are frozen with them: `KitPlatform` (with `isDebug`, `frameDensity` and `frostEffect`),
`KitSystemSignals` (with `accessibleNavigation`), `KitAppearance`, `Tokens`, `CapRole`, `TypeRole`, `TextTone`,
`KitHaptic`, `MotionLevel`, `KitEnabled`, `Failure`/`Redacted` usage, `KitMenuItem`, `KitTextState`. The frost
signatures freeze with them although frost is built in slice 2: `KitFrostState`, `Modifier.kitFrostSource(state)`
and `Modifier.kitFrost(role, state)`, because the chat feature places the source. `KitNotice` and
`KitDetailsFold` freeze in the first week of slice 1.

### 18.3 Machine rules for kit work

- Agents run module-scoped tasks only: `:ui:kit:desktopTest`, `:ui:kit:testAndroidHostTest --tests …`, or Roborazzi
  verify for one part. Never `build`.
- Roborazzi record and verify runs on the VPS only.
- From midnight to 2 am, VPS only (owner).
- Reviewers never launch test processes.

---

## 19. Owner questions (each has a default; work proceeds on the default)

| # | Question | Default |
|---|---|---|
| Q1 | The brief said 8 languages; the app ships 7 (en, ar, es, ja, pt, ru, zh). Is an 8th wanted, and which? | Ship the 7. Adding one is a folder + a picker row. |
| Q2 | Per-part kit gates (capacity, Roborazzi, accessibility checks) are built with each part now. Do they count as the "widget tests come later" work? | Build them now: they are the kit's definition of done and cost little per part. If not: keep only the logic gates (contrast, tokens, policies, menu order) + one Roborazzi capture per part, and run the capacity gate in the widget-test phase. |
| Q3 | Date and time pickers become Android's own Material pickers inside kit chrome (K10), replacing the custom picker. | Yes |
| Q4 | Replies and prompts take their direction from their own words, so an English reply in the Arabic UI reads left to right (K17). Flutter aligned them to the UI direction. | Content direction |
| Q5 | The tab lens's springs overshoot slightly today (head 5.6 %, lift while dragged 8.3 %). Keep them, or make them flat like everything new? (The sent bubble has no spring; it is a 250 ms ease.) | Keep (parity); check side by side on the emulator |
| Q6 | A haptic tick when a swipe action crosses its threshold (Android `GestureThresholdActivate`). | No (not in today's haptic list) |
| Q7 | Selecting text in replies: allow Android's own extra menu items (Translate, Share), or keep only Copy and Select all? | Keep Copy and Select all (parity), through `ComposeFoundationFlags.isSmartSelectionEnabled = false` or a kit text toolbar (§7.1) |
| Q8 | Foldables: hinge-aware split layouts. | No; window width only |
| Q9 | Arabic digits: keep Western digits as today, or use Arabic-Indic digits? | Western digits, as today (`nu-latn`, §12.1 rule 8) |
| Q10 | The 1.3× frame cap covers what Flutter caps today (top bar, chips, buttons, composer field, status strip, session tabs, sheet title). Should it also cover the undo bar, confirm and alert titles, the start box, the ask field, breadcrumbs, the jump pill, segmented labels and tab strips? Capping them keeps shapes but makes those words smaller for people who need large text. | No: keep Flutter's list. Any extension is shown to the owner first as a 200 % capture |
| Q11 | A Vibration on/off row in Settings › Appearance (today there is none; the phone's Touch feedback setting decides). | No row at the switch; if wanted, one device-wide `oc.<what>` key |

§01 already decides frost (D10, keep, two places), live backgrounds and scenes (D11, after the switch or drop), and
AI Team / FactsBook (D6, after the switch). This section follows those defaults. The owner already answered the
post-switch Glass trial ("A", 2026-11-01): it is WP G1 (§18.1), not a question.

---

## 20. Risks and their controls

| Risk | Control |
|---|---|
| M3 1.5 beta API churn (Slider, `BottomSheet`, pickers) | Only kit modules see M3, and only behind kit names. One owner absorbs the churn. D12 allows a beta M3 only inside the kit. |
| The common frame-text density does not keep Android's non-linear curve | **Settled by P7**: the custom-`toDp` design failed (20.2 dp, not 20.8); the per-size `frameDp` path matches Flutter exactly (§9.2). Remaining risk: the emulator side-by-side. |
| CMP resources ignore the per-app locale outside composition [U] | P6 proves it. Fallback: our own `LocalResourceEnvironment` and an explicit environment for `getString`. |
| The in-window `BottomSheet` is experimental, or has accessibility gaps | `KitSheet` hides it. Fallback: `ModalBottomSheet` behind the same API (motion-native §3.4). |
| Glyph extraction does not match the font rendering | `GlyphParityTest`. Fallback: ship the TTFs and draw glyph text (§4.11). |
| The Robolectric capacity gate is slow (Flutter needed about 22 min in 8 shards) | Shard on the VPS. Agents use the desktop width-only twin at T0. Run only touched parts' scenes at T1, all at T2. |
| Solid pixel drift between Flutter and Compose renderers | Auditor parity list (§15.6). Hold the exact tokens; only renderer-level differences are accepted. |
| About 330 parts is a lot | WP order follows the slices; parts land only when a slice needs them; D6 and D11 parts wait. |
| Key routing for 9,837 strings mis-files or drops keys | No import filter, so `part of` files count (§13.3); an unused share above 2 % fails the run; an unrouted path fails the converter; the report goes to the l10n owner; feature agents can move keys between their own files later. |
| The conversion runs on a stale Flutter snapshot | The converter takes `--source-sha` and refuses a worktree at another commit; the SHA is re-recorded at M0 and the run happens after the last Flutter merge. |
| Frost cannot be built in-kit on API 31+ | Solid surface (frost is optional chrome). No Haze: §12 X-21. |
| Konsist rules have false positives or holes | P9 plants violations; every allowlist only shrinks; the review checklist (§14.7) backs it up. |
| Desktop Linux has no screen reader | Accepted (§01 D14). Android TalkBack is the accessibility truth. |

---

## 21. Sources

- **Binding:** `../plan/01-architecture-decision.md` (§5, §6.3, §7, §8, §9.1–§9.4, §10.3, §11, §12).
- **Maps:**
  - `../map/ui-kit.md`: UI-KIT-01…58, §1 owner rules, K1–K7, Appendix A (every Dart kit file).
  - `../map/screens-nav.md`: §1, §5 (adaptive, desktop layer), §9 (the locale fallback trap).
- **Research:**
  - `../research/motion-native.md`: §0, §2, §3.4, §4.3–§4.5, §5–§9.
  - `../research/kmp-cmp-status.md`: §3, §6, §12.4.
  - `../research/testing-perf.md`: §4, §5.
- **Catalog:** `../catalog/BEHAVIORS.md`: KIT (B-KIT-001…062), L10N (B-L10N-001…024), XPLAT-009/010/011.
- **Repo (read-only)**, the freeze tip `int/freeze-2026-11-01` at `fc5c1e78f`
  (`/home/eslam/Storage/Code/wt/oc_app-freeze`; it contains batch1 `65e3f70a3`, `/home/eslam/Storage/Code/wt/oc_app-batch1`):
  - `lib/ui/kit/**` (incl. `kit_chrome.dart`, `kit_update_dot.dart`, `kit_update_sheet.dart`, `kit_vitals_strip.dart`,
    `kit_paste_image_menu.dart`, `chat/kit_markdown_unfinished.dart`, `kit_max_width.dart`, `kit_icon.dart`,
    `motion/kit_haptics.dart`, `kit_copy.dart`, `kit_frost.dart`), `lib/ui/theme_roles.dart` (`schemeFromRoles`),
    `theme_packs*.dart`, `app_iconography.dart`, `lib/state/glass_choice.dart`,
    `lib/ui/screens/settings/glass_choice_control.dart`, `lib/ui/teaching/teaching.dart`;
  - `test/kit/kit_overflow_matrix.dart` (G6), `test/redaction_test.dart` (G12), `test/kit/kit_style_test.dart`,
    `test/kit/kit_context_ring_test.dart`, `test/goldens/kit/` (412×915 px, DPR 1);
  - `lib/state/app_locale.dart`, `lib/l10n/app_*.arb`, `l10n.yaml`;
  - `assets/fonts/{geist,phosphor}/`, `assets/marks/`;
  - `android/app/src/main/res/values*/`;
  - `docs/ux-system/kit-v2.md` §8–§9, `screen-standard.md`, `docs/ux-system/kit-api/*.md` (111 files);
  - `test/kit/kit_capacity*.json`, `test/support/stress_content.dart`.
- **Demo:** `/home/eslam/Storage/Code/kmp-demo/shared/src/commonMain/kotlin/io/github/eslamasabry/kmpdemo/ui/{Theme,Kit,Icons}.kt`.
  These are lessons, not code to copy: sampled palette, `danger` red, bouncy `pressScale`, icons that ignore text
  scale.
- **Owner memory:** `design-standard-rule`, `component-max-width-rule`, `text-scale-cap`, `android-native-feel`,
  `no-glass-rule`, `design-schools-direction`, `micro-animations-wanted`, `teach-what-is-hidden`,
  `judge-visuals-hard`, `widgetbook-catalog`, `freeze-owner-decisions-2026-11-01`, `dev-freeze-native-rewrite-2026-11-01`,
  `agent-format-churn`, `independent-visual-auditor`.
- **Checked today [V], 2026-11-01:**
  - Maven Central `maven-metadata.xml`:
    - `dev.chrisbanes.haze:haze` latest **2.0.1** (2026-09-29); not used (§12 X-21 bans it; frost falls back to solid);
    - `org.jetbrains.compose.components:components-resources` **1.13.0-beta01**;
    - `org.jetbrains.compose.ui:ui-tooling-preview` **1.13.0-beta01**;
    - `io.github.sergio-sastre.ComposablePreviewScanner:{core,common,jvm}` **0.9.3**;
    - `io.github.takahirom.roborazzi:roborazzi-compose-desktop` **1.77.0**.
  - Gradle Plugin Portal: `io.github.composegears.valkyrie` 0.5.2 (2026-07-21). Not used for glyphs (the generator
    reads the TTFs directly); one of the two options for the 79 SVG marks (§4.11).
  - ComposablePreviewScanner `README.md` and `README_DEPRECATED.md` (GitHub, sergio-sastre): "The `:common` and `:jvm`
    modules are deprecated and will be removed in version 0.10.0"; androidx `@Preview`s, including in common
    packages, are scanned with `AndroidComposablePreviewScanner`; `@PreviewWrapper` since 0.9.0.
  - Kotlin docs "What's new in Compose Multiplatform 1.13": 1.13.0-beta01 maps to Jetpack runtime/ui/foundation
    1.13.0-beta01 and Material3 1.5.0-beta01.
  - developer.android.com "Per-app language preferences": AGP builds the LocaleConfig from the `res` folders of the app
    module and its library module dependencies; "the build fails if you do manually create a LocaleConfig file" while
    automatic support is on.
  - androidx `ComposeFoundationFlags.isSmartSelectionEnabled` (default true) [V reference page; U effect on the
    toolbar items, checked in P7].
  - Pinned Flutter SDK `curves.dart` (Shorebird cache `91f8bd75…`): `easeOutCubic (0.215, 0.61, 0.355, 1.0)`,
    `easeInCubic (0.55, 0.055, 0.675, 0.19)`, `easeInOutQuad (0.455, 0.03, 0.515, 0.955)`,
    `easeOutQuad (0.25, 0.46, 0.45, 0.94)`; `page_transitions_theme.dart` `kTransitionMilliseconds = 450`.
  - intl 0.20.3 (`pubspec.lock`): `NumberSymbols` `ar` `ZERO_DIGIT '0'`; `DateSymbols` `ar` without an Arabic-Indic
    zero digit.
  - kotlinlang.org "Manage local resource environment": the `LocalAppLocale` pattern for CMP resources on Android
    (configuration override + `Locale.setDefault`), which the page calls a workaround "until a common public API is
    implemented".
- **Counted today [R]** from the freeze tip's ARB files: keys, plurals, selects, placeholder types, `=N` selectors
  (§13.1, §13.4); and the key-routing simulation of §13.3 (import filter vs every Dart file).

---

## 22. Change requests to other sections (the coordinator merges them)

| # | Section | Change | Why (this file) |
|---|---|---|---|
| CR06-1 | §01 §5.2 module table | `:ui:design`, `:ui:kit`, `:ui:testing` start in slice 0; `:ui:words` and `:tools:strings` start in slice 0 with spike scope (W0); add `:android:os-strings` (`android.library`, resources only, S l10n owner, slice 0). Slice 0 creates about 17 modules, not 12. | §3.1; P6 and P7 are slice-0 proofs and §10.3 freezes `KitPlatform` and 20 kit signatures at the end of slice 0 |
| CR06-2 | §01 §6.1 dependency table, §03 | `:ui:kit-content` may depend on `:core:security` (the `Redactor`); `:androidApp` and `:android:*` may depend on `:android:os-strings`. | §7.1 `KitRedact`; §13.2 |
| CR06-3 | §01 §6.3 rule 1, §03 `oc.kmp.feature` | Add `androidx.compose.ui.tooling.preview.{Preview, PreviewParameter, PreviewParameterProvider}` and `…ui.kit.preview.*` to the feature allowlist; the plugin adds `org.jetbrains.compose.ui:ui-tooling-preview` to `commonMain`. | §14.2; §15.1 requires screen previews from slice 2 |
| CR06-4 | §03 `:androidApp` res tree | Remove the hand-written `xml/locales_config.xml`; the LocaleConfig is generated (`generateLocaleConfig = true`), and a manual file fails the build. Move `generateOsStrings` / `copyOsStrings` to `:android:os-strings`. | §13.6 step 4; §13.2 |
| CR06-5 | §05 key table and M-D5 | `oc.glass` is **A** (adopted, read by `:engine:settings` as the frost strength; unknown → `balanced`; device-wide, not swept), not I; remove it from M-D5's dead-key list. `oc.effectsDepth/Edges/GlowColours/GlowSpeed` stay I. | §5.2; owner 2026-10-30 |
| CR06-6 | §07 X7 and row I04 | Keep `GlassChoiceControl` as a `KitSegmented` (Off/Weak/Balanced/Strong) under the Style question, with the held reasons; "glass control dropped" is wrong: it is the frost control. | §7.2 |
| CR06-7 | §08 M8-2, the curve table, `KitMotion.Enter/Exit` and §6.3 | `Enter`/`Exit` are Flutter's control points (`0.215, 0.61, 0.355, 1` and `0.55, 0.055, 0.675, 0.19`), never the androidx `EaseOutCubic`/`EaseInCubic`; add `StepEase`/`StepOut`. §6.3: copy plays `Send` (Dart `kit_copy.dart` parity). §08 §13.2's corrections to this file are applied here. | §4.8, §4.9 |
| CR06-8 | §11 S11-8, CR11-8, §11 copy table | Superseded: `strings_os` is generated once into `:android:os-strings`, which `:androidApp` and `:android:widgets` depend on; no second copy and no parity test. Notification and widget time and number formatters use the same `nu-latn` helper as `KitPlatform.formatMoment`. | §13.2; §12.1 rule 8 |
| CR06-9 | §01 §11 P6 | P6's acceptance adds: a key used only in a `part of` file routes correctly, and the unused share is ≤ 2 % on the spike files. | §13.3 |

## Review notes (2026-11-01 revision)

What changed, by review issue. "Confirmed" means the issue was checked against the freeze tip (`fc5c1e78f`), the
pinned Flutter SDK, sibling plan sections or a primary web source before the edit.

- **Stale snapshot (blocker): confirmed, fixed.** Rule 0, §0, §13.1, the converter's paths and §21 now point at
  `int/freeze-2026-11-01` `fc5c1e78f`, re-recorded at M0; the converter checks `--source-sha` and runs after the last
  Flutter merge. Recounted §13.1 (en 9,837, ar 10,855). Added §7 rows for `KitUpdateDot`, `KitUpdateSheet`,
  `KitVitalsStrip`, `KitPasteImageMenu`, the unfinished-tail Markdown rule, the large-text approval chip, the
  ellipsizing hint and the `whats-new` tour; `KitChrome` → `KitFrameText` is in the rename map.
- **Converter key routing (blocker): confirmed, fixed.** Rerunning the rule gave 2,281 unused keys on batch1 (2,325 on
  the freeze tip) with the import filter, about 1 % without it. §13.3 drops the filter, routes by the using file's own
  path, adds the `kit_confirm_sheet.dart` fixture and a 2 % unused-share failure.
- **`kitMaxWidth` width (major): confirmed, fixed** to `constraints.constrainWidth(placeable.width)`, no layout node
  without a cap, `KitMaxWidthTest` added.
- **CapRole alignment (major): confirmed, fixed** to Dart's defaults (centre for page-level roles; start for chip,
  popover, lane; Toast stays bottom-start, which matches `kit_undo.dart`).
- **Curves (major): confirmed, fixed** with Flutter control points, `StepEase`/`StepOut`, and a Konsist ban on the
  androidx names; CR06-7 for §08.
- **`lift` spring / bubble lift / Q5 (two issues, major): confirmed, fixed.** `lensLift` is the tab lens; the bubble is
  a 250 ms `Enter` tween; Q5 covers the lens only. `EXIT` removed; token sources cited (the separate minor issue).
- **Haptics map and copy haptic (major): confirmed, fixed** to §08 §6.2 (Send = VirtualKey, Commit = KeyboardTap,
  Done = Confirm, toggles and segments on API 34+); copy plays `Send` like Dart. `KitMotionState` gains §08's fields
  (also `debugTimeScale`, which §08 lists).
- **Attention allowlist (two issues, major): confirmed, fixed by meaning.** 42 Dart kit files read attention. Not
  adopted: "only `KitColors` reads raw roles", because 27 Dart kit files (context ring, step light, composer caption…)
  read the raw roles directly, so a seeded, shrink-only file allowlist plus the attention APIs is the gate. The stuck Stop
  chip and the Stop-and-send pill are now in §7.8.
- **`require` in composition (major): confirmed, fixed** with `kitAssert` (debug, test, preview only), `KitEnabled`, a
  ban on `require(`/`check(` in composables, and `KitPlatform.isDebug`.
- **Redaction (two issues, major): confirmed, fixed.** `KitCopy(text, redact = true, sensitive)`; the 12 Dart parts that
  redact keep doing so; `KitRedactionGateTest` ports G12; CR06-2 for `:ui:kit-content`. 25 `redact: false` call sites on
  the freeze tip (the issue said 24).
- **Frost source (major): confirmed, fixed** with `KitFrostState` + `kitFrostSource` + `kitFrost`, frozen in slice 0.
  Haze removed as a fallback (solid instead), rather than adding an X-21 exception; one file name (`AndroidFrost.kt`).
- **Frost control and `oc.glass` (major): confirmed, fixed** (§5.2, §7.2; CR06-5, CR06-6).
- **Glass trial after the switch (major): confirmed** from memory `no-glass-rule`; WP G1 added and the 5-style,
  2-role and blur rules marked as switch-release rules.
- **Preview scanner and variants (major): confirmed** from the scanner's `README_DEPRECATED.md`; fixed with
  `AndroidComposablePreviewScanner`, `LocalKitPreviewOverride` and a build-time gallery registry.
- **Feature allowlist lacks `@Preview` (major): confirmed, fixed** (§14.1, §14.2; CR06-3).
- **`strings_os` in widgets (major): confirmed** (43 `@string/` references on the freeze tip, the issue said 28).
  Chose the one-library option `:android:os-strings` over §11's two copies (CR06-8).
- **LocaleConfig (major): confirmed** from Android's docs; fixed with `strings_os` in all 7 folders, a check rule, and
  CR06-4 to drop the manual file.
- **Module start slices (major): confirmed, fixed**; W1 split into W0 (slice-0 P6 spike) and W1 (CR06-1).
- **Line-height alignment (major): confirmed, fixed** to `Proportional`, `Center` only in `KitField`/`KitSearchField`.
- **Material bridge table (two issues, minor): confirmed, fixed** from `schemeFromRoles`; the test compares against
  values generated from the Dart function.
- **Parity DPR (minor): confirmed** (goldens are 412×915 px); parity captures at `w412dp-h915dp-mdpi`.
- **KitPlatform seams (minor): partly adopted.** Added `isDebug` and `frameDensity`. The suggested
  `calculateRecommendedTimeoutMillis` alone is not parity (Dart shows the undo bar with no timeout under accessible
  navigation), so `KitSystemSignals.accessibleNavigation` was added and the recommended timeout is used otherwise.
- **JETPACK_AHEAD versions and Q7 (minor): confirmed, fixed**; the selection row was dropped; Q7 now names
  `isSmartSelectionEnabled = false` or a kit toolbar [U in P7].
- **Double focus target (minor): confirmed, fixed** (`combinedClickable` alone).
- **`KitTextFit` in release (minor): confirmed, fixed** (static rule only; tests use `GetTextLayoutResult`).
- **Glyph and mark sources (minor): partly adopted.** `_duotoneBackgrounds` is now a source. Rejected detail: fontTools
  does have an SVG path parser (`fontTools.svgLib.path`), but it does not cover the marks' group transforms, `evenodd`
  and `clipPath`, so §4.11 handles those explicitly or uses Valkyrie.
- **§8.3 vs §15.2 (minor): confirmed, fixed.**
- **Haptics key (minor): confirmed, fixed**; Vibration row is Q11.
- **Read rules and Saver (minor): confirmed, fixed** in §4.3.
- **Rename map (minor): confirmed, fixed** with `kit_renames.tsv`, consumed by `:tools:capacity-port` and
  `KitManifestTest`.
- **Frame list (minor): confirmed, fixed** to Flutter's `KitChrome` list; extensions are Q10.
- **Arabic digits (minor): confirmed, fixed** with `nu-latn` in both formatters and the `:android:*` ones (CR06-8).
- **Frost σ → radius (minor): confirmed, fixed** (`blurRadiusPx`).
- **Capture matrix (minor): confirmed, fixed** (Saver, high contrast, ar-light/en-dark for the 20 parts, the owner's
  look).
- **Capacity matrix (minor): confirmed, fixed** (text 1.3; the G6 sizes as `KitOverflowMatrixTest`).
- **Sheet closes when answered elsewhere (minor): confirmed, fixed** (ViewModel owns it; test moved).
- **Jump pill 480 px (minor): confirmed, fixed** to 480 dp through `Layout`.
- **Context ring and B-KIT-058 (major): confirmed, fixed** (amber 70–95 %; B-KIT-058 parked with D6).

No issue was rejected outright. Two were adopted in part (the KitPlatform accessibility timeout and the fontTools
detail), and one had two options, of which the one-library `strings_os` was chosen.

## Slice-0 learnings folded in (2026-11-01)

From `docs/native/slice0/P6-strings.md` and `P7-kit.md` on `native/m0-2026-11-01`:

| Finding | Where it changed this section |
|---|---|
| A custom `Density.toDp` never reaches text on Android; the frame cap is a per-size `frameDp` under font scale 1 (16 sp at 200 % = 20.8 dp, Flutter-exact) | §9.2 rewritten; `KitPlatform.frameDensity` and the `FontScaleConverterFactory` `JETPACK_AHEAD` row dropped |
| `KitPlatform` haptics are a plain `haptic(kind)` through the activity's `decorView` | §6 |
| Compact two-line row is 56, not 54 (Dart computes `rowHeightTwoLine - space2`) | §4.3, §4.5 |
| Flutter's `Enter` curve is (0.215, 0.61, 0.355, 1), which is **not** androidx `EaseOutCubic` (0.33, 1, 0.68, 1) | §4.8 is right; 08 §2.2 corrected |
| CMP unescapes only `\n`, `\t`, `\uXXXX`, `\\`; two escaping profiles; `select` and double plurals refused; English-rule fallback for borrowed plurals; `__eqN` before categories | §13.3, §13.4 |
| Roborazzi task names: `recordRoborazziAndroidHostTest` / `verifyRoborazziAndroidHostTest` / `compareRoborazziAndroidHostTest` (and `…Desktop`) | §15.3 (settles 03 P-03-4) |
| The kit sheet is kit-drawn in slice 0 (deterministic goldens); M3 1.5's in-window sheet with drag and predictive back is tried in WP K1 | K9 stays the target; P7 D4 is a slice-0 deviation only |
| Goldens recorded in a cloud container must be re-recorded on the VPS before they gate anything | §15.3 rule unchanged; P7 D13 |
