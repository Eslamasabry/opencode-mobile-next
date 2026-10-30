# Per-school briefs (phase 3)

*Part of [design schools](../README.md). Built from [agent-brief-template.md](../agent-brief-template.md) against the code baseline on `feat/schools-baseline` (`f93309957`; contract §0). One agent per school, each in its own worktree. They can run in parallel: each one owns only its own spec file, tests, goldens and renders.*

| School | Brief | Spec file it owns | Starts from |
|---|---|---|---|
| Paper | [paper.md](paper.md) | `lib/ui/kit/style/kit_style_paper.dart` | placeholder |
| Brutal | [brutal.md](brutal.md) | `lib/ui/kit/style/kit_style_brutal.dart` | placeholder |
| Soft | [soft.md](soft.md) | `lib/ui/kit/style/kit_style_soft.dart` | Surfaces' Soft |
| Glass | [glass.md](glass.md) | `lib/ui/kit/style/kit_style_glass.dart` | Surfaces' Glass (too opaque, see the brief) |

Suggested landing order (contract §11): Paper, Brutal, Soft, Glass. Each merges when accepted, independently.

## Hooks: built, and driven from your spec

All twelve hooks are built on `feat/schools-baseline`, before any school starts, so a school only edits its own `lib/ui/kit/style/kit_style_<id>.dart` (plus its goldens and renders). Each hook is a no-op for Solid: every existing golden is unchanged. Each has a test in `test/kit/kit_style_test.dart` (H11 in `test/kit/kit_screen_test.dart`); a made-up spec drives them through `debugKitStyleSpecs`.

