# KitComposerChips — frozen API (wave 0, 2026-09-26)

Group: chat. Unit `kit-KitComposerChips` (wave 1, tier 1c; after kit-KitChip, kit-KitMenu and kit-KitImage, C16, C25, plus kit-KitTappable and kit-KitMotionParts, README.md). In wave 2c `lib/ui/kit/chat/kit_composer_chips.dart` joins chain link chat-3 (C03, C38). Spec: cut review C16 (split from KitComposer: "the model chip, context badge, attachment preview and inline command row"), kit-v2.md §9.2, §5 (chat composer group); visual language §5 (the composer holds the model chip); programmes P7.7 ("Sign in to a model" chip), P7.5 (never "Choose model" while a model answers), P6.6 (server default by name). Rules: STATE-8, STATE-9, COPY-2, COPY-11, KIT-32, A11Y-8, AUTO-4, AUTO-8, LOOK-4.

## Purpose

The small things that ride with a message before it is sent: the model chip (which model answers, how full its context is, and a quick way to switch), the attachments and references the message will carry, and the inline suggestions that appear when the person types `/` or `@`. The same attachment chips, read-only, show what a sent prompt carried.

**Where the model chip sits** (owner decision 2 Oct 2026, 14A): above the message field, at the end of the status line (`KitComposerStatusStrip(chips:, model:)`, `kit_composer_status_strip.dart`), next to the standing-fact chips such as "Auto-approving"; it is not inside the composer pill, so the field has its full width. The strip gives it at most 60 % of the line (it ellipsizes first); the other chips scroll sideways. It draws nothing when it has neither chips nor a model.

## Replaces

