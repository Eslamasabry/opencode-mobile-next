# Design schools: the baseline contract (2026-10-21)

*Part of [design schools](README.md). This is the shared contract that every per-school agent builds against. It fixes the token schema, the knobs, the resolver API, the parts that must honour a style, the fallbacks, the acceptance bar and the merge order. In phase 2 it becomes code (§11). Techniques and budgets: [flutter-techniques.md](flutter-techniques.md). Values per school: [schools/](schools/).*

**Status:** the code baseline landed on `feat/schools-baseline` (phase 2, 2026-10-21), built on what `feat/kit-surfaces` actually shipped. **§0 says what was built and where it differs from the proposal below; where they disagree, §0 wins.** The rest of the document is kept as the design rationale and the per-family treatment tables the school agents build to.

## 0. As built (phase 2 baseline)

**The resolver is Surfaces' `KitSurfaceLook`, extended, not replaced.** Kit parts keep calling `KitSurfaceLook.decorate(context, solid:, level:, role:, borderRadius:, circle:, color:, selected:, pressed:)` and `kitSurfaceBox(...)` / `kitSurfaceSliver(...)` / `kitSurfaceMaterial(...)`. No part or screen changed for the baseline.

| Piece | Where | What it is |
|---|---|---|
| Style enum | `lib/state/effects.dart` | `KitSurfaceStyle { solid, glass, soft, brutal, paper }`. `brutal` and `paper` are appended; stored by name in `oc.effectsSurfaceStyle`, so existing values stay valid. |
| Knobs | `lib/state/effects.dart` | `KitCorners {round, soft, sharp}`, `KitDepth {flat, raised}`, `KitDensity {comfortable, compact}`, `KitEdges {hairline, none}` (the UI says On · Off, owner Q3). `KitKnobs` (all decided) and `KitFineTune` (nullable: null = the style's default). `KitEffects.fineTune`; `KitEffects.withSurfaceStyle(style)` resets Fine-tune; `underSaver` resets it too. |
| Storage | `lib/state/app_preferences.dart`, `test/fixtures/coverage/settings_preferences_ledger.json` | `oc.effectsCorners`, `oc.effectsDepth`, `oc.effectsDensity`, `oc.effectsEdges` (by name; absent or unknown = null). Global scope in the ledger. A write that changes the style removes all four keys, even if the caller forgot `withSurfaceStyle`. |
| Registry | `lib/ui/kit/style/kit_style.dart` | `kitStyleSpec(style)`: an exhaustive switch, one line per style. Adding a style = one enum value + one spec file + one line here. |
| Token object | `lib/ui/kit/style/kit_style.dart` | `KitStyleTokens { defaults (KitKnobs), sharpRadius, blursBehind, keepsUnderHighContrast, dependsOnDepth, type (KitStyleType) }`. |
| Spec seam | `lib/ui/kit/style/kit_style_<id>.dart` | `abstract class KitStyleSpec { KitStyleTokens get tokens; Decoration resolve(KitSurfaceRequest r); }`. The request carries roles, role, the part's `solid` decoration, `knobs`, level, radius, circle, color, selected, pressed and edgeWidth. |
| Readers | `KitStyles` in `kit_style.dart` | `styleOf(context)` (high contrast → Solid unless the style keeps itself), `knobsOf(context)`, `knobsFor(style:, fineTune:, highContrast:)` (§2.4 rules: HC → Depth flat + Edges on; a `dependsOnDepth` style that is flat keeps its edge), `corner(radius, corners, sharp:)`. |
| Solid | `kit_style_solid.dart` | Returns the part's own decoration when the knobs are Solid's defaults, so Solid is pixel-identical. Moving knobs: Depth raised gives content the tight floating drop; Edges off strips panel and sheet borders. |
| Glass, Soft | `kit_style_glass.dart`, `kit_style_soft.dart` | Surfaces' `_glass` and `_soft` moved verbatim (RP-5), plus knob handling that only acts away from their defaults. Their goldens are unchanged. |
| Brutal, Paper | `kit_style_brutal.dart`, `kit_style_paper.dart` | **Placeholders, correct but plain.** Brutal: opaque fills, a 2 dp ink edge, a blur-0 hard shadow (4 content / 3 control / 6 floating; 0 when chosen, pressed or flat; mirrored in RTL), with Soft corners by default. Paper: content ruled (ground + ink at α 0.28), controls, floating pieces and sheets boxed in ink (α 0.9 light, 0.6 dark), no shadows, Sharp = 0. Both keep their look under high contrast. |
| Hard shadow | `KitSurfaceDecoration.hardShadow` / `hardOffset` | A new paint layer: the surface's shape in one colour with no blur, towards the bottom end (mirrored in RTL), drawn outside the surface only; lerps and compares like the rest. |
| Tokens follow the knobs | `KitTokens.of` / `KitTokens.styled(knobs, sharpRadius:)` | Corners scale every token radius (Round as is, Soft ≈ 60 %, Sharp = the style's `sharpRadius`; sheet, composer and nav keep up to 4 under Sharp). Compact: rows 48/54, button 48, section gap 16, label gap 6; touch targets never shrink. At Round + Comfortable it returns the theme's own object. Memoised per base. |
| Previews | `kit_option_card.dart` | Brutal and Paper's Surfaces sample draws through `KitSurfaceLook.resolve`, so a school's tuning shows there for free. The Appearance picker still lists Solid, Glass, Soft only (no UI yet). |

**Hooks H1–H11 (built after the baseline, before any school starts; table in [briefs/README.md](briefs/README.md)):** type tweaks applied to `KitText` and the token text styles; the pressed face sinking into its shadow (`faceShift`); words colour from the spec (`onColor`, read through `KitSurfaceLook.wordsOn`); pills and the static shape tokens following Corners and Density; a ground per style (`adjustRoles`, applied by `kitStyleTheme` in `main.dart`); the live-background policy (`KitLivePolicy`, and `KitSurfaceLook.choose` for the picker); the glow per style (`KitStyleGlow`); no nested shadows (`flattensNested`); a plate behind the words (`wordsPlate`); one `BackdropGroup` per page.

**Slice S0 of the style cleanup (2026-10-21, `feat/style-s0`; detail and the part recipe in [briefs/README.md](briefs/README.md#slice-s0-style-cleanup-2026-10-21-roles-and-rules-every-part-uses)):**
- **Roles.** Surfaces' five pane roles (`content`, `control`, `track`, `floating`, `sheet`; `KitSurfaceRole.panes`) gain six of a part's own: `ground` (a page host), `field`, `code`, `divider` (read through `KitSurfaceLook.divider`), `stateLayer` and `toggle` (a switch's track; its thumb is a `control`). Every spec answers each one; §4.2's other roles stay mapped onto these (a card is `content`, a tile `control`, a popover `sheet` or `floating`).
- **Rules in the resolver.** The page is its fill alone and never casts (so H9 keeps the shadows of a page hosted on `KitSurface(level: ground)`); code stays opaque; fields and off switches reach 3:1 at rest in every style but Solid (`kitStyleBoundary`); a `content` surface in a `content` surface sits on the outer face at rest (no second fill, edge or shadow) in every style but Solid (`KitStyleTokens.keepsNestedSurfaces`).
- **Solid at its defaults** (`KitSurfaceLook.isPlain`) replaces the `style == solid` early-out in `kitSurfaceMaterial`, so Fine-tune reaches every `KitSurface` under Solid.
- **Tokens.** `KitStyleTokens.keepsNestedSurfaces` (Solid true) and `dividerWidth` (Brutal 2).
- **Gate G49** (`test/kit_ratchet_test.dart`): raw `BoxDecoration(` / `ShapeDecoration(` / `ColoredBox(` / `Material(color:` inside the kit, outside the resolver and an exempt list, only shrink.

**Still not built (no school needs it yet):** the aggregate `KitStyle` object and `KitStyleScope` with a snapshot cross-fade (Surfaces' per-part fades stay: 200 ms style, 120 ms state); the other surface roles of §4.2 as their own values (see S0 above for the mapping).

**Tests:** `test/kit/kit_style_test.dart` (the five styles and their defaults; Solid identity for decorations and tokens; knob rules, high contrast and Saver; corner and density maths; invariants for every style × brightness × all 24 knob combinations × 5 roles × chosen or not: uniform radii wherever a shadow is cast, AA words on Brutal and Paper fills, no amber fills; Brutal and Paper specifics including the RTL mirror; storage round trip and the reset on a style change). Goldens: `test/goldens/kit/kit_surfaces_golden_test.dart` now renders all five styles in light and dark. Captures: `tool/capture/surfaces_test.dart` renders home, chat, settings and a sheet per style, plus UI-thread frame timing.

---

## 1. Model in one paragraph

A **style** (Solid, Glass, Soft, Brutal, Paper) is a named set of defaults for four **Fine-tune knobs** (Corners, Depth, Density, Edges), plus the style's own **models**: how surfaces are filled, how they cast depth, how they show edges, how they react to press and selection, and which type weights they use. The person's choices (style + knob overrides) live in `KitEffects`. The kit resolves them once per (choices × brightness × platform flags) into an immutable **`KitStyle`**. Kit parts never read the style enum. They ask the resolver for a **`KitSurfaceLook`** for their *surface role* and state, and paint it. Screens see none of this: they keep arranging kit parts (kit-only rule, kit-v2 §9).

```
KitEffects (stored)  ─┐
Theme brightness     ─┼─► resolveKitStyle() ─► KitStyle (tokens) ─► KitSurface.look(role, state) ─► part paints
MediaQuery flags     ─┤        ▲                     │
Saver (effective)    ─┘        │                     └─► KitTokens.styled(style): radii, density, type
                      schools/kit_style_<id>.dart (one spec per school)
```

## 2. Choices and storage

### 2.1 Style

- **Extend the frozen enum; don't rename it.** `KitSurfaceStyle { solid, glass, soft }` gets `brutal, paper` **appended** (in that order; Clay was dropped by the owner on 2026-10-21). Storage (`oc.effectsSurfaceStyle`) writes `.name` and `app_preferences.dart` reads it with `_enum(…)`, so appending is backward compatible and an unknown name falls back to Solid. The UI word is **Style**. The code keeps `KitSurfaceStyle`, plus `typedef KitStyleId = KitSurfaceStyle;` for readability. ⟂ **RP-1**
- A style the build doesn't know yet (the enum exists but the school hasn't landed) resolves to Solid, and the picker hides it (`KitStyleSpec.ready == false`, §4.4). That way the enum can land in the baseline before the schools do.

### 2.2 Fine-tune knobs

```dart
enum KitCorners { round, soft, sharp }      // Round · Soft · Sharp
enum KitDepth { flat, raised }              // Flat · Raised
enum KitDensity { comfortable, compact }    // Comfortable · Compact
enum KitEdges { hairline, none }            // Hairline · None (owner wording; see Q3)
```

- `KitEffects` gains four **nullable** fields: `corners`, `depth`, `density` and `edges`. **null means "the style's default"**. The resolver fills nulls from the active style's spec.
- Storage: `oc.effectsCorners`, `oc.effectsDepth`, `oc.effectsDensity` and `oc.effectsEdges` (by `.name`; an absent key is null). These are device-wide appearance choices, not per-profile, so the `oc.<what>.<profileId>` deletion sweep doesn't apply. That matches the existing `oc.effects*` keys.
- **Changing the style resets the knobs:** the Appearance controller writes the new style and sets all four to null in one `copyWith`, one save. The UI shows the new style's defaults as selected.
- **One combination rule:** if a style's identity depends on shadows (Soft) and Depth is Flat, the resolver forces Edges on (Hairline) so surfaces keep a visible boundary. That is the "no invisible controls" rule, from WCAG 1.4.11 ([Non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)).

### 2.3 Defaults per style

| Style | Corners | Depth | Density | Edges | Why |
|---|---|---|---|---|---|
| Solid | Round | Flat | Comfortable | Hairline | today, pixel for pixel |
| Glass | Round | Flat | Comfortable | Hairline | the rim is the edge; depth comes from translucency |
| Soft | Round | Raised | Comfortable | None | shadows carry the shape; hairlines are mostly dropped |
| Brutal | Soft | Raised | Comfortable | Hairline (= its bold 2 dp ink edge) | the border + hard shadow are the school |
| Paper | Sharp | Flat | Comfortable | Hairline (= its 1 dp ink rule) | print: rules, no shadows |

### 2.4 Effective choices (what reaches the resolver)

```
effective = person's KitEffects
  → Saver on?               style = solid, depth = flat, liveBackground off, motion off
                            (applied by the Appearance agent inside KitEffectsScope ⟂ RP-2)
  → highContrast (MQ)?      glass|soft → solid; every style: edges on (≥ 3:1), depth flat, opaque fills
  → style not ready?        → solid
  → knob nulls              → style defaults (§2.3), then the §2.2 combination rule
```

## 3. The token schema: `KitStyle`

One immutable value per (style × brightness × effective knobs × text direction-independent). It is a plain class with `lerp` (for state transitions) and `==`/`hashCode` (so it can be cached and drive an `InheritedWidget`).

```dart
@immutable
class KitStyle {
  final KitStyleId id;
  final Brightness brightness;
  final KitCorners corners;  final KitDepth depth;
  final KitDensity density;  final KitEdges edges;     // all resolved, never null

  final ThemeRoles roles;          // §3.3: the theme's roles, possibly adjusted by the style
  final KitRadii radii;            // §3.1
  final KitEdgeSpec edge;          // §3.2
  final KitShadowModel shadow;     // §3.4
  final KitFillModel fill;         // §3.5
  final KitTypeSpec type;          // §3.6
  final KitDensitySpec space;      // §3.7
  final KitStateSpec states;       // §3.8 pressed / selected / hover / disabled
  final KitFocusSpec focus;        // §3.9
  final KitGlowSpec glow;          // §3.10 the composer's running glow
  final KitLivePolicy live;        // §3.11 how the live background shows under this style
}
```

### 3.1 Radii (from the Corners knob)

One global table per knob value. **Round is today's table**, so Solid stays pixel-identical. Styles choose a default and never redefine the numbers. That keeps "Round" meaning the same thing in every style.

| Token (today's name) | Round | Soft | Sharp |
|---|---|---|---|
| `panelCornerRadius` (panel) | 18 | 12 | 2 |
| `cardRadius` (needs-you, request) | 22 | 14 | 2 |
| `buttonRadius`, `fieldRadius` | 14 | 10 | 2 |
| `sheetRadius` (top corners) | 30 | 20 | 4 |
| `panelRadius` (dialog) | 24 | 16 | 2 |
| `composerRadius` / wide | 26 / 18 | 16 / 12 | 4 / 2 |
| `bubbleRadius` / `bubbleTailRadius` | 20 / 6 | 14 / 4 | 2 / 2 |
| `iconTileRadius` | 9 | 7 | 2 |
| `codeRadius`, `popoverRadius`, `detailsRadius` | 14 | 10 | 2 |
| `navRadius` (dock, rail, top pill) | 22 | 14 | 4 |
| `KitShape.pill` (chips) | stadium | stadium | 2 |
| `KitShape.circle` (icon buttons, avatars, send/stop) | circle | circle | circle (always) |

There is one override, and no other: **Paper's Sharp is 0** rather than 2. Every radius stays **uniform per surface** wherever a shadow is cast ([flutter-techniques.md](flutter-techniques.md) §0.3).

### 3.2 Edges (from the Edges knob)

```dart
class KitEdgeSpec {
  final double width;        // logical px; KitEdgeSpec.hairline = 1/dpr (physical)
  final Color color;         // a role-derived colour
  final Color? topRim;       // glass: a light 1-physical-px line on the top edge
  final Color? bottomRim;    // glass: a dark 1-physical-px line on the bottom edge
  final bool needsContrast;  // when true, the colour is guarded to ≥ 3:1 against the ground (HC, fields)
}
```

Edges = None sets width 0 on content surfaces. **Fields, the focus ring and the segmented thumb keep an edge regardless** (they must stay identifiable). Each style defines what "Hairline" means for it (Solid: today's `hairline` role; Brutal: 2 dp ink; Paper: 1 dp ink; Glass: rims). See Q3.

### 3.3 Roles under a style

A style may adjust **only these roles**: `ground`, `surface1–3`, `hairline`, `scrim`, `elevationShadow`. It never touches `text1–3`, `accent`, `attention*` or `success` directly. After any adjustment, the resolver re-runs **`roles.withAccent(roles.accent)`**, so the existing accent guard (`_guardAccent` against `[ground, surface1]`) keeps accent text at AA on the new ground. It also re-checks `text3` ≥ 4.5:1 and `attention` ≥ 4.5:1 on every adjusted surface. If they fail, the spec is wrong; there is no silent fix. My checks (scratch script, WCAG formula): graphite light's accent `#087F43` is only **4.58:1** on today's ground, so *any* darker style ground (Soft, Brutal) needs the guard.

### 3.4 Shadow model

```dart
sealed class KitShadowModel {}
class KitShadowNone extends KitShadowModel {}                                 // Paper; Depth=Flat everywhere
class KitShadowFloat extends KitShadowModel { final List<BoxShadow> floating; } // Solid, Glass: today's one tight drop, floating layer only
class KitShadowSoftPair extends KitShadowModel {                              // Soft
  final KitShadowPair raised; final KitShadowPair inset; final double distance, blur; }
class KitShadowHardOffset extends KitShadowModel {                            // Brutal
  final Offset offset; final Color color; }                                   // blur 0 always
```

- Offsets are written **for LTR**. `resolve(TextDirection)` mirrors dx in RTL ([flutter-techniques.md](flutter-techniques.md) §5).
- Depth = Flat means: Solid and Glass keep `floating` (the existing one shadow under the dock, composer and top controls; that is today's look); Soft and Brutal drop *content* shadows and keep the floating-layer shadow only; Paper has none.
- `KitInsetShadow(color, offset, sigma)` is painted by the one kit painter (techniques §2.1), used by Soft.

### 3.5 Fill model

```dart
sealed class KitFillModel {}
class KitFillOpaque extends KitFillModel {}                       // Solid, Brutal, Paper: level → role colour
class KitFillGroundMatched extends KitFillModel {}                // Soft: every content surface = ground; tiles one step off
class KitFillTranslucent extends KitFillModel {                   // Glass
  final double alpha;            // content surfaces (no blur)
  final double floatingAlpha;    // bars/sheets (with blur)
  final double blurSigma;        // floating/modal only
  final Color tint;              // the surface role the alpha applies to
  final bool remapText3;         // text3 → text2 on translucent fills (see glass.md)
}
```

### 3.6 Type

```dart
class KitTypeSpec {
  final String? displayFamily;   // null = AppSans (Geist). Optional faces: techniques §7
  final double displayWeight;    // largeTitle/title/headline weight (Geist variable)
  final double displayTracking;  // em
  final double largeTitleSize;   // 32 today; Paper 34
  final double bodyWeight;       // 400 today
  final bool monoLabels;         // Brutal, Paper meta: section labels and captions in AppMono
}
```

`KitText.styleFor(role)` applies the spec on top of the fixed role table. Under an Arabic locale the family stays the system face and tracking stays 0. Only weights change. `boldText` adds 200 to the body weight (capped at 700).

### 3.7 Density (from the Density knob)

| Token | Comfortable (today) | Compact |
|---|---|---|
| `rowHeight` / `rowHeightTwoLine` | 54 / 60 | 48 / 54 (never below `minTarget` 48) |
| `panelPadding` (static today) | 16 | 12 |
| `sectionGap` / `labelGap` | 22 / 8 | 16 / 6 |
| `gutter` | 16 | 16 (unchanged: the phone edge needs it) |
| `buttonHeight` | 50 | 48 |
| `choiceRowMinHeight` | 56 | 48 |
| `KitTokens.chipHeight` (visual) | 32 | 28 (the 48 dp target stays as padding) |

Touch targets never shrink (kit-v2 §8.3).

### 3.8 States

```dart
class KitStateSpec {
  final KitPressed pressed;      // tint(Color) | inset | translate(Offset) | darken(double)
  final KitSelected selected;    // ring(accent) | inset | fill(accent/onAccent) | invert(ink/paper) | brighten(alpha)
  final Color hover;             // fine pointer only
  final double disabledAlpha;    // KitTokens.disabledAlpha (.38) for every style; words keep text3
}
```

| Style | Pressed | Selected (chip, segment, tab, choice) |
|---|---|---|
| Solid | `surface3` fill (today) | today's ring or fill per part |
| Glass | brighter translucent (α +0.08) | brighter fill + accent rim, 1 physical px |
| Soft | **inset** (pressed in) | **inset** + accent glyph/word |
| Brutal | **translate by the shadow offset, shadow 0** | accent fill + ink words + ink border (no shadow) |
| Paper | `darken(.06)` of the fill | **invert**: ink fill, ground words |

### 3.9 Focus ring

The ring is always visible on keyboard focus (kit-v2 §8.3). Width = `KitTokens.focusRingWidth` (2 physical px) everywhere except **Brutal (3 dp)**. Colour = `accent`, which meets ≥ 3:1 against the adjacent fill in all styles per my checks. It follows the part's resolved shape. In Brutal and Paper it sits **outside** the border (offset 2 dp), so it never merges with the ink edge.

### 3.10 Glow (the running composer)

The owner likes the running green glow and its hue around the composer (2026-10-14), so it stays in **every** style. `KitGlowSpec { haloBlur, ringWidth, followsRadius }`:

- Solid, Glass and Soft: today's glow (classic or soft ring), following the style's composer radius.
- Brutal: the travelling highlight is kept, but the **soft halo is replaced by a hard 2 dp accent ring that follows the border**, with no blur.
- Paper: a **thin 1 dp accent rule** travels the border, with no halo.
- Saver: off (motion off). Reduced motion: still ring (existing behaviour).

### 3.11 Live background policy

```dart
class KitLivePolicy { final double maxIntensity; final bool underSurfaces; }
```

| Style | Policy | Reason |
|---|---|---|
| Solid | as chosen | today |
| Glass | as chosen; glass *needs* it (without it, glass = translucent solid) | glass is "frosted panes the live background shows through" |
| Soft | cap at Subtle (0.7), drawn only on the open ground | ground-matched surfaces break if the ground moves under them |
| Brutal | cap at Subtle | Q4: suppress entirely? |
| Paper | **off by default under Paper** (print does not move) | Q4 |

## 4. The resolver API

### 4.1 Reading the style

```dart
class KitStyle {
  static KitStyle of(BuildContext context);   // depends on KitStyleScope; falls back to solid(brightness)
  static KitStyle read(BuildContext context); // no subscription (handlers)
}

/// Placed once, directly under KitEffectsScope in MaterialApp.builder (main.dart; ⟂ RP-3).
/// Resolves (effects × Theme brightness × MediaQuery flags) → KitStyle, caches it,
/// and runs the 200 ms snapshot cross-fade on change (techniques §6).
class KitStyleScope extends StatefulWidget {
  const KitStyleScope({required this.child, this.override}); // override: previews (§4.5)
}
```

### 4.2 Surface roles

Parts ask by **role**, not by level. Levels (`KitSurfaceLevel`) stay as the *fill input* for opaque styles.

```dart
enum KitSurfaceRole {
  ground,          // the page
  panel,           // a grouped panel of rows (KitSurface.panel, KitPanel, KitRowGroup)
  inset,           // a panel set into a sheet (KitSurface.inset, consequences, details)
  card,            // needs-you and request cards (attention look applied on top, §5 F2)
  tile,            // a row's leading icon tile, request tile
  bubble,          // the person's prompt bubble, queued message
  floating,        // dock, rail, top pill, jump pill, composer shell, FAB, undo, key bar
  sheet,           // bottom/side sheet body
  dialog,          // centred dialog panel
  popover,         // menu, tooltip, term bubble
  controlPrimary,  // the one main action (accent)
  controlSecondary,// surface3 buttons, chips at rest
  controlQuiet,    // tertiary text buttons (no surface in any style except Brutal's ink underline)
  track,           // segmented track, tab strip, slider track, switch track
  thumb,           // segmented thumb, selected tab, switch thumb
  field,           // text fields, search, one-time code
  code,            // code block, log panel, terminal surface, diff
}
```

### 4.3 The look

```dart
/// What a part paints for one role in one state. Immutable, lerpable.
class KitSurfaceLook {
  final Decoration decoration;   // KitStyleDecoration: fill, outer shadows, inner shadows, edge, rims, sheen
  final ShapeBorder shape;       // for Material/InkWell clipping and the focus ring
  final double? blurSigma;       // non-null → the part wraps in ClipRRect + BackdropFilter.grouped
  final Offset faceOffset;       // brutal press translation (paint only, never hit-test)
  final Color onColor;           // the default word colour on this fill (glass may remap text3→text2)
  final EdgeInsetsGeometry? padding; // density-aware default inner padding for the role
  static KitSurfaceLook lerp(KitSurfaceLook a, KitSurfaceLook b, double t);
}

class KitSurface {                       // ⟂ RP-4: merges with the Surfaces branch's decorate()
  static KitSurfaceLook look(
    BuildContext context,
    KitSurfaceRole role, {
    KitSurfaceLevel? level,              // fill input for opaque styles; null = the role's default
    bool selected = false,
    bool pressed = false,
    bool disabled = false,
    bool attention = false,              // the needs-you treatment on top (F2)
  });
}

/// The one widget that applies a look: decoration, optional grouped blur, clip,
/// face offset, and the 150 ms implicit lerp between states. Kit-internal parts use it;
/// KitSurface (the public widget) is built on it.
class KitStyledBox extends StatelessWidget { … }
```

`KitStyleDecoration extends Decoration` paints the outer shadows → fill (opaque, translucent or sheen gradient) → inner shadows → edge → rims, mirroring in RTL from `ImageConfiguration.textDirection`. It implements `lerpFrom`/`lerpTo` so implicit animations interpolate everything (techniques §6).

### 4.4 The school seam (one file per school)

```dart
/// lib/ui/kit/style/schools/kit_style_<id>.dart: the ONLY file a school agent must write.
abstract class KitStyleSpec {
  KitStyleId get id;
  bool get ready;                                   // false → picker hides it, resolver → solid
  KitKnobDefaults get defaults;                     // §2.3 row
  ThemeRoles adjustRoles(ThemeRoles base);          // §3.3 (identity for most)
  KitShadowModel shadow(ThemeRoles r, KitDepth d);
  KitFillModel fill(ThemeRoles r);
  KitEdgeSpec edge(ThemeRoles r, KitEdges e, double dpr);
  KitTypeSpec get type;
  KitStateSpec states(ThemeRoles r);
  KitGlowSpec get glow;
  KitLivePolicy get live;
  /// Optional per-role override when the generic model is not enough.
  KitSurfaceLook? lookFor(KitSurfaceRole role, KitLookContext c) => null;
}

// lib/ui/kit/style/kit_style_registry.dart: written ONCE in the baseline, never edited by schools.
const Map<KitStyleId, KitStyleSpec> kitStyleSpecs = {
  KitStyleId.solid: KitStyleSolid(), KitStyleId.glass: KitStyleGlass(),
  KitStyleId.soft: KitStyleSoft(),   KitStyleId.brutal: KitStyleBrutal(),
  KitStyleId.paper: KitStylePaper(),
};
```

The baseline lands all five spec files: Solid real, and the other four as stubs returning `ready: false` (or, for Glass and Soft, wrapping whatever the Surfaces branch landed, ⟂ RP-5). Each school then edits **only its own spec file** (and its painters, tests and goldens). The registry and resolver never change, so the branches cannot conflict.

### 4.5 Previews render a style that isn't active

The Appearance studio's preview, the Style picker's cards and the onboarding tour render *candidate* styles side by side: `KitStyleScope(override: KitStyleOverride(id: KitStyleId.paper), child: preview)`. Override scopes don't cross-fade, and their blur uses its own `BackdropGroup`. The picker cards are kit parts owned by the Appearance agent (`kit_option_card.dart`, `kit_appearance_preview.dart`), so the override is the whole contract between us.

### 4.6 Tokens follow the style

`KitTokens.of(context)` returns `base.styled(KitStyle.of(context))`, memoised per (base, style). Every part that reads tokens therefore gets the style's radii and density with no code change. **Migration item:** today some tokens are `static const` and can't vary: `fieldRadius`, `popoverRadius`, `bubbleRadius`, `bubbleTailRadius`, `panelPadding`, `chipHeight` and `choiceRowMinHeight`. The baseline turns them into instance getters (keeping the static consts as the Round/Comfortable values for `const` contexts). That edit is to the shared `kit_tokens.dart`, in the baseline only. ⟂ **RP-6**

## 5. Kit parts that must honour the style

**Surface families.** Every kit part that paints a surface belongs to exactly one family. A family has one treatment per style (the schools fill in exact values in their own docs). The complete file-by-file list, from the grep of 2026-10-21 (`surface1|surface2|surface3|cardRadius|panelCornerRadius|hairline|BoxDecoration|boxShadow|surfaceShadows|BorderSide|ShapeDecoration|KitSurface|KitGlass|Material(` in `lib/ui/kit`, 135 files), is in the [appendix](#appendix-every-surface-making-kit-file).

### F1 Content panels and groups
*KitSurface (panel, inset), KitPanel, KitRowGroup, KitSliverRowGroup, KitRow (pressed/selected fill), KitChoiceList/Row/PickerRow, KitKeyValue, KitFactRow, KitMiniTable, KitLastKnown, KitDetailsFold, KitConsequences, KitChecklist, KitProgressRow, KitFeedItem, KitHandoffRecord, KitPlanBlock, KitAgentCard/Grid/Line, KitTaskCard, KitBoardLane, KitWindowRow, KitCapabilityExplainer, KitServerTrustBody, KitStatusAnswer, KitReceipt, KitOptionCard, KitChoiceCards, KitChoiceTiles, KitSwatch/KitThemePreview, KitCarousel, KitWidgetPreview, KitWorkGraph nodes, KitContextRegion, KitHeroQuestion, KitStartBox, KitSenseAsk, team/* cards and rows*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| `surface1`, 18, no edge, no shadow | tint α 0.62–0.72 (no blur), rims | ground-matched, raised pair; rows inside flat | `surface` paper, 2 dp ink edge, hard (4,4) shadow | ground fill, 1 dp ink rule, 0 radius, no shadow |

### F2 Needs-you and request cards (amber means "needs you", nothing else)
*KitNeedsYou (mark, span, badge), KitRequestCard + request_shape/question/answers/row/sheet, KitNotice.card/offer/error, KitAskLine/KitAskField, KitStatusLine (attention)*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| `attentionSurface` + `attentionLine`, 22, 4 dp ring α 0.06 (today) | amber tint α 0.12 over the glass tint, amber rim, ring | raised soft card on ground + amber 4 dp start edge + amber caption; **no amber fill** | paper card, ink edge, hard shadow; "Needs you" tag = `attentionFill` + `onAttentionFill` + ink edge | amber 4 dp start rule + amber caption; no fill, no ring |

In every style, the code or command inside a request card stays a **neutral** code surface (F7). Amber is never a code fill (the brutal concept's yellow code box is a don't).

### F3 Floating layer
*KitGlass (+ pair), KitNav dock + KitNavRail, KitTopBar/KitShellControls pill, KitJumpPill, KitFloatingAction, KitFloatingPanel, KitUndo, KitComposer shell (+ edge, glow, tray, chips row, caption, status strip), TerminalKeyBar*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| `surface2`, hairline, one tight drop (today) | **real blur** σ 16, floating α 0.72–0.78, rims, tight drop | raised pair on ground; composer field inset | paper, 2 dp ink, hard (4,4) | ground, 1 dp ink rule top (dock) or box (composer), no shadow |

### F4 Modal
*KitSheet (frame, modal, parts), KitConfirmSheet, KitDialog, KitMenuPanel, KitTerm bubble, KitContextSheet, KitTaskSheet (KitTaskItem), KitRequestSheet, KitDateTimeRow/KitWheel*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| `surface2`, sheet 30 top, scrim (today) | **one blur** σ 20, own group, α 0.80, rims; scrim lighter (α 0.40) | ground-matched sheet; top highlight; handle inset | paper, 2 dp ink top/box, hard shadow on dialog only | ground, 1 dp ink top rule, 4/0 radius |

### F5 Controls
*KitButton (primary/secondary/tertiary), KitActionBlock/Stack/Inset, KitIconButton, KitChip/ChipWrap, KitFilterChip(s), KitSegmented, KitTabStrip/TabSwitcher, KitSessionTabs, KitStepSlider, KitSwitchRow switch, KitChoice marks, KitPhraseChips, KitComposerChips, KitTaskChip, KitStatusTag, KitSwipeAction, KitTappable (hover/pressed/focus)*

| | Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|---|
| Primary | accent fill (today) | **opaque** accent (never translucent) | accent fill, raised pair; pressed inset | bright accent fill, **ink** words, 2 dp ink, hard shadow; press = translate | accent fill, square, no shadow |
| Secondary | `surface3` | tint α 0.16 + rim | raised on ground | paper + ink edge + shadow | ink 1 dp outline |
| Tertiary | words only | words only | words only | words + ink underline | words + ink underline |
| Selected chip/segment | per part (today) | brighter + accent rim | inset | accent fill + ink | inverted ink |

**One main action in solid mint per screen holds in every style.** The primary role is always the accent fill. Brutal in light may use the theme's *bright* accent tone with `text1` words when that pair reaches ≥ 4.5:1 (ink on `#3DDC8A` is 10.5:1); otherwise it uses the theme's accent and `onAccent`.

### F6 Fields
*KitField (plain, secret, composer mode), KitSearchField, KitAskField, KitOneTimeCode, the composer's field*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| `surface1` + hairline (today) | tint α 0.5 + rim; focus → accent rim | **inset well** + 1 px edge ≥ 3:1 | paper, 2 dp ink; focus → 3 dp ring outside | ground, 1 dp ink box (or underline: Q6), 0 radius |

A field always keeps a boundary of ≥ 3:1 (WCAG 1.4.11), whatever Edges says.

### F7 Transcript and technical surfaces
*KitMessage (prompt bubble, reply, thought, notice, marker), KitQueuedMessage, KitTurn (live, stall, footer), KitToolRow/KitStepCommand, KitWorkLine, KitStepTimeline/Node/Light, KitMarkdown (inline code, blockquote, table), KitCodeBlock (+ parts), KitDiffView (+ chrome, rows), KitLogPanel, KitTerminalView, KitTechnicalValue, KitTranscriptExcerpt, KitAgentStrip, KitSkeletonTranscript*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| prompt `surface2` bubble 20/20/6/20; reply plain; code `surface1` (today) | prompt tint α 0.62 (no blur); code **opaque** `surface1` (code never sits on translucency) | prompt raised on ground (uniform-radius shadow under the tailed bubble); code inset | prompt = partner-tint paper + ink edge + shadow (Q5); code paper + ink edge, **no shadow** | prompt ink 1 dp box; code ground + 1 dp rule; reply plain |

The agent's reply is plain body text in every style. No style puts a bubble on the reply (visual language §5).

### F8 Tiles, marks and small things
*KitSurface.tile, KitRowIcon, KitImage/KitAvatar, KitStatusMark, KitTaskMark, KitProgress bars, KitProgressRing, KitLevelMeter, KitContextRing/Chart/Bar, KitChart, KitQr (fixed ink/paper always), KitScanner frame, KitDivider, KitScrollbar, KitIllustration + scenes*

| Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| `surface3` tile 9 (today) | tint α 0.2 tile | inset tile | paper tile + 2 dp ink | ink-outline tile |

Dividers: Solid and Glass hairline; Soft none (spacing); Brutal 2 dp ink; Paper 1 dp ink. Illustrations and scenes stay style-agnostic (they draw on `KitPalette`), apart from the stroke weight in Brutal (+0.5). `KitQr` never changes (scanability).

### F9 Ground, type and transitions
*KitScreen ground, KitLiveBackground, KitPageRoute/KitPageTransitionsBuilder, KitText roles, KitPageHeading, KitSectionLabel, KitTopBar title*

These follow §3.6 (type), §3.11 (live), §3.3 (ground). Page transitions don't change per style.

### F10 Previews (render *another* style)
*KitAppearancePreview, KitLiveLookPreview, KitLiveStylePreview, KitLiveStylePicker, KitThemePreview, KitOptionCard (style kind)*

These use `KitStyleScope(override:)` (§4.5). They belong to the Appearance agent.

## 6. Fallbacks

| Situation | Result |
|---|---|
| **Saver** | Solid, Depth Flat, true black ground, motion off, live background off, glow off. Applied in the effective effects by the Appearance agent (RP-2). Mirrors Windows switching acrylic to solid in Battery Saver ([Microsoft Acrylic](https://learn.microsoft.com/en-us/windows/apps/design/style/acrylic)). |
| **High contrast** (`MediaQuery.highContrastOf`, iOS 13+, Android 34+) | Glass, Soft → Solid. Brutal, Paper keep. All styles: Edges on with a ≥ 3:1 colour (`text2` or stronger), Depth Flat, fills opaque, glow off. ⟂ **RP-7** (the Surfaces brief says "fall back to solid"; this extends it to the three new styles) |
| **Reduce motion** | No snapshot cross-fade, no press translation animation (the state still applies), glow still. |
| **Live background off** | Glass: no blur (nothing behind), floating α +0.1. The others are unchanged. |
| **Platform view on screen** | No blur pane above it, and no snapshot cross-fade (instant swap). |
| **Unknown or unready style id** | Solid. |
| **The high-contrast theme pack** (`ThemePackId.highContrast`) | Treated like the HC flag: the style resolves as in the HC row. Q7 |

## 7. Invariants (asserted in pure Dart for every combination)

`test/kit/style/kit_style_contract_test.dart` (baseline) runs all **5 styles × 2 brightness × 3 × 2 × 2 × 2 knobs = 480** combinations, plus Saver × HC × RTL, and asserts:

1. **Text contrast.** `text1`, `text2`, `text3` (after any glass remap), `accent` text and `attention` text are each ≥ 4.5:1 on the resolved fill of every role that carries words. For translucent fills, the fill is composited over the **worst-case backdrop**: the brightest colour of every registered `KitLiveStyle` palette at Bold intensity, over the ground. (I estimated that as accent at 55 % over ground; the test must use the real palettes.)
2. **Non-text contrast.** Field edges, the selected segment's boundary, the focus ring and the switch track are ≥ 3:1 against adjacent colours (WCAG 1.4.11).
3. **Amber.** No role except F2's needs-you looks resolves to an `attention*` colour.
4. **No red.** The resolved palette passes the existing `no_red_scan` hue check (no red-hued literal enters through a style).
5. **One main action.** Only `controlPrimary` resolves to an accent *fill*.
6. **Fast-path shapes.** Every role that casts an outer shadow has uniform radii.
7. **RTL.** Every shadow's dx flips sign under RTL. Glass rims don't.
8. **Saver and HC.** Effective style and knobs per §6.
9. **Solid identity.** `resolveKitStyle(solid defaults)` produces exactly today's `KitTokens` values and today's decorations for every role. The existing goldens are the pixel proof.
10. **Lerp.** `KitSurfaceLook.lerp(a, b, 0) == a`, `lerp(a, b, 1) == b` for every pair of looks.

## 8. Per-style acceptance checklist

A school is accepted when **all** of these hold, in **light and dark**:

**Captures** (`tool/capture/style_schools_test.dart`, 412×915, one full phone screen per image, live background frozen), the capture list:

1. Conversations home, with a needs-you card and the composer
2. Chat, running turn: live line, steps, glowing composer, a question card (needs you)
3. Chat, finished: code block, diff summary, work line folded, prompt bubble
4. Settings hub (grouped rows, section labels)
5. Appearance studio (the preview card with this style active)
6. A bottom sheet: a confirmation (`KitConfirmSheet`, destructive kind, the neutral strong button)
7. A menu open over a list (popover), and a dialog with a field
8. Servers list, with a status tag and switch rows
9. Files or a viewer with a long code surface
10. An empty state (`KitStateView`) and a loading skeleton
11. A search field focused, with the keyboard focus ring visible on the next control (desktop focus mode)
12. **RTL Arabic:** chat (2) and Settings (4) at 1.3× text
13. **320 dp at 2.0× text:** Settings (4) and the confirmation sheet (6), with no overflow
14. **Tablet 1280×800:** two-pane conversations (dark only)

**Checks:**

- [ ] Each capture is looked at next to its concept image ([schools/](schools/)), with at least **3 polish passes**. Before and after shots go in `docs/qa/design-schools-<style>-<date>/README.md` (audit-trail rule).
- [ ] AA: text ≥ 4.5:1 and non-text ≥ 3:1 (the §7 test, plus the G5 `textContrastGuideline` on the style sheet).
- [ ] 60 fps: [flutter-techniques.md](flutter-techniques.md) §9 numbers reported against Solid on the same run.
- [ ] RTL: shadows mirrored, nothing directional left uncovered.
- [ ] 320 dp at 2× text: no RenderFlex overflow (G6 on the style sheet).
- [ ] Owner rules: no red; amber only for needs-you; one main action in solid mint; no new UI level (no extra bar or row added to show the style); plain words; transitions 150–250 ms and still under reduced motion; no `flutter_animate`.
- [ ] Saver forces Solid; HC behaves per §6.
- [ ] **Every existing golden is unchanged** (Solid identity). `git diff --stat -- '*.png'` lists only the school's own new goldens.
- [ ] `flutter analyze` is clean; the G16 kit ratchet doesn't rise.

## 9. Performance budget (per style, reference scroll)

| | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|
| p90 raster Δ vs Solid | ≤ +2 ms | ≤ +1 ms | ≤ +1 ms | ≤ +1 ms |
| Live backdrop filters on screen | ≤ 3 | 0 | 0 | 0 |
| Inset-shadow instances visible | 0 | ≤ 8 | 0 | 0 |
| Frames > 16 ms after warm-up | 0 | 0 | 0 | 0 |

## 10. Reconciliation with the Surfaces branch (`feat/kit-surfaces`)

Reconciled 2026-10-21 against the merged code (`5de1d648e`):

| # | Point | What Surfaces landed | Done in the baseline |
|---|---|---|---|
| RP-1 | Style enum | `{solid, glass, soft}`, stored by name | appended `brutal, paper` (Clay dropped); no typedef needed |
| RP-2 | Saver | `KitEffects.underSaver` in `KitEffectsScope` | unchanged; `underSaver` now also resets Fine-tune |
| RP-3 | Where the style is resolved | per-call `KitSurfaceLook.styleOf(context)` | kept; `styleOf` and `knobsOf` read through `KitStyles`. No scope object (not needed yet) |
| RP-4 | Resolver signature | `decorate(context, solid:, level:, role:, …)` → `Decoration`; five roles | kept as is; each style's spec answers it |
| RP-5 | Glass and Soft values | inside `KitSurfaceLook` | moved verbatim into `style/kit_style_glass.dart` and `kit_style_soft.dart`; goldens unchanged |
| RP-6 | Static radius tokens | untouched | done (hook H5): instance tokens `fieldCorner`, `popoverCorner`, `bubbleCorner`, `bubbleTailCorner`, `pillHeight`, `panelInner`; statics kept for `const` uses |
| RP-7 | HC fallback | HC → Solid for all | narrowed: Brutal and Paper keep themselves; knobs go flat with edges on |
| RP-8 | Cross-fade | `kitSurfaceBox`: 200 ms style, 120 ms state, still under reduced motion | kept; no snapshot cross-fade (open hook) |
| RP-9 | Blur placement | one `BackdropFilter` per floating piece or sheet, bounded, sigma 18 | kept; one `BackdropGroup` per page and grouped filters (hook H11) |
| RP-10 | Goldens | `kit_surfaces_golden_test.dart` | it now iterates all five styles; it is the style sheet |
| RP-11 | Hand-built surfaces | (see the Surfaces report) | unchanged; the coordinator assigns |

## 11. Merge order

1. **`feat/kit-surfaces`** merges to int (Solid, Glass, Soft).
2. **`feat/schools-baseline`** (phase 2, landed): see §0.
3. **Per-school branches, in parallel**, one agent each, each in its own worktree from the baseline. Suggested landing order (each merges when accepted, independently):
   1. **Paper** (simplest; proves the knob plumbing and the HC keep-path),
   2. **Brutal** (proves `faceOffset` press and bold edges),
   3. **Soft** (refines Surfaces' Soft; proves inset),
   4. **Glass** (refines Surfaces' Glass; proves worst-case contrast and the blur budget),
4. **The Appearance studio's Style picker** (Appearance agent) adds Brutal and Paper cards and the Fine-tune sheet once their schools are accepted; the cards already draw through the specs.
5. **The coordinator** merges each school, runs the gates, and regenerates nothing for Solid (any Solid PNG diff is a defect).

**Ownership rule that makes 3 conflict-free:** a school agent owns `lib/ui/kit/style/kit_style_<id>.dart`, an optional `kit_style_<id>_painters.dart`, `test/kit/style/<id>_test.dart`, its `kit_surfaces_<id>_*.png` goldens, its capture outputs and its QA folder. It must not edit `kit_style.dart`, `kit_surface_look.dart`, `kit_tokens.dart`, `effects.dart`, any kit part or any screen. If it needs a hook a part doesn't expose, it asks the coordinator, who adds it in a small baseline follow-up that all schools rebase onto.

## 12. Open questions for the owner

All nine were answered "All yes" by the owner on 2026-10-21 (Q8 dropped with Clay); see [README.md § Owner decisions](README.md#owner-decisions-2026-10-21-all-yes).

---

## Appendix: every surface-making kit file

From the grep in §5 (2026-10-21, int `236b2c277`). The number is the count of matches; the family is from §5. A school agent rechecks this list against its own capture list before claiming coverage.

| Matches | File | Family |
|---|---|---|
| 40 | kit_progress_row.dart | F1 |
| 31 | kit_swatch.dart | F1/F10 |
| 29 | kit_tokens.dart | (tokens, §4.6) |
| 25 | kit_surface.dart | F1/F8 |
| 25 | kit_segmented.dart | F5 |
| 24 | kit_work_graph.dart | F1 |
| 24 | glass/kit_glass.dart | F3 |
| 22 | kit_row.dart | F1 |
| 22 | kit_option_card.dart | F10 (Appearance agent) |
| 20 | diff/kit_diff_chrome.dart | F7 |
| 17 | terminal_key_bar.dart | F3 |
| 16 | kit_menu.dart | F4 |
| 15 | kit_request_shape.dart | F2 |
| 15 | kit_nav.dart | F3 |
| 14 | kit_choice_cards.dart | F1 |
| 13 | kit_panel.dart | F1 |
| 13 | chat/kit_composer_chips.dart | F5 |
| 12 | kit_choice_tiles.dart | F1 |
| 11 | scenes/team_scenes.dart | F8 |
| 11 | kit_tappable.dart | F5 (state layer) |
| 11 | kit_divider.dart | F8 |
| 11 | kit_appearance_preview.dart | F10 (Appearance agent) |
| 11 | chat/kit_markdown.dart | F7 |
| 10 | kit_widget_preview.dart | F1 |
| 10 | kit_live_look_preview.dart | F10 |
| 10 | kit_jump_pill.dart | F3 |
| 10 | kit_chip.dart | F5 |
| 10 | kit_buttons.dart | F5 |
| 10 | kit_agent_choice.dart | F1 |
| 9 | motion/kit_motion_parts.dart | F5 (state) |
| 9 | kit_request_question.dart | F2 |
| 9 | kit_icon_button.dart | F5 |
| 8 | scenes/states_scenes.dart | F8 |
| 8 | kit_wheel.dart | F4 |
| 8 | kit_image.dart | F8 |
| 8 | kit_consequences.dart | F1 |
| 8 | kit_code_block.dart | F7 |
| 8 | kit_board_lane.dart | F1 |
| 7 | team/team_parts.dart | F1 |
| 7 | kit_term.dart | F4 |
| 7 | kit_status_mark.dart | F8 |
| 7 | kit_start_box.dart | F1/F6 |
| 7 | kit_mini_table.dart | F1 |
| 6 | kit_undo.dart | F3 |
| 6 | kit_top_bar.dart | F3/F9 |
| 6 | kit_terminal_view.dart | F7 |
| 6 | kit_step_slider.dart | F5 |
| 6 | kit_status_line.dart | F2 |
| 6 | kit_sliver_row_group.dart | F1 |
| 6 | kit_search_field.dart | F6 |
| 6 | kit_screen.dart | F9 |
| 6 | kit_log_panel.dart | F7 |
| 6 | kit_live_style_picker.dart | F10 |
| 6 | kit_choice_list.dart | F1/F5 |
| 6 | kit_ask_field.dart | F6 |
| 6 | diff/kit_diff_rows.dart | F7 |
| 6 | chat/kit_turn.dart | F7 |
| 6 | chat/kit_message.dart | F7 |
| 5 | motion/kit_tab_switcher.dart | F5 |
| 5 | live_background/live_mock_chat.dart | F10 |
| 5 | kit_viewer_bodies.dart | F7 |
| 5 | kit_status_answer.dart | F1 |
| 5 | kit_shape.dart | (tokens) |
| 5 | kit_session_tabs.dart | F5 |
| 5 | kit_notice.dart | F2 (card) / F1 (plain) |
| 5 | kit_dialog.dart | F4 |
| 5 | kit_details_fold.dart | F1 |
| 5 | kit_carousel.dart | F1 |
| 5 | chat/kit_composer_caption.dart | F3 |
| 4 | kit_request_card.dart | F2 |
| 4 | kit_receipt.dart | F1 |
| 4 | kit_progress.dart | F8 |
| 4 | kit_image_markup.dart | F8 |
| 4 | kit_field.dart | F6 |
| 4 | kit_fact_row.dart | F1 |
| 4 | chat/kit_step_timeline.dart | F7 |
| 4 | chat/kit_composer_tray.dart | F3 |
| 3 | scenes/setup_phone_scene.dart, servers_link_scene.dart, servers_cast.dart | F8 |
| 3 | kit_window_row.dart | F1 |
| 3 | kit_skeleton_transcript.dart | F7 |
| 3 | kit_sheet_parts.dart | F4 |
| 3 | kit_qr.dart | F8 (fixed) |
| 3 | kit_live_style_preview.dart | F10 |
| 3 | kit_date_time_picker.dart | F4 |
| 3 | kit.dart | (exports) |
| 3 | kit_context_region.dart | F1 |
| 3 | chat/kit_queued_message.dart | F7 |
| 3 | chat/kit_composer.dart | F3/F6 |
| 3 | chat/kit_agent_strip.dart | F7 |
| 2 | scenes/setup_ready_scene.dart, folders_open_scene.dart | F8 |
| 2 | kit_text_halo.dart | F9 |
| 2 | kit_task_card.dart | F1 |
| 2 | kit_status_tag.dart | F5 |
| 2 | kit_state_view.dart | F8 |
| 2 | kit_progress_ring.dart | F8 |
| 2 | kit_level_meter.dart | F8 |
| 2 | kit_illustration.dart | F8 |
| 2 | kit_floating_panel.dart | F3 |
| 2 | kit_checklist.dart | F1 |
| 2 | kit_chart.dart | F8 |
| 2 | chat/kit_tool_row.dart | F7 |
| 2 | chat/kit_step_light.dart | F7 |
| 2 | chat/kit_composer_classic.dart | F3 |
| 1 | team/kit_findings_card.dart | F1 |
| 1 | scenes/team_discover_scenes.dart, setup_unplugged_scene.dart, setup_steps_scene.dart, servers_welcome_scene.dart, portal_scene.dart | F8 |
| 1 | kit_viewer.dart | F7 |
| 1 | kit_vector_logo.dart | F8 |
| 1 | kit_top_bar_scope.dart | F3 |
| 1 | kit_sheet_modal.dart | F4 |
| 1 | kit_sheet.dart | F4 |
| 1 | kit_sense_ask.dart | F1 |
| 1 | kit_scanner.dart | F8 |
| 1 | kit_row_parts.dart | F1/F5 (switch, expand) |
| 1 | kit_request_row.dart | F2 |
| 1 | kit_plan_block.dart | F1 |
| 1 | kit_page_heading.dart | F9 |
| 1 | kit_one_time_code.dart | F6 |
| 1 | kit_needs_you.dart | F2 |
| 1 | kit_floating_action.dart | F3 |
| 1 | kit_feed_item.dart | F1 |
| 1 | kit_context_chart.dart | F8 |
| 1 | kit_confirm_sheet.dart | F4 |
| 1 | kit_code_block_parts.dart | F7 |
| 1 | kit_agent_card.dart | F1 |
| 1 | chat/kit_step_node.dart | F7 |
| 1 | chat/kit_composer_edge.dart | F3 (glow) |
