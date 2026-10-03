# KitText v2: API freeze (wave 0)

Unit: `kit-KitText-v2` (kind `kit-change`, tier 1a, after the `feat/visual-language-v1` merge; cut review C10). Spec for `revamp.workflow.js` (`spec` field in `work-units.json`).

## Purpose

KitText draws every word in the app in one of the visual-language type roles. The role sets size, line height, weight and tracking, and the tone sets the colour. Version 2 keeps the VL branch's `KitText` and `KitText.rich` as they are. It adds selectable text (`KitText.selectable`, `KitSelectable`), a `KitText.mono` for technical values that isolates LTR and aligns to the start of the row, an LTR block region (`KitLtr`), and tabular figures. With these, no `Text`, `SelectableText`, `SelectionArea` or `Directionality` needs to stay outside the kit.

## Replaces

- **G16 baseline** (`test/kit_ratchet_baseline.json`, files outside `lib/ui/kit/`):
  - `Text` 2,241 in 174 files. The VL `KitText` already covers this; v2 covers the jobs it could not do.
  - `SelectableText` 53 in 39 files. `SelectableText.rich` ×2 in `lib/ui/widgets/markdown.dart` goes to `KitText.selectableRich` through the chat part `KitMarkdown`.
  - `SelectionArea` 5 in 5 files: `about_screen.dart`, `app_diagnostics_screen.dart`, `file_preview.dart`, `delimited_file_preview.dart`, and `desktop_interaction.dart`'s `DesktopSelectionArea`.
  - `SelectionContainer` 1 (`file_preview.dart:676`, `.disabled`).
  - `Directionality` 10 in 10 files. They go to `KitText.mono` or `KitLtr`: `terminal_screen`, `local_terminal_screen`, `technical_direction`, `file_preview`, `delimited_file_preview`, `phone_server_card`, `setup_progress_view`, `team_host_form`, `svg_file_preview`, `markdown`.
  - `DefaultTextStyle` 1. R23/C27 allows it only in `app_theme.dart`; everywhere else a role replaces it.
- **Files with the most text:**
  - `usage_screen.dart` 61;
  - `termux_setup_screen.dart` 55;
  - `review_workspace.dart` 51;
  - `external_agents_screen.dart` 50;
  - `chat_screen.dart` 46.
- **Classes it makes redundant.** They are left in place, because their files are not in this unit's write set (PROC-10). The screen units move their callers, and the unit that brings each count to zero deletes the class (KIT-43).
  - `TechnicalDirection` (`lib/ui/widgets/technical_direction.dart`) → `KitLtr`.
  - `DesktopSelectionArea` (`lib/ui/desktop/desktop_interaction.dart`) → `KitSelectable(mode: KitSelectMode.finePointer)`.
- **kit-v2.json `assignment`:** no element is assigned. The §9.2 parts postdate the assignment.

## File

`lib/ui/kit/kit_text.dart` (created on the VL branch). The write set is that file, `test/kit/kit_text_test.dart` and `test/goldens/kit/kit_text_golden_test.dart`.

## Public API

Existing API, unchanged (R11/KIT-43): `KitTextRole`, `KitTextTone`, `KitText(...)`, `KitText.rich(...)`, `KitText.styleFor`, `KitText.defaultTone`, `KitText.toneColor`, `KitText.styleOf`, `KitText.textTheme`. `styleFor(KitTextRole.button)` stays **16/20 w600** (LOOK-12, G19): the VL branch already has it (`kit_text.dart`, checked at `cca62454`), so there is no value change.

Added by the open-project polish (additive): `KitText.link(String)`, a quiet inline link (secondary role, accent tone) for "In /root/projects · Change"; wrap it in a `KitTappable` for the tap.

