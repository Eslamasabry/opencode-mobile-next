# KitSheet v2 — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitSheet-v2` (wave 1, tier 1a, kind `kit-change`). Spec: kit-v2.md §1.1, §4.6, §4.7, §8.2, §8.3; visual language §5 "Sheets", §6 (no glass on sheet bodies), §7; cut review C17 (keep: "grabber, icon tile, left-aligned title, consequences in a surface1 panel, buttons stacked on phones and right-aligned in a row on PC; KitDraft unchanged"). Rules: KIT-11, KIT-15, KIT-16, KIT-17, KIT-18, KIT-19, KIT-39, KIT-43, LOOK-19, LOOK-21, LOOK-22, LOOK-25, LAY-3, LAY-4, LAY-10, LAY-13, DATA-1, DATA-2, DATA-3, DATA-4, MOT-2, MOT-11, A11Y-8. STANDARDS wins where it differs from K2 or VL (§0.2).

## Purpose

The one frame for every modal sheet (a choice, a short task, a request's details, a list of acts on one thing, a picker): a grabber, a header (optional icon tile, a start-aligned title, an optional muted subtitle, Close), a body that scrolls inside the frame, and a pinned `KitActionBlock`. v2 gives the existing `showKitSheet`/`KitDraft` API the visual-language look, with no call shape changed.

## Replaces

- **Map elements (kit-v2.json `assignment` → `KitSheet`): 52 elements on 41 pages.** The pages are:
  - appearance-picker-sheet, builtin-server-log-sheet, chat-message-actions-sheet, chat-read-aloud-voice-sheet, coding-settings-shell-sheet, command-launcher-sheet, connection-status-details-sheet;
  - development-services-editor-sheet, development-services-logs-sheet, files-row-actions-sheet, form-sheet, isolated-task-sheet, language-sheet, legacy-drafts-review-sheet;
  - model-picker-sheet, model-picker-sheet-options-dialog, model-picker-sheet-unloaded-providers-dialog, permission-sheet, plugins-mapping-dialog, plugins-settings, project-folder-browser;
  - prompt-history-sheet, prompt-stash-sheet, prompt-tools-sheet, question-sheet, review-comment-sheet, run-result-output-sheet, running-work-sheet, server-switcher-sheet;
  - session-approvals-sheet, session-menu-sheet, settings-transcript-display-sheet, shortcuts-help-dialog, team-cycle-how-sheet, team-plugin-sheet, theme-pack-preview-sheet;
  - timeline-sheet, voice-composer-sheet, voice-model-setup-sheet, workspace-archived-sheet, workspace-context-sheet.
- **Code it is the destination for** (G1 baseline, `test/kit_ratchet_baseline.json`, outside `lib/ui/kit/`):
  - `showModalBottomSheet(`: 78 uses in 51 files;
  - `DraggableScrollableSheet(`: 5 uses in 5 files.

  Wave-2 screen units move these to `showKitSheet`, or to the specialised entry points built on it (`showKitConfirm`, `showKitChoiceSheet`, `showKitTechnicalDetails`, `showKitRequestSheet`). Today `showKitSheet` has no call site outside the kit.
- **Merged gap names:** `KitSheet`, `KitDiscardGuard`.
- **Retired inside the kit by this change** (`kit_sheet.dart`, look only):
  - the raw `IconButton` close, which becomes `KitIconButton`;
  - the raw `Text` header, which becomes `KitText`;
  - the header's truncation (`maxLines: 2` with an ellipsis), which A11Y-8 forbids.

## File

- **Part:** `lib/ui/kit/kit_sheet.dart` (the library; its part file `kit_confirm_sheet.dart` belongs to kit-KitDetailsFold, see KitConfirmSheet.md).
- **Tests:** `test/kit/kit_sheet_test.dart`, and `test/kit/kit_draft_test.dart` for KitDraft tests only (PROC-10).
- **Gallery:** `test/goldens/kit/kit_sheet_golden_test.dart`.
- **Private seams the confirm part compiles against, which this unit keeps with their current names and signatures:** `_l10n`, `_KitHandle`, `_KitModalShape`, `_presentKitModal`, `_KitSheetScope.maybeOf`, `_KitSheetHostState.ask`, `_KitAsk`, `_KitConfirmSpec`, `_KitConfirmBody`. Changing one breaks kit-KitDetailsFold's part file.
- **New private seam:** `_KitIconTile({required IconData icon, required _KitTileTone tone})`, where `_KitTileTone` is `neutral`, `attention` or `danger`. It is the one 44 dp header tile. `KitSheet` uses `neutral` and `attention`, and the confirm part uses `neutral` and `danger` (LOOK-5: danger only inside a confirmation).

## Public API

```dart
/// How tall a showKitSheet opens. Unchanged.
enum KitSheetHeight { content, half, full }

/// NEW. The tone of the header's icon tile. `attention` is used only by
/// showKitRequestSheet (LOOK-4, LOOK-24); nothing outside lib/ui/kit/
/// references it (G17).
enum KitSheetTone { neutral, attention }

/// Unchanged: the code's KitDraft stands (KIT-39), with `controller` and
/// `prefs` beyond K2 §1.1's two fields. Key `oc.draft.<target>.<profileId>`.
@immutable
class KitDraft {
  const KitDraft({
    required String target,
    required String profileId,
    required TextEditingController controller,
    SharedPreferences? prefs,
  });
  static String keyFor(String target, String profileId);
  String get key;
  Future<void> restore();
  Future<void> save();
  Future<void> clear();
}

/// Unchanged call shape (KIT-43); `icon` and `tone` are new and optional.
Future<T?> showKitSheet<T>(
  BuildContext context, {
  required String title,                     // the place in the person's words (COPY-10)
  required WidgetBuilder body,               // scrolls inside the frame; never its own Scaffold
  String? subtitle,                          // what it is about; wraps
  IconData? icon,                            // NEW: the header's icon tile; null draws no tile
  KitSheetTone tone = KitSheetTone.neutral,  // NEW
  KitSheetHeight height = KitSheetHeight.content,
  KitAction? primary,
  KitAction? secondary,
  List<KitAction> tertiary = const [],
  WidgetBuilder? footer, // slice-P3.3: settings pinned above the actions
  // KitSheet (the frame) only, picker layout: leading (back or Close), menu
  // (the more button), menuLabel, headerLine (the quiet line under the
  // title), bar (text button + primary as one pinned row), step (in-place
  // swap). `entry` was removed; see "Picker layout" below.
  ValueListenable<bool>? dirty,              // unsaved input that cannot be a draft
  KitDraft? draft,                           // preferred over dirty (DATA-2)
  ValueListenable<bool>? loading,            // code stands (KIT-39): the one loading bar
  RequestRoutes? routes,                     // closes itself when its request is answered elsewhere
  bool dismissible = true,                   // false only while an irreversible step runs (KIT-18)
  Key? sheetKey,
});

/// The frame itself, for goldens and a tablet full-screen variant.
/// Unchanged constructor; `icon` and `tone` are new and optional.
class KitSheet extends StatelessWidget {
  const KitSheet({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.icon,                       // NEW
    this.tone = KitSheetTone.neutral, // NEW
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.onClose,
    this.loading = false,
    this.handle = true,
    this.fill = false,
    this.onPullDown,
  });

  /// Unchanged.
  static void close<T>(BuildContext context, [T? result]);
}

/// NEW. What a fact in a consequences panel says about the thing.
enum KitConsequenceMark {
  info,  // a fact: text2 info glyph
  lost,  // goes with the act (loses data or ends work): danger glyph (LOOK-5)
  kept,  // survives the act (DATA-8): text2 check
}

/// NEW. One counted fact: "3 queued prompts will be deleted".
@immutable
class KitConsequence {
  const KitConsequence(
    this.text, {
    this.mark = KitConsequenceMark.info,
    this.icon,                       // overrides the mark's glyph ("the test run" → terminal)
    this.key,
  });
  final String text;
  final KitConsequenceMark mark;
  final IconData? icon;
  final Key? key;
}

/// NEW. The visual language's consequences panel (§5 Sheets): a surface1
/// panel of one-line facts with hairlines inset to the words. A sheet body
/// places it where the facts belong, and showKitConfirm draws its
/// `consequences` with it.
class KitConsequences extends StatelessWidget {
  const KitConsequences({super.key, required this.items})
    : assert(items.length > 0);
  final List<KitConsequence> items;
}
```

- **`showKitFramedSheet` (added by slice-P9.10).** `Future<T?> showKitFramedSheet<T>(BuildContext context, {required WidgetBuilder builder, double? maxWidth, bool useSafeArea = false, Key? sheetKey})` opens a body that draws its own `KitSheet` frame (with `handle: false`) on the theme's bottom sheet route: scroll-controlled, the route's one drag handle, capped at `maxWidth` when given. It exists for the few sheets whose frame depends on state only the body holds (the folder browser, the command launcher, the timeline), so the last raw `showModalBottomSheet(` calls outside the kit came through the kit without changing their look. Everything else uses `showKitSheet`.
- **Additive only (KIT-43).** Every existing `showKitSheet`, `KitSheet` and `KitDraft` call compiles and behaves as before. Nothing is renamed, removed or marked `@Deprecated`.
- **Returns.** `showKitSheet` completes with the value passed to `Navigator.pop`/`KitSheet.close`. It returns null on dismissal, and also when `routes` closed it or `routes` was no longer pending at call time.
- **Internal keys kept (TEST-5):** `kit-sheet-close`, `kit-sheet-handle`, `kit-sheet-actions`, `kit-sheet-content` and `kit-loading-bar`. They are used by 8 test references. New internal keys: `kit-sheet-icon` and `kit-consequences`.
- **Kit copy:** no new ARB keys. The existing keys are `kitSheetClose`, `kitSheetDismiss`, `kitSheetLoading`, `kitDiscardTitle`, `kitDiscardBody` and `kitDiscardConfirm`.

## States

The doc comment declares: `default`, `with-icon`, `loading`, `disabled` (the primary disabled with its reason), `working` (an action's own tap in flight), `discard` (the question in place), `half`, `full`, `not-dismissible`, `consequences`, `keyboard-open`, `attention` (the request sheet's header).

- **Loading.** The 2 dp `KitLoadingBar` sits directly under the header while `loading` is true. The body shows the caller's skeleton rows (STATE-4).
- **Empty and error.** These are the body's, drawn as an inline `KitStateView` or a `KitNotice` with Try again (STATE-20: a sheet that fetches). The frame adds nothing.
- **Footer (slice-P3.3).** `footer` is one short line of settings that go with the primary (the model sheet's `Thinking: High` and `Agent: Build` chips). It is pinned with the actions, above them, and stays in reach while the body scrolls. A longer choice opens from it as a `KitMenu`, never a dialog over the sheet.
- **Picker layout (2026-10-03).** The folder pickers follow Canva "Move to a folder", the iOS Files picker and the Apple Notes folder picker. Five options, none a new widget: `leading` (a `KitAction`; a back chevron that goes up a folder or back a step, or Close (X) at the first step; it replaces the header's close button), `menu` (a list of `KitMenuItem`s behind one ⋯ button at the end, replacing the close button; `menuLabel` names it, "More actions" by default), `headerLine` (one quiet widget under the title, such as the place menu "This phone ⌄"; it replaces `subtitle`), `bar: true` (pins `secondary` as a text button at the start and `primary` at the end, ellipsized, as one row; `tertiary` is not shown) and `step` (a key naming the step: when it changes, the whole frame cross-fades in place, so the next step replaces the first inside the one sheet; reduced motion swaps at once). A second step is never a second sheet or a dialog. First user: the folder browser ("Open a project", then "New project"). The older `entry` option (a name field pinned in the footer under the list) is removed: the name step has the sheet to itself, with an autofocused `KitField` in the body and "Create and open" pinned above the keyboard.
- **Disabled.** A disabled primary shows its `disabledReason` line under it (KitActionBlock, kit-KitAction-v2; STATE-8).
- **Working.** Shown by the tapped action (`KitAction.working`) and never by the frame (STATE-7).
- **Discard.** With `dirty` and no `draft`: swipe, back, Esc, Close and a tap outside replace the content with the `KitConfirmKind.discard` question in place, with its own back step. The typed text stays underneath (KIT-16, DATA-3).
- **Not dismissible.** There is no Close, no drag, and Esc and back are ignored. The body must say why (KIT-18).
- **Closed by the request.** With `routes`, the sheet is removed a frame after the request stops pending. The opener's part (the request card) keeps the receipt ("Answered on the laptop"), so the frame never disappears without a word. The frame draws no receipt of its own.

## Tokens

All exist on `feat/visual-language-v1` unless flagged.

- **ThemeRoles:**
  - `surface2`, the sheet, panel and end sheet (through `KitTokens.sheetSurface`, `panelSurface` and `sideSheetSurface`);
  - `scrim`, the barrier, with no blur. VL §3's "blurred at σ 3" is void under LOOK-22;
  - `surface3`, the neutral icon tile;
  - `text1`, the title and the neutral tile glyph;
  - `text2`, the subtitle and the info and kept glyphs;
  - `text3`, the grabber (`handleColor`);
  - `attention`, the attention tile glyph and its tint (request sheet only);
  - `danger`, the lost glyph;
  - `hairline`, the consequence separators;
  - `surface1`/`ground` for the consequences panel through `KitTokens.insetSurface`. In light, surface1 equals surface2, so the panel uses `ground` (B5).
- **KitText:**
  - `title`, the header title (`sheetTitle`);
  - `secondary`, the subtitle (`sheetSubtitle`);
  - `rowTitle`, a consequence's text;
  - `button`, through KitActionBlock.
- **KitTokens:**
  - `rail` (20), `space1`–`space5`;
  - `sheetRadius` (30), `panelRadius` (24), `panelInset` (24);
  - `handleSize` (36×5), `handleHeight` (22);
  - `markSize` (44), `markRadius` (12), `markIconSize` (22), `markTintAlpha` (the attention tile only), `iconSize(context, …)` with `maxIconScale`;
  - `panelCornerRadius` (18, the consequences panel, LOOK-19 "panel 18"; today's private `_KitConsequences` uses `detailsRadius` 14);
  - `smallIconSize` (20, consequence glyphs; replaces the literal `20` and `top: 1`);
  - `minTarget` (48).
- **KitLayout (existing):** `sheetMaxWidth` (640), `dialogPanelWidth` (560), `sideSheetMinWidth`/`sideSheetMaxWidth` (400/480), `sideSheetShare`, `modalMaxHeight` (0.9), `sheetHalfHeight`, `sheetFullHeight`, `shortHeight` (480, through `modalWindowOf`).
- **Pre-wave seams (§0.5 step 2, flagged):** `KitTokens.hairlineWidth(context)` for the separators (LOOK-21), and `focusRingWidth(context)` for the focus ring through KitIconButton and KitButton.
- **New tokens:** none.

## Adaptive

| Class | Shape | Actions |
|---|---|---|
| compact (< 600) | bottom sheet, full width up to 640, grabber, top radius 30 | stacked full width: primary, secondary, then up to two tertiary at the start |
| medium (600–839) | bottom sheet capped at 640 dp and centred | one end-aligned row, primary at the end (LAY-13, from kit-KitAction-v2) |
| expanded / large (≥ 840) | centred panel up to 560 dp, radius 24, no grabber; `KitSheetHeight.full` becomes an end-side sheet of 400–480 dp | one end-aligned row |
| short (< 480 dp tall, any width) | keeps the compact bottom sheet (LAY-3) | stacked |

- **The icon tile** sits above the title, at the start, on every class, as in the approved Confirm render. The header is: grabber, tile, title, subtitle. Close is at the end of the title line.
- **The keyboard (§8.3, G14):**
  - Esc closes the sheet and obeys `draft` and `dirty` exactly as a swipe does (DATA-3);
  - Esc inside a text field still closes (existing test);
  - Tab follows reading order: Close, the body, then the pinned actions;
  - the frame binds no Enter;
  - a focus ring is always visible.
- **A fine pointer:**
  - hover highlights come from KitButton and KitIconButton;
  - a tap on the barrier dismisses and obeys `dirty`;
  - targets stay 48 dp.

## Accessibility

- **Focus.** The route is named by the title (`namesRoute`, `scopesRoute`, `header`), and TalkBack's first focus is the title (KIT-19). Keyboard focus sits on the sheet host so Esc works, and returns to the opener on close.
- **Close** is a `KitIconButton` labelled "Close" (`kitSheetClose`), 48 dp, at the end.
- **The grabber** has the semantic "Dismiss" action (`kitSheetDismiss`), or none when not dismissible.
- **The icon tile and consequence glyphs** are excluded from semantics: the title and the fact's words carry the meaning (STATE-9).
- **200 % text:**
  - the title and subtitle wrap and are never truncated (A11Y-8 supersedes K2's "two lines");
  - the body scrolls;
  - the pinned actions stay pinned and visible, also with `viewInsets.bottom` 300 (KIT-17);
  - a short window lets the header scroll with the body.
- **Announcements.** The loading bar's label is read once. The discard question is announced as its title when it swaps in.

## RTL

- Close is at the end, and the icon tile and title at the start. All padding is directional (LAY-8).
- The end-side sheet sits at the end edge (left in Arabic) and slides in from it.
- The frame does not wrap text. Callers wrap placeholders with `KitBidi` (COPY-30), and technical values go into the body's `KitDetailsFold` or `KitCodeBlock`.

## Motion and haptics

- **Routes.**
  - The bottom and end sheets slide on `KitMotion.standard` with `enter`/`exit`.
  - The panel cross-fades on `standard`, with no scale (MOT-2).
  - Under `KitMotion.reduced` they appear and leave at once (`AnimationStyle.noAnimation`, a zero transition).
- **The discard question and an in-place confirmation** swap with the content through `KitReveal` (KIT-16, K2 §1.2), replacing today's `KitEntrance`. Height changes inside the body use `KitReveal` too, never an animated sheet height.
- **Haptics:** none. Sheets never vibrate (MOT-11). A confirmed stop, delete or discard plays `KitHaptics.commit` from the confirmation, not the frame.
- **One `pump()`** settles every state under reduced motion (G8).

## Data safety and honest state

- **With a draft,** swipe, back, Esc, Close and a tap outside keep the typed text silently. Reopening restores it into an empty controller, and the caller calls `clear()` once the text is used (DATA-1, DATA-2). The key is `oc.draft.<target>.<profileId>`, which the profile sweep removes (DATA-4, G10).
- **With only `dirty`,** the frame owns the drag (on the grabber and header) and asks the discard question in place. "Keep editing" is the default. Clean input closes without asking (DATA-3).
- **Not dismissible** only while an irreversible step runs, and the body says so (KIT-18).
- **`routes`:** if the request is already answered at call time, nothing opens and the call returns null. While open, the sheet closes by itself. The receipt is the opener's (see KitRequestSheet.md).
- **Glass:** no glass, blur or shadow on the sheet or its body (LOOK-20, LOOK-22, LOOK-27).

## Depends on

- **Existing parts:**
  - `KitActionBlock`/`KitAction`/`KitButton` (kit_buttons.dart);
  - `KitIconButton` (the existing `label` API, which kit-KitIconButton-v2 forwards to `tooltip`);
  - `KitLoadingBar`, `KitReveal`, `KitMotion`, `KitLayout`;
  - `RequestRoutes` (`lib/ui/widgets/request_routes.dart`, unchanged);
  - VL `KitText`/`KitTokens`/`ThemeRoles`.
- **Pre-wave seams:** `KitTokens.hairlineWidth`/`focusRingWidth`.
- **No edge to kit-KitAction-v2 (README.md, decision D4).** The "row on PC" acceptance needs kit-KitAction-v2's window-class rule (today's KitActionBlock goes to a row only at `maxWidth >= 600`, so inside the 560 dp panel it stacks). An edge would move this unit to tier 1b, and kit-KitDetailsFold, which needs this unit's `KitConsequences` and `_KitIconTile`, to 1c with everything after it. Instead both stay in tier 1a: this unit's test 6 is listed under NOT proven until Action-v2 merges, and the integrator regenerates the 800/1280/1600 `kit_sheet_*` goldens once, after the tier-1a integration (R07).
- **Depended on by:**
  - kit-KitDetailsFold (the confirm part: `KitConsequences` and the frame; edge added, README.md);
  - kit-KitRequestSheet (`tone: attention`);
  - kit-KitChoiceList (`showKitChoiceSheet`);
  - screen-voice-1 (C30).

## Tests required

In `test/kit/kit_sheet_test.dart` (the existing tests keep passing unchanged):

1. **The frame:**
   - with `icon`, the `kit-sheet-icon` tile sits above the title at the start, and is excluded from semantics;
   - without `icon`, no tile is drawn;
   - `tone: attention` paints the tile glyph in `attention`, and `neutral` in `text1`.
2. **Title wrap:** at `textScaler` 2.0, a 60-character title and a two-sentence subtitle are fully laid out (`RenderParagraph.didExceedMaxLines` is false) and nothing overflows.
3. **Pinned actions (KIT-17, G9x):** at `textScaler` 2.0 with `viewInsets.bottom` 300, the primary is fully inside the visible rect while the body scrolls.
4. **Close:**
   - it is a `KitIconButton` whose semantics label is "Close", at least 48×48;
   - the grabber exposes the "Dismiss" action;
   - with `dismissible: false`, neither exists and Esc does nothing.
5. **The window classes:**
   - bottom sheet at 360, 412 and 915×412 (short);
   - capped at 640 at 800×1280;
   - a 560 panel at 1280×800 and 1600×1000;
   - `full` is an end sheet of 400–480 at 1280×800, at the start edge in Arabic.
6. **Actions:** stacked at 412×915, and one end-aligned row with the primary last at 1280×800 (needs kit-KitAction-v2; before it merges, the test is listed under NOT proven).
7. **`KitConsequences`:**
   - `lost` draws the danger glyph, `kept` the check in `text2`, and `info` the info glyph;
   - the separator is one physical pixel (`hairlineWidth` at DPR 3 = 1/3) and inset to the text start.
8. **`routes`:**
   - flipping `isPending` to false while open removes the route after one frame, and the future completes with null;
   - a non-pending `routes` at call time pushes no route.
9. **Discard in place:** no route is added, "Keep editing" keeps the text, and "Discard changes" closes (the existing tests stay green).
10. **Reduced motion (G8):** opening and closing settle after one `pump()`.
11. **KIT-43:** a call with only the pre-v2 parameters compiles and renders exactly as before, apart from the look.

`test/kit/kit_draft_test.dart` and `test/kit/kit_keyboard_test.dart` (the Esc and Tab rules) keep passing unchanged.

## Galleries required

`test/goldens/kit/kit_sheet_golden_test.dart`, DPR 3.0, Android, VL fonts (TEST-8), with names per TEST-20 (the size is left out for 412×915; this unit renames its existing `…_412x915_…` PNGs in the same commit).

- **Every declared state × dark and light at 412×915:**
  - default (no icon);
  - with-icon;
  - consequences (lost, kept and info);
  - loading;
  - disabled (with its reason line);
  - discard (in place);
  - half;
  - not-dismissible;
  - keyboard-open (`viewInsets.bottom` 300);
  - attention.
- **with-icon × dark and light** at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **full** at 1280×800 (the end sheet), dark and light.
- **Text 2.0 and Arabic** (with-icon), each at 412×915 and 1280×800, dark and light.

That is 40 PNGs, under the TEST-20 cap of 60.

## Non-goals

- No call site outside the kit changes. The 78 `showModalBottomSheet(` uses move in wave 2.
- No public name is renamed or removed, and KitDraft is unchanged (C17).
- The confirmation's look and its private fold are kit-KitDetailsFold's (KitConfirmSheet.md). This unit only provides `KitConsequences` and keeps the private seams stable.
- The request sheet's content and outcome are KitRequestSheet.md's.
- No glass, blur, shadow or scale anywhere in the frame.
- No sheet on a sheet, and no second modal type.

## Open questions

None. The scheduling choice above is README.md decision D4.
