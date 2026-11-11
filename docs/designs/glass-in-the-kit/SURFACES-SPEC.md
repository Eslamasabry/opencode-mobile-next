# Kit surfaces: every part gets its face from the resolver

Inventory taken read-only from `~/Storage2/wt/liquid-merge` (branch `native/liquid-merge-one-body`). Input to the glass
session's step 3 (`docs/designs/glass-in-the-kit.md`). Paths: `K` = `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/`,
`C` = `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/`,
`T` = `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/`. Line numbers are of that tree.

## 1. The rule

A kit part states WHAT it is. The resolver `kitSurfaceLook(KitSurfaceInput(...))` decides how that looks in the style in force.

A part MAY: call `kitSurfaceLook`, apply `look.modifier`, read `look.words` and `look.shape`; pass `ownFill` (its Solid look, used
only by Solid); pass `color` (a deliberate tint such as the attention wash, kept by every style); draw marks (dots, rings, progress
tracks, glyphs) and content grounds that are not a pane (section 4 allowlist).

A part MAY NOT: call `.background(colors.surfaceN | colors.ground)`, `.border(...)`, `.shadow/dropShadow(...)` to make a pane; hold
blur/tint/alpha/rim numbers for glass; call `kitLiquidFace` itself or test the style (`Style.Glass`, `Style.Solid`, a slab flag) to pick
a face. A Solid-only hairline today is `if (solid) Modifier.border(...)` after `look.modifier` (the `KitFab` / `KitNavRail` pattern); it
is tolerated until the `ownEdge` parameter (N2) exists, and counted by the ratchet.

Inputs of the request:

| Input | Meaning |
|---|---|
| `role` | What the surface is for (table below). It, not the part, selects the style's recipe. |
| `shape` (`KitShapeId`) | Square, Tile, Code, Button, Panel, Card, Dialog, Sheet (top corners only unless `roundSheetBottom`), Pill, Circle. Radius is the token under the style's Corners knob and Glass's pane growth (+4 dp). `radius` / `exactRadius` override (popover 12, composer, nested thumb). |
| `level` | Solid step the part stands for: Ground, Surface1, Surface2, Surface3. Glass uses it for the tint and for how thick the pane must be for words to read (`GlassFinish.thickened`). |
| `ownFill` | The part's Solid look. Solid paints it under the shape and nothing else. Other styles ignore it except as the reference for word contrast. |
| `color` | A tint the part insists on (accent, attention wash). Kept as the fill in every style (Glass: translucent tints still frost). |
| `selected` / `pressed` / `disabled` / `action` / `withinGroup` | State; Glass lifts the tint (.12 selected, .08 pressed); `selected` Content/Control becomes accent-tinted chosen frost; `action` marks a pressed command; `withinGroup` only Brutal reads it. |
| `faceAlpha` | Composer on the chat veil only. |

| `SurfaceRole` | Solid | Glass (clear panes on: Background on, blur available; else thick fallback) |
|---|---|---|
| `Content` | `ownFill` (surface1/2) | Clear pane: tint .16 dark/.22 light, blur 6 dp, sheen, specular, lit rim. Frost kind: holds reading content. Thick fallback .42/.50 + 32 dp shadow. |
| `Control` | `ownFill` | Clear pane tint .20/.24, blur 6; chosen = accent-tinted frost with accent rim. Chips, buttons, pills at rest. |
| `Track` | `ownFill` | Clear pane .14/.20: segmented track, tab bed. |
| `Field` | `ownFill` + part's edge | Clear pane .18/.24, boundary edge or accent when selected. |
| `Toggle` | `ownFill` | Thin tint .28/.38, no blur; on = own colour. |
| `Floating` | `ownFill` | Frost pane (tint .50/.62, blur 16, 12 dp shadow). Today the only way a floating part gets glass; it has no lens (see LENS). |
| `Sheet` | `ownFill` | Frost pane (tint .58/.72, blur 16). Sheets/menus add a lens through `kitLiquidSheetFace`/`kitLiquidMenuFace` first. |
| `Code` | `ownFill` | Opaque `ownFill` over ground with rims. Not glass: code and tool output read still. |
| `Ground` | page ground | `styleGround`: the page plate. |
| `Divider` | hairline | hairline fill (KitDivider owns it). |
| `StateLayer` | pressed/hover wash | White wash .06/.10 dark, .28/.42 light. |

Skill mapping (`liquid-glass/references/audit.md`): floats over content (bars, composer, menus, sheets, chips over content, pills, round
action, toasts) = liquid, via a lens request; holds reading content (cards, row groups, settings groups) = frost = `Content`; code,
diff, viewers, marks, text = not glass = `Code` or allowlist. Frost inside frost is wrong: a well inside a pane takes no frost (N1).

## 2. Inventory

`F` = the site is INSIDE a file the other builder is editing (`native/ui/design/**`, `foundation/KitSurface.kt`, `KitSurfaceGlass.kt`,
`KitDivider.kt`, `effects/KitGlassPanes.kt`, `live/**`, `row/KitRecentRow.kt`, `row/KitRowGroup.kt`, `ChatsScreen.kt`). F rows are for
the glass session; the sweep agent does not touch them. "Gated" = already behind an `isSolid`/`Style.Solid` check.
Behind: P = page (aurora or ground), L = content scrolling under it, G = inside another pane, C = card/list pane.
Verdict codes: MOVE-role (existing role fits; the request is in the row), MOVE-new (needs N1/N2/N4), ALLOW, LENS, ?.

### 2a. Panes painted in every style (the real outliers)