Added by slice-P9.10 (additive): `KitText.appScaler(TextScaler scaler, {required double max})`, the app-wide text scale with only the extreme top end capped (A11Y-8: only kit parts clamp text scale; `main.dart` calls it with `AppTheme.maxTextScale`), and `KitText.sentenceCase(String)`, the first letter capitalised and nothing else (LOOK-15), for names the app derives from ids.

```dart
enum KitTextRole { largeTitle, title, headline, body, rowTitle, secondary, label, caption, button, mono } // unchanged
enum KitTextTone { primary, secondary, tertiary, accent, onAccent, attention, danger, success }         // unchanged

/// How a selectable region turns on (new).
enum KitSelectMode {
  /// Long-press on touch, drag with a mouse: About, diagnostics, a file preview.
  always,

  /// Only while a fine pointer is in use (KitLayout.finePointer): the
  /// transcript, where long-press on touch belongs to the row menu.
  /// Replaces DesktopSelectionArea.
  finePointer,
}

/// What mono text does when it does not fit (new; A11Y-8).
enum KitMonoCut {
  wrap,   // wraps at any character; a code line, a long command
  end,    // one line, "…" at the end; an id, a host
  middle, // one line, "…" in the middle; a path keeps its root and its file name
}

class KitText extends StatelessWidget {
  // Existing constructors gain one optional parameter (additive):
  const KitText(
    String text, {
    Key? key,
    KitTextRole role = KitTextRole.body,
    KitTextTone? tone,
    int? maxLines,
    TextOverflow? overflow,
    TextAlign? textAlign,       // start/center/end only (G7); never left/right
    bool? softWrap,
    String? semanticsLabel,
    bool tabular = false,       // NEW: tabular figures (FontFeature.tabularFigures) for numbers that line up (LOOK-18)
  });

  const KitText.rich(
    InlineSpan span, {
    Key? key,
    KitTextRole role = KitTextRole.body,
    KitTextTone? tone,
    int? maxLines,
    TextOverflow? overflow,
    TextAlign? textAlign,
    bool? softWrap,
    String? semanticsLabel,
    bool tabular = false,       // NEW
  });

  /// NEW. Text the person can select and copy. Long-press on touch, drag with
  /// a mouse. The toolbar offers Copy and Select all, and nothing else.
  /// Not a Tab stop.
  const KitText.selectable(
    String text, {
    Key? key,
    KitTextRole role = KitTextRole.body,
    KitTextTone? tone,
    int? maxLines,
    TextAlign? textAlign,
    String? semanticsLabel,
    bool tabular = false,
  });

  /// NEW. Selectable spans (the Markdown part's paragraphs).
  const KitText.selectableRich(
    InlineSpan span, {
    Key? key,
    KitTextRole role = KitTextRole.body,
    KitTextTone? tone,
    int? maxLines,
    TextAlign? textAlign,
    String? semanticsLabel,
    bool tabular = false,
  });

  /// NEW. A technical value the app did not write: a path, command, host, id,
  /// branch, URL or version. It uses the mono role and is laid out left to
  /// right whatever the reading direction (LOOK-16, KIT-32). It is aligned to
  /// the START of the ambient direction, so under Arabic it sits at the row's
  /// right edge: the paragraph is LTR with TextAlign.end. When [cut] is not
  /// [KitMonoCut.wrap], the semantics carry the full value.
  const KitText.mono(
    String text, {
    Key? key,
    KitTextTone? tone,                 // default: the mono role's primary
    KitMonoCut cut = KitMonoCut.wrap,
    int? maxLines,                     // wrap only; end/middle are always one line
    bool selectable = false,
    String? semanticsLabel,            // default: the full text
  });

  // Unchanged statics: styleFor, defaultTone, toneColor, styleOf, textTheme.
}

/// NEW. A selectable region around several texts (SelectionArea's job).
class KitSelectable extends StatelessWidget {
  const KitSelectable({
    Key? key,
    required Widget child,
    KitSelectMode mode = KitSelectMode.always,
  });

  /// Content inside a selectable region that must not be selected, such as
  /// a preview's buttons (SelectionContainer.disabled's job).
  const KitSelectable.excluded({Key? key, required Widget child});
}

/// NEW. A block of technical content that is not one text: a table of
/// values, a terminal strip, a key/value grid. It is forced left to right.
/// Never used for prose, a server's description or a whole mixed-content
/// card (the same rule as TechnicalDirection). A placeholder inside a
/// sentence is isolated with KitBidi.ltr / KitBidi.auto (the pre-wave seam,
/// COPY-30), not with this.
class KitLtr extends StatelessWidget {
  const KitLtr({Key? key, required Widget child});
}
```