- **Map** (kit-v2.json, module:chat composer): `embedded-composer#embedded-composer-model-chip`, `#embedded-composer-model-cycle-menu`, `#embedded-composer-context-badge`, `#embedded-composer-attachment-chip-preview`, `#embedded-composer-inline-command-row`, `chat#chat-pending-photo-row` (6 elements). Map `statesMissing` fixed here: "no model signed in: chip says 'Choose model' identically to 'server default in use'"; the map note "Inline ListTile suggestions, descriptions truncated mid-word"; `chat-pending-photo-row` couldBeAutomatic "attach the recovered photo to this draft automatically".
- **Code:** in `lib/ui/screens/chat/composer.dart` (chat-3): `_ModelContextChip` (:1054), `_ContextPercentBadge` (:1288), `_ContextMeterLine` (:1705), `_AttachmentGlyph` (:1449), `_PendingAttachmentChip` (:1515), `_StagedReferenceChip` (:1620); in `lib/ui/widgets/model_shortcuts.dart` (chat-3; G16 Icon 1, PopupMenuButton 1, PopupMenuItem 3, Text 3) the model cycle menu; in `lib/ui/screens/chat/command_launcher.dart` (chat-4) `_InlineCommandSuggestions` (:621) and `_InlineAgentSuggestions` (:709); the recovered-photo row in `chat_screen.dart` (≈:7785, chat-9's region). These carry most of `composer.dart`'s ListTile 13, Material 2, Image 1, Tooltip 5 and part of its Text 40 and Icon 29 (G16 total 120, brought to zero by chat-3 with KitComposer).

## File

`lib/ui/kit/chat/kit_composer_chips.dart` (new).

## Public API

```dart
/// Which model answers, as far as the composer can say.
enum KitModelChipState {
  chosen,        // a model the person picked: its presented name
  serverDefault, // no pick: the server's default, by name when known (P6.6)
  signInNeeded,  // no model is signed in: "Sign in to a model" opens the provider row (P7.7)
  chooseNeeded,  // models exist, none picked and no default: "Choose a model"
}

enum KitAttachmentKind { image, file, folder, reference }

/// One thing a message carries. A value, so the kit never sees a
/// PromptAttachment or a ReviewReference (ARCH-1).
@immutable
class KitAttachment {
  const KitAttachment({
    required this.id,              // stable identity for keys and removal
    required this.label,           // a file name, "@src/app", "main.dart:12–30" (host isolates technical text)
    required this.kind,
    this.detail,                   // "Recovered", "Not saved with your draft", "2.1 MB"
    this.thumbnail,                // images: KitImageSource for a small preview
    this.onOpen,                   // preview; null: not openable (a folder reference)
    this.chipKey,                  // e.g. Key('composer-reference-<id>')
    this.thumbnailKey,             // e.g. Key('attachment-thumbnail')
  });
  final Object id;
  final String label;
  final KitAttachmentKind kind;
  final String? detail;
  final KitImageSource? thumbnail;
  final VoidCallback? onOpen;
  final Key? chipKey;
  final Key? thumbnailKey;
}

enum KitSuggestionKind { command, agent }

@immutable
class KitSuggestion {
  const KitSuggestion({
    required this.id,
    required this.label,           // "/compact" (mono, LTR) or an agent's name
    required this.kind,
    this.description,              // up to two lines, wrapped at words
    this.key,                      // e.g. Key('inline-command-compact')
  });
  final Object id;
  final String label;
  final KitSuggestionKind kind;
  final String? description;
  final Key? key;
}

/// The composer's chips (C16). Named forms of one part (NAME-1):
/// [KitComposerChips.model], [KitComposerChips.attachments] and
/// [KitComposerChips.suggestions].
///
/// States: model chosen / server default / sign in needed / choose needed /
/// context warning / narrow (glyph only); attachments editable / read-only
/// / empty; suggestions list / empty (KIT-12).
class KitComposerChips extends StatelessWidget {
  /// The model chip. Tap opens the host's model picker (or, for
  /// signInNeeded, the provider row). Long-press, right-click and the
  /// semantic custom actions open [menu] (next, previous, favourites).
  const KitComposerChips.model({
    super.key,
    required String this.label,            // presented name(s), host-joined: "explore · Sonnet 4.5 · High"
    required VoidCallback this.onPressed,
    this.state = KitModelChipState.chosen,
    this.contextUsed,                      // 0–1; shown from 0.7 (see below)
    this.menu = const <KitMenuItem>[],
    this.chipKey,                          // today's Key('composer-model-context')
    this.contextKey,                       // today's Key('composer-context-percent')
  });

  /// The attachments and references a message carries. With [onRemove]
  /// null the chips are read-only (a sent prompt, KitMessage).
  const KitComposerChips.attachments({
    super.key,
    required List<KitAttachment> this.items,
    this.onRemove,                         // ValueChanged<KitAttachment>?
    this.stripKey,                         // today's Key('composer-reference-strip')
  });

  /// What `/` or `@` would insert. At most [visibleCount] rows, then
  /// "Show all" when [onShowAll] is given.
  const KitComposerChips.suggestions({
    super.key,
    required List<KitSuggestion> this.suggestions,
    required ValueChanged<KitSuggestion> this.onSelected,
    this.onShowAll,
    this.note,                             // one line shown when there are no rows (B12, the demo)
    this.listKey,                          // today's Key('inline-command-suggestions')
  });

  static const int visibleCount = 5;       // behaviour constant, not a look token

  // Fields for the three forms; each is null outside its form.
  final String? label;
  final VoidCallback? onPressed;
  final KitModelChipState state;
  final double? contextUsed;
  final List<KitMenuItem> menu;
  final List<KitAttachment>? items;
  final ValueChanged<KitAttachment>? onRemove;
  final List<KitSuggestion>? suggestions;
  final ValueChanged<KitSuggestion>? onSelected;
  final VoidCallback? onShowAll;
  final Key? chipKey, contextKey, stripKey, listKey;
}
```

**Frozen behaviour.**

- **Model chip.** A `KitTappable(shape: KitShape.pill, surface: KitSurfaceLevel.surface3, menu:)` holding a 20 dp model glyph, the label (`label` role, one line; two from 1.3× text) and a 20 dp down chevron. The words by state: `chosen` → `label`; `serverDefault` → `label` when the host knows the default's name, else "Server default"; `signInNeeded` → "Sign in to a model"; `chooseNeeded` → "Choose a model". The chip is never in the error or attention colours (LOOK-4). When the laid-out label would show fewer than six characters, the chip shows only its glyph and chevron (48 dp), and the words move to semantics and the tooltip.
- **Context.** From `contextUsed >= 0.7` the chip adds "· {percent} %" in tabular figures after the label; at `>= 0.95` the words become "· Context almost full". Colour never carries it (STATE-9): text stays `text1`.
- **Attachments.** A wrapping row (`KitChipWrap` spacing) of pills with KitChip.removable's metrics: an image's 30 dp `KitImage` thumbnail (`KitShape.tile`) or the kind's glyph, the label (file names middle-cut, full name in semantics), `detail` in `text2`, and, when `onRemove` is set, a 48 dp remove target labelled "Remove {label}". Tapping the chip body calls `onOpen`. An empty list renders nothing.
- **Suggestions.** A panel of rows (each ≥ 48 dp, a `KitTappable`): commands in `mono` LTR, agents in `rowTitle`; the description in `secondary`/`text2`, wrapped at words, at most two lines with an end ellipsis and the full text in semantics (the map's "truncated mid-word" defect). At most `visibleCount` rows, then "Show all" (`KitButton.tertiary`). An empty list renders nothing, unless the host gives a `note`: then one plain `secondary` line on the same panel surface says why nothing is offered (the demo: "The demo has no commands — send the sample prompt to see a change reviewed.", B12), a live region with no tap target.

**Kit copy** (ARB, `kit` prefix, en + ar): `kitModelServerDefault` "Server default", `kitModelSignIn` "Sign in to a model", `kitModelChoose` "Choose a model", `kitModelChange` "Change model" (chip semantics hint), `kitModelActions` "Model shortcuts" (menu name), `kitModelContext` "{percent} %", `kitModelContextFull` "Context almost full", `kitModelContextLabel` "Context {percent} % full" (semantics), the remove target's label is KitChip's `kitChipRemove` "Remove {label}" (one key, COPY-18; KitChip is tier 1a), `kitAttachmentOpen` "Preview {label}", `kitSuggestionsShowAll` "Show all", `kitSuggestionsLabel` "Suggestions" (list semantics).

## States

Declared (KIT-12):

- **model:** chosen, serverDefault, signInNeeded, chooseNeeded, context warning (≥ 0.7), context almost full (≥ 0.95), narrow (glyph only).
- **attachments:** editable, read-only, with a thumbnail, with a detail ("Recovered"), empty (renders nothing).
- **suggestions:** list, capped with Show all, empty (renders nothing), note (one line saying why nothing is offered).

No loading (the host has the data), no error of its own (a thumbnail that fails is KitImage's failure state), no disabled (a chip that cannot act is not shown, STATE-8; the composer hides the model chip while `readOnly`).

## Tokens

- ThemeRoles: `surface3` (chip and pill fills), `surface2` (suggestions panel inside the glass), `text1`, `text2`, `text3`, `hairline` (between suggestion rows), `accent` (focus ring only).
- KitText roles: `label` (chips), `rowTitle` (agent suggestions), `mono` (commands, technical labels), `secondary` (descriptions, details).
- KitTokens: `minTarget` (48), `smallIconSize` (20), `iconTileSize` (30, thumbnails), `space1`–`space4`, `hairlineWidth(context)` (§0.5 step 2 seam). Shapes `KitShape.pill` and `KitShape.tile` (pre-wave seam enum). Both in `_new-tokens.md`.
- No new token.

## Adaptive

- **compact:** the model chip shrinks to glyph-only when the composer's row is tight (the rule above, never a width literal); attachments wrap to as many lines as needed above the field; suggestions show up to five rows.
- **medium / expanded / large:** the same, inside a wider composer (the chip rarely shrinks). Suggestions from expanded show descriptions on the same line as the label when they fit.
- **Fine pointer:** hover steps (KitTappable); the model chip's tooltip repeats its label and, when it has one, the context words (LAY-11); right-click opens the model menu at the pointer.
- **Keyboard:** each chip and each remove target is a Tab stop in reading order; Delete or Backspace on a focused attachment removes it; in the suggestions, Tab and the arrow keys move between rows, Enter selects, Esc returns focus to the composer's field (KitComposer routes it).

## Accessibility

- Model chip: a button labelled with its words plus ", {context words}" when shown, hint "Change model", and the menu items as custom actions.
- Attachment chip: the body is a button "Preview {label}" (plain text when `onOpen` is null); the remove target is a separate button "Remove {label}"; read-only chips are plain text with the kind ("Image, screenshot.png").
- Suggestions: a list named "Suggestions"; each row is a button whose label is "{label}, {description}".
- 48 dp targets with 8 dp between the chip body and its remove target (LAY-9).
- 200 % text: chip labels go to two lines, attachments wrap, descriptions keep two lines and ellipsize with the full text in semantics (A11Y-8); nothing overflows at 320 dp.

## RTL

- Glyphs and thumbnails at the start, remove targets and chevrons at the end, mirrored. The chevron is vertical (not mirrored).
- File names, paths, references and slash commands are LTR-isolated (KitText.mono or KitBidi.ltr from the host); model and agent names are isolated with KitBidi.auto by the host (COPY-30).
- The "{percent} %" words and digits come from ARB and intl (COPY-30).

## Motion and haptics

- Chips appear and leave with `KitAnimatedRows`-style paint fades on `KitMotion.quick` (no size animation in the composer's layout); the context words cross-fade (`KitSwap`, quick) when they appear.
- The suggestions panel appears at once (it follows typing; motion would lag the keyboard).
- Reduced motion: nothing moves; one `pump()` settles (G8x).
- Haptics: none (MOT-11).

## Data safety and honest state

- Removing an attachment is the host's act; the chip never deletes a file, only asks the host to drop it from the draft (DATA-11: instantly reversible by adding it again, so neither confirm nor Undo).
- The model chip never says "Choose a model" while a model answers (P7.5): the host passes `serverDefault` with the default's name. With nothing signed in it says "Sign in to a model" before the first send, so a failed reply is never the first discovery (P7.7).
- A recovered photo is attached by the host and shown with `detail: "Recovered"`; the host announces it once (AUTO-4 KitAutoLine), and the chip keeps a remove target, so the automation is visible and undoable.
- Raw model ids, provider ids and prices stay out of the chip; they belong in the picker's Details (COPY-11).

## Depends on

- **kit-KitChip** (C25): `KitChipWrap` spacing and the removable metrics.
- **kit-KitMenu** (C25): `KitMenuItem`, the model menu through KitTappable.
- **kit-KitImage** (C25): thumbnails.
- **kit-KitTappable** (tier 1b; edge added, README.md): the model chip, attachment bodies and suggestion rows. It moves this unit from tier 1b to 1c; kit-KitMessage stays 1d and kit-KitComposer is 1e.
- Existing `KitButton.tertiary`, `KitIconButton` (remove targets; IconButton-v2 is tier 1a), `KitText`, `KitMotion`; kit-KitMotionParts (`KitSwap`).
- Used by kit-KitComposer and kit-KitMessage.

## Tests required

`test/kit/kit_composer_chips_test.dart`:

1. Model chip words for each `KitModelChipState`; `serverDefault` with a label shows the label, without one shows "Server default"; none of them is "Choose a model" except `chooseNeeded`.
2. Tap calls `onPressed` once; long-press and right-click open `menu`; the menu items are semantic custom actions.
3. `contextUsed: 0.69` shows no context words; 0.7 shows "· 70 %" in tabular figures; 0.96 shows "· Context almost full"; no attention or danger role is used.
4. At a 120 dp wide slot with a long label the chip shows glyph and chevron only, with the full label in semantics and the tooltip.
5. Attachments: an image shows a thumbnail under `thumbnailKey`; the remove target calls `onRemove` with that attachment; read-only (`onRemove: null`) shows no remove target; tapping the body calls `onOpen`.
6. Suggestions: seven items show five rows and "Show all"; tapping a row calls `onSelected` once; a long description wraps to two lines and ellipsizes at a word, with the full text in semantics.
7. Keyboard (desktop capabilities): Tab order chip → attachments → remove targets; Delete on a focused attachment removes it; arrow keys move between suggestion rows and Enter selects.
8. Semantics: labels and hints as specified; targets ≥ 48×48 with 8 dp between a chip body and its remove target.
9. Reduced motion: one `pump()` settles.
10. 200 % text at 320 dp, LTR and RTL: no overflow (G6).

## Galleries required

`test/goldens/kit/kit_composer_chips_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20), on a `surface2` backdrop like the composer's:

- States at 412×915, dark and light: `kit_composer_chips_model` (chosen), `kit_composer_chips_model_default`, `kit_composer_chips_model_sign_in`, `kit_composer_chips_model_context` (0.85), `kit_composer_chips_model_narrow`, `kit_composer_chips_attachments` (an image thumbnail, a file, a reference, a "Recovered" detail), `kit_composer_chips_attachments_read_only`, `kit_composer_chips_suggestions` (commands, one with a long description, and Show all).
- Default (model chosen + attachments) at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800, dark.
- About 30 PNGs.

## Non-goals

- The model picker sheet, the provider row and sign-in (the host's sheets).
- Choosing, uploading, compressing or persisting attachments (the host; PromptPhotoStore, DraftStore).
- Filtering commands or agents for a query (the host passes the matching suggestions).
- The composer frame and its send, stop, attach and voice controls (KitComposer).

## Open questions

None.