| # | Hook | Your spec drives it with | Where it is read | Already set in the baseline |
|---|---|---|---|---|
| H1 | Type tweaks | `tokens.type: KitStyleType(displayWeight:, displayTracking: (em), largeTitleSize:, bodyWeight:, monoLabels:, weightBoost:)` | `KitText.styleOf` (every `KitText`) and the token text styles (`KitTokens.styled`); Arabic keeps its face and zero tracking, only weights change | Brutal (800, −0.03, body 500, mono labels), Paper (750, −0.03, 34, mono labels) |
| H2 | Face sinks on press | `resolve` returns `KitSurfaceDecoration(faceShift: …)` | the decoration's painter moves the fill, edge and pressed-in shadows; `kitSurfaceBox` moves the content (`transformHitTests: false`); mirrored in RTL | Brutal: `faceShift = offsetOf(role)` while pressed, hard shadow 0 |
| H3 | Words colour on a chosen surface | `Color? onColor(KitSurfaceRequest r)` (null keeps the part's own) | `KitSurfaceLook.wordsOn(...)`: the chosen segment (and its check), a chosen tab, a chosen chip, a secondary button's words | none (Paper's inversion and Glass's lifted words are for their agents) |
| H4 | Pills follow Corners › Sharp | `tokens.sharpRadius` (with Corners › Sharp) | `KitTokens.pillRadius`, `shapeOf(KitShape.pill)`, `pillBorder(side:)` (chip, composer chip, jump pill borders); `KitSurfaceLook.decorate` maps a stadium to it | follows every style's knobs (Paper's Sharp = 0 makes square chips) |
| H5 | Static shape tokens follow Corners and Density | nothing: the knobs | instance tokens `fieldCorner`, `popoverCorner`, `bubbleCorner`, `bubbleTailCorner`, `pillHeight`, `panelInner` (statics unchanged for `const` uses); fields, search field, menus, tooltips, the tip bubble, term bubble, prompt and queued bubbles, chips | all styles |
| H6 | Ground per style | `ThemeRoles adjustRoles(ThemeRoles roles)` (only ground, surface1–3, hairline, scrim, elevationShadow) | `kitStyleTheme` under the effects scope in `main.dart`; rebuilds `ThemeRoles` and `KitTokens`, re-runs the accent guard; memoised; one stable `Theme` wrapper so the navigator never rebuilds | none (Soft's lifted ground, Brutal's cream, Paper's newsprint are for their agents) |
| H7 | Live background policy | `tokens.live: KitLivePolicy(maxStrength:, offWhenChosen:)` | `KitLiveBackground` caps the person's Intensity (0 hides; its ticker stops); `KitSurfaceLook.choose` turns it off when an `offWhenChosen` style is picked (the Style picker calls it), still the person's switch | Soft and Brutal `maxStrength: .7`; Paper `offWhenChosen: true` (owner Q4) |
| H8 | Glow per style | `tokens.glow: KitStyleGlow(halo:, lineWidth:)` | the composer frame: no breathing halo or soft wash when `halo` is false; the classic ring and soft ring take `lineWidth` | Brutal (no halo, 2 dp), Paper (no halo, 1 dp) |
| H9 | No nested shadows | `tokens.flattensNested: true` | `kitSurfaceBox` and `KitSurfaceLook.decorate` drop what a surface inside a casting surface would cast; `KitSurfaceLook.castScope` marks the request card | Brutal |
| H10 | A plate behind the words | `resolve` returns `KitSurfaceDecoration(wordsPlate: color)`; set it per role, never per state | `kitSurfaceBox` puts a `KitTextHalo(allTones: true)` of that colour around its content, so every `KitText` on the pane gets the soft plate | none (for the Glass agent) |
| H11 | One backdrop group per page | nothing | every page `KitScreen` holds one `BackdropGroup`; `kitSurfaceBox` blurs with `BackdropFilter.grouped` | all pages |
| H12 | The calm needs-you card keeps its shadow | nothing: any style whose card decoration casts | `kitRequestFrame` clips only the attention edge to the card's corners, not the whole card (Solid keeps the old clip, pixel for pixel) | Glass light, Soft and Brutal question cards now show their outer shadow |

## Slice S0 (style cleanup, 2026-10-21): roles and rules every part uses

Built on `feat/style-s0` from the [style coherence audit](../../../audits/style-coherence-2026-10-21.md) §6, so slices S1–S7 can move the kit's parts onto the resolver in parallel, each in its own files.

**Six new surface roles** (`KitSurfaceRole`; the first five, `KitSurfaceRole.panes`, are the panes the fill, depth and blur models are tuned for). Each spec answers each one in a small `_part(r)` method called first in its `resolve`:

| Role | What asks for it | Solid | Glass | Soft | Brutal | Paper |
|---|---|---|---|---|---|---|
| `ground` | a page host, `KitSurface(level: ground)` (mapped by `kitSurfaceMaterial`) | the part's own | fill only | fill only | fill only | fill only |
| `field` | text, search and ask fields, one-time code; `selected:` = focused | the part's own | tint α 0.5, 3:1 boundary, accent when focused, the plate | inset well, 3:1 boundary, accent when focused | paper in a 2 dp ink box | 1 dp ink box α 0.9, 2 dp focused |
| `code` | code block, log, terminal, diff, command box; `color:` = its fill | the part's own | opaque `surface1` (or `color`) with the rims | a well of the opaque fill | paper in the ink box (none under Edges › Off), no shadow | opaque fill under the α 0.28 rule |
| `divider` | a line, through `KitSurfaceLook.divider(context, color:, width:)` | the part's own | the part's hairline | none (space only); the hairline under Edges › On | 2 dp ink α 0.9 (`tokens.dividerWidth`) | ink α 0.20 at the part's hairline |
| `stateLayer` | hover and press highlight; `pressed:` = held, otherwise hover or focus | the part's own | near-white α 0.06/0.10 dark, 0.28/0.42 light | a faint shade; pressed in while held | `surface3` held, ink α 0.04 hover | `surface3` held, ink α 0.04 hover |
| `toggle` | a switch's track; `selected:` = on, `color:` = the on fill; the thumb is a `control` with `circle: true` | the part's own | like a field; the on fill when on | a well with a 3:1 boundary; the on fill in the well | 2 dp ink box, the on fill when on | ink box, the on fill when on |

**Rules the resolver keeps, whatever a spec returns** (`KitSurfaceLook.resolve` / `decorate`):
- **The page never casts.** `ground` is its fill alone: no edge, no shadow, no plate, and it hosts nothing. Hook H9 therefore no longer strips every shadow below a page hosted on `KitSurface(level: ground)` (audit V1: Brutal Conversations has its hard shadows back).
- **Code stays opaque.** A `code` fill is composited over the ground and carries no plate, in every style.
- **Fields and off switches reach 3:1 at rest** in every style but Solid, every theme and every knob (`kitStyleBoundary(roles)` gives the quietest grey that does; a test holds every spec to it).
- **Nesting.** A `content` surface inside another `content` surface paints nothing of its own at rest: no second fill, edge or shadow, so what it holds sits on the outer face (audit V3: the question card's answers). Chosen or pressed, it keeps its face and casts nothing. Every style but Solid (`KitStyleTokens.keepsNestedSurfaces`, true for Solid). Sheets, floating pieces and the page do not host: a row group on a sheet keeps its panel. The request card marks its inside with `KitSurfaceLook.castScope`, which now carries the nesting rule too.
- **Solid at its defaults** (`KitSurfaceLook.isPlain(context)`) is the only time a part's own look is returned untouched. `kitSurfaceMaterial` no longer early-returns on `style == solid`, so Fine-tune (Depth › Raised, Edges › Off) reaches every `KitSurface` under Solid too.

**Gate G49** (`test/kit_ratchet_test.dart`): `BoxDecoration(`, `ShapeDecoration(`, `ColoredBox(` and `Material(color:` inside `lib/ui/kit/`, outside the resolver files and the exempt list (`_styleExemptFiles`: QR, scanner, progress, charts, markup, swatches, scenes, illustrations, the live background, page and arrival washes, needs-you, day circles), per file, only shrinking (baseline 94 files, 226 hits at `a88017d32`). When a slice moves a part, rewrite the smaller numbers with `KIT_RATCHET_WRITE=1 KIT_RATCHET_GATES=G49`.

## Slice S9 (style cleanup, 2026-10-21): the last shared hooks

Built on `feat/style-s9`, so the Brutal and Paper polish agents (and Glass and Soft) only change values in their own spec file. Each hook is a no-op for Solid, and **every default answers what each style drew before**: no real spec sets any of them yet. A made-up spec drives each one in `test/kit/kit_style_hooks_s9_test.dart`; the parts are in the `kit_surfaces_hooks_<style>_<mode>` gallery.

| # | Hook | Your spec drives it with | Where it is read | Default (today) |
|---|---|---|---|---|
| S9-1 | How "Needs you" looks | `tokens.needsYou: KitNeedsYouLook(tag:, tint:, ring:, startEdge:)`; `tag: KitNeedsYouTag.pill` sets the words as a pill in `attentionFill` drawn as your `control` with `color: attentionFill` (Brutal: the ink edge comes from your control answer) | `kitRequestFrame` (tint, the attention line and outer ring, the 4 dp start edge) and `kitRequestNeedsYouTag` (the full card's caption and the question look's "● Needs you · 20 s") | every null follows `glow.halo`: with a halo the full card is tinted and ringed; without, the start edge alone. The question look is never tinted or ringed. So Soft (start edge, no fill) sets `tint: false, ring: false`; Paper the same; Brutal `tag: pill, tint: false, ring: false, startEdge: false` |
| S9-2 | Dividers between groups vs within one | `resolve` for `divider` reads `r.withinGroup` (an inset line between the rows of one group) | `KitSurfaceLook.divider(withinGroup:)`, passed by `KitDivider` for every inset line (`KitDividerInset.gutter`/`.text`); an edge-to-edge line (between groups, the destructive divider) asks without it | ignored: both alike. Brutal "ink between groups, space within": transparent when `withinGroup` |
| S9-3 | The plate stops at an opaque styled surface | nothing | `KitTextHalo._covers` reads a `KitSurfaceDecoration` fill (α > 0.9) as covering, like a `BoxDecoration` | fixed for all: an opaque face inside a Glass pane (a latched key, a primary button, code) no longer takes the pane's H10 plate |
| S9-4 | One prompt bubble | your `content` answer at `surface2` | `kitPromptBubbleDecoration(context, edge:)` (kit_message.dart), used by `KitMessage.prompt` and `KitQueuedMessage` (`edge: false`: Solid's queued bubble has no Accent edge) | unchanged |
| S9-5 | Flush-start rows in a lazy group | your `content` answer at `surface1`: return it bare (no fill, edge or shadow) | `KitSliverRowGroup` now has `KitRowGroup`'s reach rule; both use `KitSurfaceDecoration.isBare` | Paper's lazy lists (Settings hub, Work) go flush with the label, as its row groups already did |
| S9-6 | The section rule right above the words | `resolve` for `divider` with `selected: true`: a `KitSurfaceDecoration(fill: <ink>, edgeWidth: <rule width>)` different from your row divider | `KitSurfaceLook.sectionRule`, drawn by `KitSectionLabel` after its gap with `space2` to the words (every section label: row groups, lazy groups, sheets) | none (no spec answers it apart) |
| S9-7 | Press passed down | `resolve` with `r.pressed` (your H2 `faceShift`) | `KitTappable.pressedOf(context)`: the floating "+" asks `floating` with `pressed:` while held | Brutal's "+" now sinks into its 6 dp shadow while held (its spec already answered `pressed`) |
| S9-8 | Link underline | `tokens.linkUnderline` (a multiple of the words' stroke; 0 none) | `KitSurfaceLook.linkUnderline`, read by the tertiary `KitButton` | null: as the `divider` role draws a line (Brutal 2, Paper 1, none elsewhere) |
| S9-9 | The highlight on a filled control | override `Decoration? resolveFilledLayer(KitSurfaceRequest r)` (`r.color` is the control's own fill, the accent) | `KitSurfaceLook.resolve` for `stateLayer` asked with `color` (the held primary `KitButton`) | null: the part's own layer (16 % of its words' colour), so the accent is never greyed by an opaque `surface3` |
| S9-10 | The field halo, and field hover | `tokens.fieldHalo` (bool); `resolve` for `field` reads `r.hovered` | `kitFieldHalo(context, look:, radius:, halo:)` (kit_field.dart), used by `KitAskField` and `KitStartBox` so they agree; `KitField` asks `field` with `hovered:` | `fieldHalo` null: as `glow.halo` (the start box's rule), so the ask field now glows under Glass and Soft like the start box; hover ignored |
| S9-11 | The one-time code is a field | your `field` answer | `KitOneTimeCode` draws `kitFieldFrame` (Solid's inset fill) through `role: field` | no longer `KitSurface.inset` (`pressed: true`), so it stops sinking under Brutal and reads as a value in every style |
| S9-12 | A control that cannot be used | `resolve` reads `r.disabled` | the disabled `KitButton` (primary and secondary) asks its `control` with `disabled: true` | ignored: drawn like an enabled control; Brutal and Soft ("disabled = flat, no shadows") answer it flat so it never reads as a secondary |

`KitSurfaceRequest` therefore gained `hovered`, `disabled` and `withinGroup` (and `KitSurfaceLook.decorate`/`resolve` the same arguments). A spec that ignores them answers as before.

## How a part asks for the style (slices S1–S7)

1. **Pick the role** by what the surface *is*, not by how it looks today: a pane (`content`, `control`, `track`, `floating`, `sheet`) or a part's own (`field`, `code`, `divider`, `stateLayer`, `toggle`; `ground` only for a page host). Never read the style enum in a part, never branch on a style name: what differs between styles belongs in the specs (`resolve`, `onColor`, the tokens).
2. **Build the part's own decoration exactly as it paints today** and pass it as `solid:`. That is what Solid at its defaults returns, untouched, so Solid stays pixel-identical with no branch in the part.
3. **Ask the resolver** with the state: `KitSurfaceLook.decorate(context, solid: mine, role: KitSurfaceRole.field, level: …, color: <a part's own fill, such as the accent>, selected: <chosen / focused / on>, pressed: <held>, circle: …, borderRadius: …)`.
4. **Paint it** with `kitSurfaceBox(style: KitSurfaceLook.styleOf(context), decoration: d, blur: KitSurfaceLook.blurs(style, role), blurSlot: <true for floating and sheet>, borderRadius: …, child: …)`. It cross-fades style changes (200 ms) and state changes (120 ms), at once under reduced motion, keeps hook H2's face shift and H10's plate, and marks the inside for H9 and the nesting rule. A part with a `Material` host uses `kitSurfaceMaterial(context, level:, shape:, fill:, role:, …)` instead, which keeps the Material path while `KitSurfaceLook.isPlain(context)` holds.
5. **Words on it:** `KitSurfaceLook.wordsOn(context, fallback: <today's colour>, role:, selected:, color:)`. **A line:** `KitSurfaceLook.divider(context, color: <today's colour>, width: <today's width>)` and draw what it returns (a transparent colour means no line: keep the space).
6. **Check:** the part's existing goldens do not move (`git diff --stat -- '*.png'`); add its non-Solid rows to `test/goldens/kit/kit_surfaces_golden_test.dart`; lower its G49 count. If a role cannot say what the part needs, ask the coordinator for a role or a token rather than painting around it.

**Colours (gate G17, `test/kit_ratchet_test.dart`):** no `Color(0x…)` literal may appear in `lib/ui`, spec files included. Derive every colour from the theme's roles (`Color.alphaBlend`, `Color.lerp`, `withValues(alpha:)`); the hex values in the school guides are targets to reach from the roles, not literals to paste. A colour no role can make is a new `ThemeRoles` role in every pack: ask the coordinator.

**Nothing is open.** If a school finds a part that still hides its look, ask the coordinator for a new hook rather than working around it in a part.