## States

- **Plain and rich text:** no data states. Text that stands for something disabled uses `KitTextTone.tertiary` (LOOK-14). It is never faded with opacity.
- **Selectable:** idle, and selected (handles and toolbar). The selection colour, cursor and handles come from the theme's `textSelectionTheme`, which uses the accent (LOOK-6; `app_theme.dart:551`).
- **Mono cut:** `wrap`, `end` or `middle`. For `middle`, the part measures the text and keeps the head and the tail around one "…". Each cut value exposes the full string in semantics.
- Loading, empty, error and working do not apply. Those belong to the host part.

## Tokens

- **KitText roles (LOOK-12):**
  - largeTitle 32/38 w650;
  - title 24/30 w650;
  - headline 17/22 w600;
  - body 16/24;
  - rowTitle 16/22 w500;
  - secondary 14/20;
  - label 13/18 w600;
  - caption 12/16 w600;
  - button 16/20 w600;
  - mono 13/19, AppMono.
- **Faces:** AppSans, or the system face with zero tracking under Arabic, taken from `theme.textTheme.bodyLarge`.
- **ThemeRoles:** `text1`, `text2`, `text3`, `accent`, `onAccent`, `attention`, `danger`, `success` (through `KitText.toneColor`). Selection uses `accent` through the theme.
- **KitTokens:** none.
- **New tokens:** none.

## Adaptive

- The roles do not change with the window class: there is one type scale (VL §2).
  - **compact:** as specified.
  - **medium, expanded, large:** the same sizes. Reading width comes from `KitScreen` (720), not from KitText.
- **Fine pointer (§8.3):**
  - an I-beam cursor over selectable text;
  - drag selects;
  - Ctrl+C copies and Ctrl+A selects all within a `KitSelectable`;
  - `KitSelectMode.finePointer` regions turn on only while `KitLayout.finePointer` is true.
- **Keyboard:** selectable text is never a Tab stop, so Tab moves between actions (LAY-10).

## Accessibility

- **Semantics:**
  - The text is read as written, or as `semanticsLabel`.
  - `KitText.mono` with a cut reads the full value.
  - `KitSelectable` adds no extra node.
- **200 % text:** every role scales with the person's `TextScaler`, and KitText never clamps (A11Y-8). Callers keep `maxLines` only where A11Y-8 allows truncation (a row title, a name or path in a list, a chip). Everything else wraps.
- **Contrast:** every tone colour is an opaque role at alpha 255 that meets LOOK-8. There are no shadows or glow on text (LOOK-14).
- **48 dp** does not apply: text is not a control.

## RTL

- `KitText.mono` and `KitLtr` lay content out LTR. A standalone value aligns to the start of the ambient direction (COPY-30).
- Code blocks, logs and diffs are LTR blocks aligned left in both directions. That is their own parts' job, not this one.
- The Arabic locale keeps letter spacing 0 on every role (LOOK-11).
- No direction-control characters are inserted (COPY-26). Isolation is done by layout direction.

## Motion and haptics

- Nothing moves.
- No `KitHaptics`. Platform text-selection feedback is the framework's own; KitText adds none.

## Data safety and honest state