| ID | file:line | Part | Paints / states | Behind | F | Verdict |
|---|---|---|---|---|---|---|
| P01 | K modal/KitToast.kt:125-126 | `ToastPill` | surface3 + hairline, all styles. Host has no caller in the app: dead. | L | - | MOVE-role `Floating, Pill, Surface3, ownFill=surface3`; Solid hairline gated. Or delete (owner call). Later LENS. |
| P02 | K modal/KitUndoBar.kt:129-130 | `KitUndoBar` | surface2 + hairline, all styles | L (list) | - | MOVE-role `Floating, Panel, Surface2`. First explicit-lens user in step 2. |
| P03 | K teaching/KitTip.kt:210-211 | `KitTipBubble` | surface2 + hairline | L | - | MOVE-role `Floating, Card, Surface2, radius=POPOVER_CORNER`. |
| P04 | K action/KitIconButton.kt:246 | tooltip bubble | surface2, popover radius | L | - | MOVE-role `Floating, Card, Surface2, radius=POPOVER_RADIUS`. |
| P05 | K modal/KitSideSheet.kt:168 | `KitSideSheet` panel | surface1, all styles | P+L (scrim) | - | MOVE-role `Sheet, Square, Surface1`. `liquidShapeOk` rejects square: it stays frost, as the note says. |
| P06 | K state/KitSummaryCard.kt:79 | summary card | surface2 | C | - | MOVE-role `Content, Card, Surface2`. |
| P07 | K state/KitSummaryCard.kt:96 | detail well in P06 | surface1 inside surface2 | G | - | MOVE-new N1 `Inset, Code shape, Surface1`. |
| P08 | K choice/KitAgentChoice.kt:115-116 | agent choice card | surface1 + hairline | P | - | MOVE-role `Content, Card, Surface1`; Solid hairline gated. |
| P09 | C tool/KitCommandBox.kt:94-95 | shell command box | surface1 + hairline | transcript | - | MOVE-role `Code, Code, Surface1`; hairline gated. |
| P10 | C tool/KitToolOutput.kt:49-50 | tool output box | surface1 + hairline | transcript | - | MOVE-role `Code, Code, Surface1`; hairline gated. |
| P11 | C request/KitRequestTargets.kt:72-73, 156-157 | target wells (2) | ground + hairline inside request card | G | - | MOVE-role `Code, Code, Ground, ownFill=ground` (precedent `KitInboxRequestRow.kt:92`). |
| P12 | C request/KitPermissionCard.kt:224 | subject well | ground, Code radius | G | - | MOVE-role `Code, Code, Ground`. |
| P13 | C form/KitFormSheet.kt:128-130 | form card | surface1 then attentionSurface wash + attentionLine | P | - | MOVE-role `Content, Card, Surface1, color/ownFill=attentionSurface.compositeOver(surface1)` (precedent `KitAgentCard.kt:276`); line gated. |
| P14 | C request/KitRequestFrame.kt:94-96, 137-143 | request card, question look | same wash; question look adds surface1 + left attention bar + line. Signal returns `Modifier` (keep). | P | - | MOVE-role as P13. The 4 dp bar (141) and ring (105) are marks: ALLOW (A12). |
| P15 | C card/KitAgentCard.kt:857-858 | card list slot | attentionSurface + attentionLine | C | - | MOVE-role `Content, Card, color=attentionSurface`. (:276-314 already via resolver.) |
| P16 | C composer/KitVoicePill.kt:72-73 | voice pill | attentionSurface or surface2, + line, 20 dp | L (bottom block) | - | MOVE-role `Floating, Panel, radius=20, Surface2`; attention = `color`. Later LENS (floats over chat). |
| P17 | C composer/KitStopAndSendPill.kt:57 | stop-and-send pill | attentionFill | L | - | MOVE-role `Control, Pill, action=true, color=attentionFill`. |
| P18 | C card/KitAgentStrip.kt:116 | agent pill | surface3 circle | C | - | MOVE-role `Control, Pill, Surface3`. |
| P19 | C composer/KitComposerBox.kt:520 | stop/send disc | surface3 or text1 | G (composer) | - | MOVE-role `Control, Circle, action=!stopping, ownFill=...`. Send already does (:399). |
| P20 | C composer/KitComposerTray.kt:154 | tray disc (non-Glass path) | surface3; Glass shows a bare glyph by a style test (:128) | G | - | MOVE-role `Control, Circle, Surface3`; then the `Style.Glass` branch goes: the resolver gives the glyph's disc its glass. Unsure whether owner wants the bare glyph kept (2026-11-08 call): keep as a request-driven look. ? |
| P21 | C composer/KitComposerBox.kt:288-315, 296 | caption chip, wordless chip | accent/text1 tint on `LocalKitComposerSlab`, else surface3 | G | - | MOVE-new N1 `Inset, Pill/Circle, Surface3`; slab flag goes (tint is the role's). |
| P22 | C composer/KitComposerLimitStrip.kt:133 | limit strip | surface1 | L (above box) | - | ? role Track vs Inset; reads like N1 `Inset, Card`. |
| P23 | C transcript/KitPromptBubble.kt:127-128 | user prompt bubble | bubble fill + hairline; one per message in a lazy list | L | - | MOVE-new N1 (not `Content`: frost per bubble in a scrolling list costs one blur each and swims). |
| P24 | C markdown/KitPictureTiles.kt:97 | image tile | surface2, 8 dp | transcript | - | MOVE-new N1. |
| P25 | C markdown/KitMarkdown.kt:598 (+578 border) | table header band + cell border | surface1 band | transcript | - | MOVE-new N1 for the band; 578/604 hairlines are rules: ALLOW (A12). |
| P26 | K frame/KitScreen.kt:518, 942 | bar / bottom-block ground when not liquid | `background(ground)` | L under chrome | - | LENS group G3 (frame chrome). |
| P27 | K row/KitSliverRowGroup.kt:119 | lazy row group panel (used by ChatsScreen home) | surface1 slice per row with first/last corners; flat in Solid | P | - | MOVE-new N4 (slice request). HIGH RISK. |
| P28 | K row/KitOpenSwitchRow.kt:47 | highlighted row | surface3 | C | - | ? Probably the chosen-row face (`kitGlassChosenRow` is used by `KitRow.kt:239`, `KitChoiceList.kt:245`): call the same path. |
| P29 | K row/KitRowParts.kt:163, K row/KitConversationRow.kt:278 | row icon tiles | surface1/surface3 tile fill | G | - | MOVE-new N1 `Inset, Tile`. Many per list: tint only. |
| P30 | K motion/KitRefresh.kt:179 | pull-to-refresh disc | surface2 circle | L | - | MOVE-role `Floating, Circle, Surface2`. |
| P31 | K motion/KitMotionBuilders.kt:98-115 | `KitAnimatedBox` | fill by `level` (animated), optional hairline, any shape | any | - | ? Generic: needs a `role` parameter (default Content) before it can move. Callers decide. |
| P32 | T terminal/KitTerminalKeyBar.kt:557-575 | `terminalSurfaceLook` | A second copy of the resolver (`state.spec.resolve(request)`), skips clear panes, thickening, fades; Solid branch `background(ownFill)` | keybar | - | MOVE-role: delete the copy, call `kitSurfaceLook` (same inputs). Watch radius: it passes `StyleResolver.corner` itself, use `exactRadius`. |
| P33 | T terminal/KitTerminalKeyBar.kt:605-614, 646-647 | key caps, calm cap edge | surface2/3 caps, hairline/accent edges | G | - | Caps already come from P32's look; the edge at 646-647 is gated by `calm`. After P32: MOVE-new N2. |

### 2b. Solid-only paints next to a correct resolver call (gated; low priority, N2)

All are `look.modifier` plus a Solid hairline/fill; other styles are right. Move when `ownEdge` exists; until then they are the
tolerated pattern.

| ID | file:line | Part |
|---|---|---|
| S01 | K input/KitField.kt:504-507, 679 | field (framed, inline) surface1 + edge |
| S02 | K input/KitAskField.kt:196 | ask field |
| S03 | K input/KitStartBox.kt:92 (+ halo `dropShadow` 98-111, gated by `fieldHaloShown`) | start box |
| S04 | K action/KitIconButton.kt:277-286 | round icon button hairline + light-theme shadow |
| S05 | K nav/KitNavRail.kt:140 (lens fill surface3 at 132) | nav rail edge |
| S06 | K frame/KitShellBar.kt:99 | shell bar pill edge |
| S07 | K choice/KitWheel.kt:101, KitOptionCard.kt:833, KitStepSlider.kt:249, KitChoiceTiles.kt:230, KitChoiceCard.kt:134, KitSegmented.kt:233, KitModeSegmented.kt:568 | option/segment edges |
| S08 | K team/KitBoardLane.kt:333, K nav/KitSessionTabs.kt:86-92 | lane / tab edge |
| S09 | C composer/KitComposerBox.kt:201 | composer hairline (Solid) |
| S10 | K KitButton.kt:254 | `look?.modifier ?: Modifier.background(easedFill)`: a `?:` fallback of the resolver (no look = Solid path) |
| S11 | K state/KitActionCard.kt:50, agent/KitAgentTile.kt:106, input/KitSearchField.kt:213-225, row/KitFactRow.kt:118, state/KitStatusAnswer.kt:123 | `solid` flags next to resolver calls |

Verdict for all S rows: MOVE-new N2 (batch 6). Not part of "existing role outside fence" until N2 lands.

### 2c. LENS sites (`kitLiquidFace` and hand-built glass faces)

| ID | file:line | Part | Floats over | Class | F | Verdict |
|---|---|---|---|---|---|---|
| L01 | C composer/KitComposerBox.kt:187 (`Composer`, joined to jump pill, touch light, `LocalKitLiquidChrome`) | message box | transcript | liquid | - | LENS: `liquid = LiquidRole.Composer`, join/touch as modifiers of the returned face |
| L02 | K frame/KitScreen.kt:321, 484-485 (`Bar`, `LiquidBarShape`, joins) | top bar capsule | page body / transcript | liquid | - | LENS `Bar` |
| L03 | K frame/KitJumpPill.kt:320 (`Pill`, join); fallbacks 291-292, 344 (`kitBackdropVeil` + hairline) | jump pill | transcript | liquid | - | LENS `Pill`; both fallbacks go to Floating |
| L04 | K chip/KitChip.kt:131 (`Pill`, `chosen`); style test 115 | chips over chat chrome | transcript | liquid (calm) | - | LENS `Pill`; chosen = `selected`. In flow, away from chrome it is `Control` already |
| L05 | K action/KitFab.kt:136 (`Action`) | + / round action | list (via `kitGlassSource`) | liquid | - | LENS `Action` |
| L06 | K modal/KitMenu.kt:610, 616-617 (`kitLiquidMenuFace ?: kitGlassSheet ?: background(surface2)+border`) | menu | page | liquid, frost under Frosted look | - | LENS `Menu` (modal; budget; no lens over a modal) |
| L07 | K modal/KitSheet.kt:573-574, 591 (`kitModalGlassFace ?: background(surface2)`); KitDialog.kt:192, 210 | sheet, dialog | page | liquid (sheet), reading content inside | - | LENS `Sheet`; dialogs also; Solid fallback = ownFill |
| L08 | K foundation/KitSurfaceGlass.kt:321 `kitGlassSheet` | Sheet pane for menu/sheet/dialog | page | frost | F | duplicate resolver call: fold into `kitSurfaceLook` (glass session) |
| L09 | K frame/KitTopEdge.kt:335-352 | chat-header blur: own `FrostRole.ChatHeader`, `LiquidEdge.face(Bar).fill`, `RIM_ALPHA_*`, own clip | transcript | liquid/frost | - | LENS-new: a glass face built outside the resolver with its own numbers; becomes the `Bar` request with an "edge-to-edge under status bar" shape. ? what the status-bar part needs |
| L10 | K foundation/KitBackdropVeil.kt:66-72, KitFrost.kt, KitFrostHeader.kt | chrome veil: tint 76 %, fallback 92 %, sigma 22 dp; used on Solid too (`composerFaceAlpha` .76) | transcript | not a glass recipe | - | ALLOW as engine for now; numbers are Solid-chrome numbers, not glass. ? the veil for Glass is the lens/frost path |
| L11 | K choice/KitGlassLookPicker.kt:471; KitOptionCard.kt:787 (`kitSurfaceGlass(face.copy(frost=3f))`) | look previews | own picture | shows another look | - | ALLOW (A09) |
| L12 | K effects/KitLiquidGlass.kt, KitLiquidJoin.kt, KitLiquidTouch.kt, KitGlassPanes.kt:88-98 | the engine; `PANE_RIM_*`, `CHOSEN_RIM_*` constants | | engine | F (KitGlassPanes) | ALLOW (engine); rim numbers move into `GlassLook` (note step 1a) |
| L13 | feature chat/ui/ChatScreen.kt:885 `kitFrostSource`; chats/ui/ChatsScreen.kt:278 `kitGlassSource` | features mark their scroll content as the source | | recording marker, not a paint | ChatsScreen F | ALLOW (A13). No feature touches glass faces otherwise. |

LENS decision rule (moves into the resolver): a part whose job is to float over scrolling content (L01-L07, P01, P02, P16, P30)
passes `liquid: LiquidRole?` (N5). Role alone does not choose the lens (the eight `Floating` users stay frost). The resolver
returns the lens face when a source is in scope, the budget grants it, and the look has a lens; else the part's `Floating`/`Sheet`
pane. Join, touch light and ink-flip hosts attach to the returned face as modifiers. No scope flag (`LocalKitComposerSlab`,
`kitChromeIsGlass()`, `LocalKitLiquidChrome != null` as a style switch) decides the face.

### 2d. Direct paints that must stay (ALLOW)

| ID | Sites | Reason |
|---|---|---|
| A01 | K swipe row `row/KitSwipeRow.kt:104,126` | backing must cover the action |
| A02 | scrims: `modal/KitSheet.kt:408`, `KitDialog.kt:138`, `KitFloatingPanel.kt:62`, `T viewer/KitViewerFrame.kt:310` | scrim over page, not a pane |
| A03 | code/diff/log/terminal grounds: `C code/CodeCorner.kt:100,198`, `C diff/KitDiffView.kt:250,259,346,375,433,520,615,654,709-788`, `C diff/KitDiffPreview.kt:232,264,291`, `C diff/DiffCodeText.kt:57`, `C markdown/KitMarkdown.kt:490` (quote rail), `T viewer/KitViewerCodeBody.kt:120,180,237`, `T log/KitLogPanel.kt:165,301,485,619`, `T terminal/KitTerminalView.kt:426-429,571-665,959` | read-still code ground (`detailsSurface`: surface1 dark, ground light); diff line tints; ANSI palette. Diff bars 433/654/615 are chrome of the diff view, same family |
| A04 | viewer/scanner/image/PDF/web: `T viewer/KitPdfPage.kt:128-138,239`, `KitViewerFrame.kt:164,302`, `KitViewerTableBody.kt:58,151`, `KitWebPreview.kt:77`, `qr/KitScanner.kt:251`, `qr/KitQr.kt:104-105` (paper must stay light), `image/KitImageView.kt:217,286,309` | page of a document or camera, not chrome; QR needs a fixed light quiet zone |
| A05 | theme/look previews that show another theme's or look's colours: `K theme/KitThemePreview.kt:67-116`, `choice/KitSwatch.kt:273-357`, `choice/KitOptionCard.kt:198,257-264,787`, `choice/KitGlassLookPicker.kt:323,439,471`, `guide/KitAppearancePreview.kt:60-67`, `KitWidgetPreview.kt:153-276`, `T/live` previews | they must show colours the current style would not give |
| A06 | progress and gauge tracks: `state/KitProgress.kt:193`, `KitStepProgress.kt:67`, `KitProgressRow.kt:50`, `KitWindowRow.kt:165`, `KitLoadingBar.kt:62`, `KitChecklist.kt:513`, `T usage/KitLimitBar.kt:61-75`, `team/TeamParts.kt:242`, `KitTaskCard.kt:455`, `KitCarousel.kt:85-97`, `C card/KitAgentCard.kt:618`, skeletons `state/KitSkeleton*.kt` | data marks, not surfaces |
| A07 | dots, rings, badges, glyph tiles: `KitStatusTag.kt:52`, `KitStatusPill.kt:98`, `KitShellBar.kt:152`, `KitChip.kt:194`, `KitInboxEmptyAnswer.kt:68-75`, `KitRowParts.kt:248-249`, `KitNumberedStep.kt:71,115`, `KitNeedsYou.kt:79`, `KitTabStrip.kt:356`, `KitNavDestination.kt:110`, `KitTaskMark.kt:144`, `KitChoiceTiles.kt:192`, `KitChoiceList.kt:380-388`, `KitGuide.kt:354,375,219`, `KitSwitchRow` colours (365-378), `KitSummaryCard.kt:85`, `C KitToolRow.kt:312`, `KitMessageNotice.kt:187`, `KitTurnLiveLine.kt:95`, `KitStepTile.kt`, `KitToolNode.kt`, `KitStepTimeline.kt`, `KitComposerLimitStrip.kt:174` | status colours and marks |
| A08 | focus rings, selection rings: `KitContextRegion.kt:71`, `KitIconButton.kt:150`, `KitTopBar.kt:475`, `KitSessionTabs`, `KitSearchField` focus, `KitGuide.kt:176`, `KitWheel.kt:217` | focus is not a surface |
| A09 | edge fades to ground: `chip/KitFilterChips.kt:78`, `T terminal/KitTerminalKeybar.kt:89-104` (mask), `C transcript/TranscriptList.kt:204-228` (jump fade), `C code/CodeOverflow.kt:52-75`, `K frame/KitScreen.kt:888-936` (header fade, bar mask) | scroll-edge fade, skill rule 2 |
| A10 | `K style/KitSignalSlip.kt` | the Signal style's own face |
| A11 | `KitTheme.kt:160`, `MaterialBridge.kt`, `KitThemeFade.kt`, `KitThemeSwitchColors.kt:72`, `KitGrow.kt:181-210`, `KitTaskCard.kt:436-437` (glyph), `LivePalette.kt:29` | colour plumbing and glyphs, no pane |
| A12 | attention rings/bars/rules: `KitRequestFrame.kt:105,141,176`, `KitAgentCard.kt:288`, `KitMarkdown.kt:578,604`, `KitDiffView.kt:445,453` | accent marks (the ring is the Needs-you signal in all styles) |
| A13 | `ChatScreen.kt:885`, `ChatsScreen.kt:278` | see L13 |
| F-ALLOW | F files: `K live/KitLiveBackground.kt:138,145` (page plate), `live/KitLiveLookStill.kt:102,192`, `live/KitLiveLookPreview.kt:134`, `live/LiveShaderLayer.kt:105,225`, `foundation/KitDivider.kt:73` (the divider's own painter), `foundation/KitSurface.kt` Solid branch (`background(own, shape)` is the resolver) | page ground, previews, the resolver itself |
| F-MOVE | F files: `row/KitRowGroup.kt:154,158` (`HairlineColumn` draws its own lines with `colors.hairline`), `row/KitRecentRow.kt:327` (`KitHairlineColumn`) | MOVE-role: draw through the `Divider` role / `KitDivider` look so Glass's inset and fade apply (note step 1c). For the glass session. |

Not outliers: ~60 calls `kitSurfaceLook(... ownFill = colors.surfaceN)` (the correct pattern; `ownFill` is Solid's look), including
KitTopBar:730, KitStatusPill:75, KitSegmented:244/360, KitChoiceTiles:199, KitRowGroup:73, KitRecentRow:311, KitNavRail:112,
KitDialog:193/211, KitFloatingPanel:55, KitZoom:102, KitTerminalView:835, KitViewerFrame:340, KitComposerBox:156.

## 3. New resolver roles and parameters (once; resolver's owner only)

| ID | Addition | Solid | Glass | Soft / Brutal / Paper / Signal |
|---|---|---|---|---|
| N1 | `SurfaceRole.Inset`: a well/tile/chip inside a pane or in a scrolling list | exactly `ownFill` under the shape (no change) | tint only, about .10-.14 dark / .14-.20 light, no blur, no shadow, no specular, no rim unless `edge`; thickened as far as `GlassFinish` needs for words | `null` -> part keeps `ownFill` = what it paints today (zero change). Recipes for those styles are a later decision. |
| N2 | `KitSurfaceInput.ownEdge: Color? = null` (+ width, default `kitHairline()`) | draws that border exactly as the part does now | ignored (rim from recipe) | ignored |
| N4 | slice request for `KitSliverRowGroup` (`first`/`last` corners) | per-slice corners with `ownFill`, flat rows as today | needs an owner call: a frost pane per lazy slice cannot blur as one pane. Options: the lazy list draws ONE pane behind itself, or Glass stays tint-only per slice. | as Inset |
| N5 | `KitSurfaceInput.liquid: LiquidRole? = null` (+ source/budget in the overlay level, per note step 2) | ignored: `ownFill` | lens face if recording + budget + look lens, else Floating/Sheet pane | ignored |
| N6 | `KitAnimatedBox(role = Content)` parameter (P31) | default keeps today | by role | by role |

N3 is not used. Everything listed as MOVE-role needs none of these.

## 4. Batches

Rule for all: Solid output must be identical (same fill under the same shape, same hairline). Moving a part from "paints Solid look
in every style" to the resolver changes it in Soft, Brutal, Paper and Signal as well, and in Glass: that is the fix, but it is a visible
change in styles the brief says must stay. Where there is a golden in those styles it must not move (section 6); where there is none
the part gains the style's face. Owner decision O1: accept that, or hold those styles (a `null` for the role in those specs, as N1).

**Batch 1: MOVE with an existing role, outside the fenced files.** One file per site; no two sites share a file except where noted.
P01-P06, P08-P19, P30, P32 (P32 alone: whole terminal key bar, verify radius), P20 only if the owner confirms the bare glyph.
Disjoint files: KitToast, KitUndoBar, KitTip, KitIconButton, KitSideSheet, KitSummaryCard (P06 only), KitAgentChoice, KitCommandBox,
KitToolOutput, KitRequestTargets, KitPermissionCard, KitFormSheet, KitRequestFrame, KitAgentCard, KitVoicePill, KitStopAndSendPill,
KitAgentStrip, KitComposerBox (P19 only; L01/S09/P21 later in the same file: one hand), KitRefresh, KitTerminalKeyBar.
Edits per file are 1-5 lines; one hand for a whole batch is hours, not days.

**ALLOW list**: 2d, written into the guard's allowlist file with these reasons (section 5). Lands first, alone: it makes the count meaningful.

**Waiting list NEW (needs section 3 first; resolver's hand)**: P07, P21, P23, P24, P25, P29 (N1); S01-S11 and P33 (N2); P27 (N4 +
owner call); P31 (N6); P22, P28 (role undecided).

**Waiting list LENS (needs N5 and the resolver's lens path)**: L01-L07, L09, P26, P02/P16/P01 second pass; L08 and the F-MOVE
rows are the glass session's. Do not guess these: the faces carry joins, touch light and the window's liquid budget.

Risk: P27 (feeds the Chats home), P23 (one per message), P29 (one per row), P32 (every terminal key), P04 (every icon button's
tooltip), P05/L07 (every sheet), L01-L03 (chat screen), P11/P12/P14 (request screens, golden dir `feature/chat/.../goldens/requests`).

Parallel or one agent? Batch 1 and the ALLOW list are safe for two or three builders (disjoint files, no new API, each row
independently verifiable) but the glass session said it will run it as one agent: that is fine because batch 1 is small. The NEW and
LENS lists are one careful hand, the resolver's owner: they edit `KitSurface.kt` and `design/**` (fenced elsewhere) and every
role/parameter lands with its Solid pin first. Recommendation: ONE agent for the sweep, in this order: allowlist + ratchet, batch 1,
then N2 and the S rows (mechanical), then N1 rows, then LENS last. Do not parallelise past batch 1.

## 5. The guard

Where tests live today: `native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/KonsistRulesTest.kt` (Konsist scans
`production` = commonMain/androidMain/desktopMain of `core, protocol, engine, ui, feature, androidApp, desktopApp`). Its private
`ratchet(rule, files)` compares the set of breaking files with `quality/config/rule-baseline/<rule>.txt`; a new breaking file fails;
a fixed file must be deleted from the list (`-Doc.ruleBaseline=write` rewrites). `K1` (feature imports allow-list, `KitOnly.kt`) is
the kit-only guard for `/feature/`; it passes today's two glass touches (ChatScreen:885, ChatsScreen:278) because `ui.kit.*` imports
are allowed. Kit test dirs hold the goldens (`ui/kit/src/desktopTest`, `FlutterGoldenGate`), not source scans.

Design (new rule `K20`, same file and same runner):

1. Scope: `ui/kit`, `ui/kit-chat`, `ui/kit-content`, `commonMain` + `androidMain`; exclude `effects/`, `*Test*`.
2. Count per file (comments stripped) the lines matching any of:
   `\.background\([^)]*(colors|roles|c)\.(surface[123]|ground)` (including `.copy(`, `.compositeOver(`, `Color(roles.ground.v)`),
   `\.border\(`, `\.shadow\(`, `dropShadow\(`, `drawRect|drawRoundRect` whose first argument is such a colour, `kitLiquidFace\(`,
   `kitGlassSheet\(`, `kitBackdropVeil\(`, `kitFrostPane\(`, `kitSurfaceGlass\(`, `state\.spec\.resolve\(`.
3. Baseline `quality/config/rule-baseline/K20-kit-surfaces.txt`: `path<TAB>count` for today's tree AFTER removing allowlisted lines.
   Test fails if a file's count rises or a new file appears; fails with "fixed, lower the baseline" if it falls (the existing
   ratchet's two-sided shape, so the number can only go down and stays honest).
4. Allowlist in ONE file `quality/config/kit-surfaces-allow.txt`: `path | line-text-regex | reason`. A line matching an entry is not
   counted. A reason is required (blank fails); an entry whose line no longer exists fails (no stale allowances). Section 2d
   becomes this file verbatim.
5. Fenced files are in the scan like any other; the glass session lowers their numbers.
6. Planted test (like `KitOnlyPlantedTest`): a synthetic source with `.background(colors.surface2)` counts 1; the same line with an
   allowlist entry counts 0; a `kitSurfaceLook(... ownFill = colors.surface2)` call counts 0.
7. Feature side: extend `KitOnly.bannedInside` with the glass internals a feature must not import (`kitLiquidFace`, `kitGlassSheet`,
   `LocalKitGlassBackdrop`); keep `kitFrostSource`/`kitGlassSource` (recording markers) allowed.
8. Target: batch 1 takes the P rows to 0; N2 takes the S rows to 0; the end state is the allowlist plus zero.

## 6. What must not change, and what covers it

Solid, Soft, Brutal, Paper (and Signal): no pixel moves, no size or shape change, except as O1 states for parts that today paint
the Solid look in all styles. Sizes and shapes are unchanged by every row (the request names the same shape and radius).

| Batch / site | Covered by (run before and after) |
|---|---|
| Resolver itself | `ui/kit/.../rb/o01/KitSurfaceGateTest`, `KitSignalGateTest`; `ui/design/.../rb/o03/KitStyleParityTest`, `o02/KitEffectsParityTest` (Soft/Brutal/Paper/Signal specs); `effects/GlassPanesKitTest`, `GlassPaneRenders` |
| P01-P04 toast, undo, tip, tooltip | `Kita2ModalReviewTest`, `E20S08KitCraftTest`, tests naming KitUndoBar (3), KitTip (3), `Kita2ActionsReviewTest` |
| P05, L06, L07 sheets, menus, dialogs | `Kita1PaneSheetTest`, `KitSheetLeadingTest`, `KitModalSemanticsTest`, `modal/` tests, feature goldens that open sheets (`feature/settings`, `feature/agents/agents-bento`) |
| P06-P08 cards | `Kita1PaneStatusTest`, `rb/o22/KitOptionCardGateTest` (Soft/Brutal/Paper), `choice/` tests |
| P09-P15, P24, P25 transcript and requests | kit-chat `tool/`, `request/`, `form/`, `markdown/`, `card/` tests; `feature/chat/.../goldens` and `goldens/requests` (12) |
| P16-P21 composer | `ComposerSurfaceTest` (Style.Soft etc.), `feature/chat-composer/.../goldens` (18), kit-chat `composer/` tests, `E20S30JumpPillTest` |
| P23 prompt bubble | kit-chat `transcript/` (8 tests name it), `feature/chat` goldens |
| P27, P28, P29 rows, home | `KitRowPanelProminentTest`, `KitRecentRowTest`, `KitDividerProminentTest`, `feature/chats/.../goldens` (17 + a1-renders, `A1HomeRendersTest` Glass/Solid x light/dark) |
| P32/P33 terminal key bar | kit-content `terminal/` tests |
| S rows | `KitFieldLabelTapTest`, `KitComposerFieldInputTest`, `S7PolishLooksTest`, `ThemeSwitchControlTest` |
| LENS | `KitLiquidGlassTest`, `KitLiquidJoinKitTest`, `KitLiquidResolutionTest`, `JumpFadeEdgeTest`, `ChromeEdgeRenders`; and a phone look (desktop has no lens) |

Glass goldens may change (desktop renders the thick fallback); look at every one that does. Tests prove shape and compile, not the
look: judge Glass on a device.

## After batch 1 (2026-11-11, the sweep builder, branch `native/kit-surfaces-sweep`)

### What landed

- **The guard (§5).** `K20 kit parts paint no surface of their own, and the count only falls` in
  `native/quality/.../KonsistRulesTest.kt`, the counter in `KitSurfaces.kt`, planted tests in `KitSurfacesPlantedTest.kt`,
  allowlist `native/quality/config/kit-surfaces-allow.txt` (55 entries, a reason each), baseline
  `native/quality/config/rule-baseline/K20-kit-surfaces.txt`: **77 counted lines in 43 files**. Two-sided: a file over
  its number or a new file fails; a file under its number fails until the number is lowered; an allowlist entry that
  matches nothing fails. `-Poc.ruleBaseline=write` rewrites the baseline (review the diff: numbers only fall).
- **K1** (§5 item 7): a feature importing `kitLiquidFace`, `kitGlassSheet` or `LocalKitGlassBackdrop` fails; the
  recording markers `kitFrostSource` / `kitGlassSource` stay allowed. No feature imports them today.
- **No site was moved.** See O1 below.

### O1, decided by the coordinator: the other styles must not change in this sweep

A site moves only if, after the move, Solid, Soft, Brutal, Paper and Signal render it exactly as today and only Glass
changes. Checked against the specs (`ui/design/.../style/*Spec.kt` at 298328f854): **Soft, Brutal, Paper and Signal
never return null from `resolve`** (Brutal, Paper and Signal are typed non-null; Soft falls through to `shaped()`), so
once a part asks `kitSurfaceLook` its `ownFill` is used by Solid only and the four styles paint their own face. Every
batch-1 site today paints a plain fill (plus a hairline) in every style, so every move changes those four styles. With
each style's default knobs (Soft: Raised, no edges; Brutal: Raised, Hairline, Soft corners; Paper and Signal: Flat,
Hairline, Sharp corners), dark theme, at rest:

| Role asked | Soft | Brutal | Paper | Signal |
|---|---|---|---|---|
| `Floating` (P01-P04, P16, P30) | ground lifted by white .05, rim .16 to 0, paired shadows 6/16 dp | level fill, 2 dp ink edge, 6 dp hard shadow | ground, 1 dp ink box, square corners | ground, 1.5 dp text2 outline, corners at most 4 |
| `Sheet` (P05) | ground + white .03, top rim, 28 dp shadow | level fill, 2 dp ink edge, 4 dp hard shadow | level fill, 1 dp ink box | level fill, hairline edge |
| `Content` (P06, P08, P13-P15) | paired shadows and rim (Surface2: a prompt tint) | 2 dp ink edge, 4 dp hard shadow | Surface1: **no fill**; Surface2: ground + ink box | Surface1: **no fill**; Surface2: surface2 + hairline |
| `Code` (P09-P12) | a pressed-in well (black .18 over ground) | 2 dp ink edge instead of the hairline | 1 dp ink box (.6 alpha) instead of the hairline, square | 1 dp hairline drawn centred on the outline, corners at most 4 |
| `Control` (P17-P19) | paired shadows 3/8 dp | surface2 (not surface3), 2 dp ink edge, 3 dp hard shadow | ground + ink box | clear + 1.5 dp outline (an opaque action colour: a raised key) |

Result: **0 of the 21 batch-1 sites pass O1** (20 "changes other styles" by the table; P32 below). That is more than a
third, so the sweep stopped after the guard as the brief says, and nothing in `ui/kit*` main code changed.

**P32** (`KitTerminalKeyBar.terminalSurfaceLook`) is the closest: it already resolves `state.spec.resolve(request)` in
every style, so the fill, edge and corners would be identical with `kitSurfaceLook(... exactRadius = <the same
StyleResolver.corner value>)`. Two differences remain: `kitSurfaceLook` applies hook H2 (Brutal's pressed cap sinks by
its 3 dp offset, `faceShiftDp`; the copy never sinks) and the 200 ms style cross-fade. The pressed Brutal key would
render differently: changes other styles. Its Glass side is real (the copy skips clear panes and thickening).

### What would unblock batch 1 (for the resolver's owner, not done here)

**N7, "hold the other styles":** a request field (for example `KitSurfaceInput.holdOtherStyles: Boolean`, or one rule
in the four specs) under which Soft, Brutal, Paper and Signal return null, so the part keeps `ownFill`, and Glass
resolves the role as usual. With N2 (`ownEdge`, drawn in every style but Glass) every batch-1 site becomes a mechanical
`kitSurfaceLook(KitSurfaceInput(role, shape, level, ownFill = <today's fill>, ownEdge = <today's hairline>, hold...))`
with zero change outside Glass, provable by a pin test on the null decorations. N1 (`Inset`) already proposes exactly
this null for its own role; N7 generalises it. The other way is to reopen O1 (accept the four styles' faces), which the
coordinator ruled out for this sweep.

### Left for later (list C)

**Batch 1, "changes other styles"** (role as the spec proposed; all need N7 + N2, or O1 reopened):
P01 `KitToast`, P02 `KitUndoBar`, P03 `KitTip`, P04 `KitIconButton` tooltip, P05 `KitSideSheet`, P06 `KitSummaryCard`
card, P08 `KitAgentChoice`, P09 `KitCommandBox`, P10 `KitToolOutput`, P11 `KitRequestTargets` (2), P12
`KitPermissionCard`, P13 `KitFormSheet`, P14 `KitRequestFrame`, P15 `KitAgentCard` list slot, P16 `KitVoicePill`, P17
`KitStopAndSendPill`, P18 `KitAgentStrip`, P19 `KitComposerBox` stop disc, P30 `KitRefresh`, P32 `KitTerminalKeyBar`.
P11/P12 could ask role `Ground` and change in no style at all (Ground is `styleGround` everywhere, Glass included), but
that names a well the page plate only to lower a count: not done.

**Need a new role or parameter (N1, N2, N4, N6):** P07, P21, P23, P24, P25, P29 (N1); S01-S11 and P33 (N2); P27 (N4,
owner call); P31 (N6). Found by the guard, not in §2: `modal/KitSheet.kt:707` (a framed entry's surface1 panel inside a
sheet: N1), `C transcript/KitPromptBubble.kt:194` (attachment pill, surface3) and `:251` (photo tile, surface3): N1;
`C card/KitAgentCard.kt:282`, `K foundation/KitPanel.kt:124`, `K team/TeamParts.kt:325` (an own hairline in every
style right after a resolver call: N2); `K choice/KitWheel.kt:101` (Solid-only accent band, belongs with S07: N2).

**LENS (N5 and the lens path):** L01-L07, L09, P26, and the second pass of P01, P02, P16. Counted by K20 today:
`KitComposerBox:187`, `KitFab:136`, `KitChip:131`, `KitJumpPill:291-292,320,344`, `KitScreen:321,484,485,518`,
`KitMenu:616-617`, `KitSheet:574,591`.

**Unsure, what I found:**
- P20 `KitComposerTray`: the Glass branch (`:128`) tests the style and shows a bare `KitIconButton`; the other styles
  paint a surface3 disc (`:154`). Moving the disc to `Control, Circle` changes the four styles (table). Whether Glass
  keeps the bare glyph is the owner's 2026-11-08 call; untouched.
- P22 `KitComposerLimitStrip:133`: a surface1 band set inside the composer's bottom block, its corner derived from the
  composer's. A well, not a track: N1 `Inset`.
- P28 `KitOpenSwitchRow:47`: surface3 band when highlighted, every style. `KitRow:239` and `KitChoiceList:245` do the
  chosen row as `kitGlassChosenRow() ?: <own band>` (`effects/KitGlassPanes.kt:108`, null outside Glass), which would
  keep the four styles and change Glass only. It is not a `kitSurfaceLook` request and its fallback stays a counted
  paint, so it was not done; it is the right shape for an O1-safe chosen row until the resolver has a chosen-row role.
- P31 `KitAnimatedBox` (`motion/KitMotionBuilders.kt:115`): fill and edge chosen by its callers; needs N6.

**Inside the fence:** L08 (`kitGlassSheet`), the F-MOVE rows (`KitRowGroup` `HairlineColumn`, `KitRecentRow:327`) and
everything in `KitSurface.kt`, `KitSurfaceGlass.kt`, `KitDivider.kt`, `KitGlassPanes.kt`, `live/**`. K20 counts no line
in a fenced file: `KitSurface.kt:199,330` are allowlisted as the resolver itself, and the row lines are drawn with
`drawLine`, which K20 does not see.

### Where §2-§5 were wrong or incomplete

1. §4 and §6 assumed a move changes the other styles only "where there is no golden"; under O1 that is a fail, and
   it applies to every MOVE-role site (above). The spec's request rows are right for Glass; they cannot be built
   O1-safe with today's resolver.
2. P32 "same inputs": `kitSurfaceLook` adds hook H2 (pressed sink) and the style fade the copy lacks.
3. P11's precedent `KitInboxRequestRow.kt:92` is in `C request/`; it asks `Code` and so already shows the four
   styles' faces (Soft's well, Brutal's ink edge), i.e. the precedent is not O1-safe either.
4. A04 names `qr/KitScanner.kt:251`; the painting line K20 finds is `K scanner/KitScanner.kt:89` (`ground` behind the
   camera). Both files exist.
5. A12 names `KitAgentCard.kt:288` (the attention ring, a fill of `attention`, not counted); the card's own hairline
   at `:282` is a separate N2 site, missing from §2.
6. §2 misses `KitSheet:707`, `KitPromptBubble:194,251`, `KitPanel:124`, `TeamParts:325`, `KitWheel:101` (counted, list
   above) and `KitComposerBox:523` (the Stop glyph's square, a mark), `setup/KitServersLink.kt:85,92` (illustration),
   `foundation/KitFrost.kt:141` (frost engine), the accent halos `KitAskField:174,181`, `KitStartBox:98,104`,
   `C composer/ComposerGlow.kt:171` (allowlisted; the halos are my addition: the style's glow, not a pane shadow; §2b
   had KitStartBox's halo under S03; veto it by deleting the entry and raising nothing).
7. §5's fill pattern `[^)]*` misses `background(if (x) colors.surface3 else ...)` (P19, P16's fill, KitCarousel's
   dots); K20 allows two levels of nested parentheses instead.

### What K20 does not see (the count is a floor)

A fill passed through a local value (`val fill = colors.surface2` then `.background(fill)`, e.g. P13's wash at
`KitFormSheet:129`, KitMotionBuilders), the attention colours (`attentionSurface`, `attentionFill`: P15's fill at
`KitAgentCard:857`, P17 entirely), the kit's own edge helpers (`kitHairlineBorder`, `exactEdge`), lines drawn with
`drawLine`, and fills nested three parentheses deep (`KitWidgetPreview:165`, a preview).