- A secret is never shown with `KitText`, `KitText.selectable` or `KitText.mono`. Secrets are entered only through `KitField.secret` and never shown again (§4.10, SEC-3).
- KitText does not redact. A value that may carry a credential (a URL with a token, a header) goes through the redaction in `KitDetailsFold`, `KitLogPanel` or `KitTechnicalValue` first (G12).
- A middle cut keeps both ends of a path visible, so a cut value is never mistaken for another one. The full value is in semantics and, through the host's `KitTappable.tooltip`, on hover.

## Depends on

- The `feat/visual-language-v1` merge: `kit_text.dart`, `ThemeRoles`, and the Geist faces.
- No wave-1 part.
- `KitLayout.finePointer` (existing).

## Tests required

`test/kit/kit_text_test.dart`:

1. `styleFor(role)` equals LOOK-12 exactly for all ten roles, including button 16/20 w600 (G19).
2. Under an Arabic locale every role has letter spacing 0, and the fallback list contains Noto Sans Arabic.
3. Every tone colour has alpha 255 in `graphiteDark`, `graphiteLight` and a `deriveRoles` pack.
4. `KitText.mono` under `TextDirection.rtl`:
   - the paragraph's direction is LTR;
   - a short value's right edge equals its parent's right edge;
   - under LTR its left edge equals the parent's left edge.
5. `KitMonoCut.middle` on `/home/user/projects/very/long/path/main.dart` in 200 dp shows the head and `main.dart` with one "…", and its semantics label is the full path. `end` keeps the head.
6. `KitText.selectable`:
   - a long-press selects a word;
   - the toolbar offers Copy and Select all only;
   - Tab from a preceding button skips the text.
7. `KitSelectable(mode: finePointer)`:
   - with `debugPlatformCapabilities` touch, a drag does not select, and a long-press reaches an ancestor `KitTappable` (its menu opens);
   - with desktop, a drag selects.
8. `KitSelectable.excluded`: Select all followed by Copy leaves out the excluded content.
9. `tabular: true` sets `FontFeature.tabularFigures()`.
10. `KitLtr` lays a `Row` out left to right under an RTL ambient.
11. At `TextScaler.linear(2)` the rendered font size is twice the role size (no clamp).
12. Reduced motion (G8): no ticker runs after one `pump()`.

## Galleries required

`test/goldens/kit/kit_text_golden_test.dart`, at DPR 3 on `TargetPlatform.android` (TEST-9, ARCH-11):

- **States:**
  - `type`: all ten roles in their default tone, plus one line per tone;
  - `mono`: wrap, end and middle, in LTR;
  - `selectable_selected`: a selected word with handles.
  Each in dark and light at 412×915.
- **Default state (`type`):** dark and light at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **`_text2` and `_ar`:** `type` and `mono` at 412×915 and 1280×800, dark and light. The Arabic `mono` shot shows start alignment on the right.
- **G6 overflow matrix** (no images): 320–1600 dp and 915×412, at 1.0, 1.3 and 2.0, LTR and RTL.

## Non-goals

- No new roles: no `number` and no `heading` (Appendix A #26, LOOK-18).
- No Markdown, links or inline terms. Those are `KitMarkdown` and `KitTerm`.
- No editable text. That is `KitField`.
- No redaction.
- No inline bidi isolation of placeholders. That is `KitBidi`, a pre-wave seam.
- No change to the `textTheme` slots beyond LOOK-12 (§0.5 step 1 is the coordinator's).
- No edits to `technical_direction.dart`, `desktop_interaction.dart` or `markdown.dart`: they are not in the write set.
- No call-site migration.

## Open questions

None. Two decisions this freeze makes from the specs:
- **Button role 16/20.** The VL branch already matches LOOK-12 and G19; test 1 pins it.
- **The old wrappers stay where they are.** `TechnicalDirection` and `DesktopSelectionArea` are not made into forwarders here: their files belong to no wave-1 kit unit, and KIT-43 lets the last caller's unit delete them.
