# KitChoiceList, KitChoiceRow, KitPickerRow — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitChoiceList` (wave 1, tier 1c, kind `kit-part`). Spec: kit-v2.md §1.7, §2.1 (the answer card uses it), §4.1, §8.2; STANDARDS KIT-25, KIT-26, STATE-9, STATE-10, AUTO-8, MOT-11, DATA-2; cut review C24 (`question_options.dart` joins this unit), C25 (ChoiceList = [Field, Receipt]), C42 (the ```choices``` block renders it inside `KitRequestCard(kind: choice)`), and the G9 contract "KitChoiceList.single: one tap, one callback". STANDARDS wins where it differs from kit-v2.md.

## Purpose

One way to pick from a list:

- question options, form radios and `choices` blocks;
- server, destination, organisation, language, voice and shell choices;
- every dropdown, which becomes a `KitPickerRow` that shows its value and opens a sheet.

A single choice acts on tap, with no Apply. A multiple choice is applied by its host's primary.

## Replaces

- **Map elements (kit-v2.json `assignment` → `KitChoiceList`): 37 elements on 31 pages.** They are:
  - agent-choice row;
  - appearance-picker choice;
  - read-aloud voice row;
  - coding-settings shell choice;
  - console organisation;
  - the markdown agent choice;
  - the message-view choice option;
  - the embedded question options;
  - gate options;
  - language choice;
  - local-agent folder radios;
  - managed-workspaces adapter;
  - model-picker agent dialog and dropdown;
  - model-picker options agent;
  - notifications check-in minutes;
  - plugins mapping checkbox;
  - profile-editor backend;
  - question-sheet option and send;
  - server-switcher current and profile;
  - servers-welcome choice;
  - session-approvals mode;
  - session-destination destination;
  - session-export format;
  - session-import destination;
  - shell-output timeout;
  - start-run project and supervision;
  - team-board priority row;
  - team-phone-onboarding project radio;
  - termux-setup runtime radios;
  - voice-model-setup language and pack;
  - web-sources provider and selected.
- **Files and their G16 baseline counts** (the widgets this part retires in them):

  | File | Counts |
  |---|---|
  | `lib/ui/widgets/pickers.dart` | DropdownButtonFormField 2, DropdownMenuItem 4, ChoiceChip 3 (the chips go to KitSegmented) |
  | `lib/ui/screens/termux_setup_screen.dart` | RadioListTile 3, RadioGroup 1 |
  | `lib/ui/screens/chat/approvals_sheet.dart` | RadioListTile 2, RadioGroup 1 |
  | `lib/ui/screens/team/start_run_sheet.dart` | DropdownButtonFormField 2, DropdownMenuItem 3 |
  | `lib/ui/screens/managed_workspaces_screen.dart` | DropdownButtonFormField 1, DropdownMenuItem 1, ListTile 5 |
  | `lib/ui/screens/web_sources_screen.dart` | CheckboxListTile 1, DropdownButtonFormField 1, DropdownMenuItem 1 |
  | `lib/ui/screens/settings/notifications_settings_screen.dart` | DropdownButton 1, DropdownMenuItem 1 |
  | `lib/ui/widgets/local_agent_onboarding.dart`, `widgets/team_phone_onboarding.dart` | RadioListTile 1, RadioGroup 1 each |
  | `lib/ui/screens/settings/server_plugins_section.dart` | CheckboxListTile 1 |
  | `lib/ui/screens/activity_screen.dart` (9), `widgets/server_switcher_sheet.dart` (4), `session_import_screen.dart` (3), `session_destination_sheet.dart` (2), `settings/default_shell_row.dart` (2), `widgets/language_picker.dart` (2), `chat/read_aloud.dart`, `running_work_sheet.dart`, `session_export_screen.dart`, `widgets/appearance_picker.dart` (1 each) | ListTile: those used as choice rows only; the rest go to `KitRow` |
  | `agent_choice_screen.dart`, `chat/message_view.dart`, `servers_screen.dart`, `team/gate_sheet.dart`, `widgets/markdown.dart`, `widgets/question_options.dart`, `widgets/team_board_move_sheet.dart`, `lib/voice/voice_ui.dart` | hand-built bordered cards and trailing checks (0 in G16) |

- **Whole app:**
  - RadioListTile 9 in 5 files;
  - RadioGroup 5 in 5;
  - CheckboxListTile 7 in 7;
  - DropdownButton 4 in 3;
  - DropdownButtonFormField 10 in 7;
  - DropdownMenuItem 21 in 10;
  - DropdownMenu 1 (`form_renderer.dart`).
- **File it absorbs (C24, R12):** `lib/ui/widgets/question_options.dart` joins this unit's write set. The kit never imports domain types (`QuestionChoice`, `PendingQuestion`), so the file stays where it is:
  - `QuestionOptionRow` becomes a thin forwarding wrapper that builds a `KitChoiceRow`;
  - `QuestionCustomAnswerField` forwards to `KitField(kind: multiline)`;
  - `questionPrefersSheet` is untouched.
  
  Each wrapper is marked `/// Retired by kit-KitChoiceList: use KitChoiceRow / KitField` (KIT-43). Its importers (`activity_screen.dart`, `team/team_needs_you.dart`, `chat_screen.dart`, and the tests `chat_question_card_test.dart` and `activity_requests_test.dart`) keep compiling.

## File

`lib/ui/kit/kit_choice_list.dart`. It holds `KitChoice<T>`, `KitChoiceOther`, `KitChoiceList<T>`, `KitChoiceRow<T>`, `KitPickerRow<T>` and `showKitChoiceSheet<T>`. Tests go in `test/kit/kit_choice_list_test.dart` and galleries in `test/goldens/kit/kit_choice_list_golden_test.dart`.

## Public API

```dart
@immutable
class KitChoice<T> {
  const KitChoice({
    required this.value,
    required this.title,           // ≤ 2 lines
    this.supporting,               // ≤ 2 lines; a disabled choice's reason goes here
    this.leading,                  // a kit mark only: KitRowIcon, KitStatusMark, KitIcon
    this.recommended = false,      // adds "Recommended" to the supporting line (words, not colour)
    this.enabled = true,
    this.disabledReason,           // required when !enabled
    this.key,
  }) : assert(enabled || disabledReason != null);
  final T value;
  final String title;
  final String? supporting;
  final Widget? leading;
  final bool recommended;
  final bool enabled;
  final String? disabledReason;
  final Key? key;
}

/// "Something else": a free answer below the options (a question's custom
/// answer). Its text is a draft of the request it answers.
@immutable
class KitChoiceOther {
  const KitChoiceOther({
    required this.label,           // "Something else"
    required this.fieldLabel,      // "Your answer"
    required this.onSubmitted,     // sends the typed answer
    this.draft,                    // required inside a sheet (G48); keyed by the request
    this.fieldKey,
  });
  final String label;
  final String fieldLabel;
  final ValueChanged<String> onSubmitted;
  final KitDraft? draft;
  final Key? fieldKey;
}

class KitChoiceList<T> extends StatelessWidget {
  /// One answer. [actsOnTap] (default): a tap chooses and the host closes
  /// its sheet, or the answer is sent; there is no Apply. false: the tap
  /// only selects, and a form's primary sends everything (an OC2 form).
  /// [sends]: the tap sends the person's answer to the agent
  /// (KitHaptics.send, the receipt on the chosen row, further taps
  /// ignored until the host passes a new [receipt] or [selected]).
  const KitChoiceList.single({
    super.key,
    required List<KitChoice<T>> choices,
    required T? selected,
    required ValueChanged<T> onSelected,
    this.actsOnTap = true,
    this.sends = false,
    this.receipt,                 // KitReceipt shown on the chosen row once sent (STATE-10)
    this.other,
    this.loading = false,         // skeleton rows (KitSkeletonRows)
    this.empty,                   // shown when choices is empty and !loading: an inline KitStateView saying why
    this.semanticsLabel,          // the group's name
  });

  /// Several answers. The host's primary applies them ("Use 3 sources").
  const KitChoiceList.multi({
    super.key,
    required List<KitChoice<T>> choices,
    required Set<T> selected,
    required ValueChanged<Set<T>> onChanged,
    this.loading = false,
    this.empty,
    this.semanticsLabel,
    this.showCount = true,        // false: the host's primary names the count
  });

  // choices / selected / onSelected (single) and selected / onChanged
  // (multi) are held in private fields; the two named constructors are
  // the only public way in.
  final bool actsOnTap;
  final bool sends;
  final KitReceipt? receipt;
  final KitChoiceOther? other;
  final bool loading;
  final KitStateView? empty;
  final String? semanticsLabel;
  final bool showCount;           // multi only; single is always false
}

enum KitChoiceMark { radio, check }

/// A KitRow with a leading selection mark (a filled radio or a check) and,
/// for the value in use now, the word "Current" first on its supporting line.
/// Used by KitChoiceList, KitSegmented's stacked form and the request card.
class KitChoiceRow<T> extends StatelessWidget {
  const KitChoiceRow({
    super.key,
    required this.choice,
    required this.selected,
    required this.onTap,           // null: disabled (the choice's reason shows)
    this.mark = KitChoiceMark.radio,
    this.current = false,          // "Current · …" (STATE-9)
    this.receipt,
    this.rowKey,
  });
}

/// A setting row that shows its value and opens a sheet with
/// KitChoiceList.single: "Language · English ›". Replaces DropdownButton.
/// With one choice it is a plain row showing that value, with no chevron
/// and no sheet (AUTO-8: a picker with one option is skipped).
class KitPickerRow<T> extends StatelessWidget {
  const KitPickerRow({
    super.key,
    required this.title,           // "Language"
    required this.choices,
    required this.selected,
    required this.onSelected,      // null: disabled (disabledReason required)
    this.sheetTitle,               // default: title
    this.leading,                  // a kit mark
    this.valueLabel,               // override the shown value (e.g. "Follow the system")
    this.supporting,
    this.disabledReason,
    this.rowKey,
    this.sheetKey,
  }) : assert(onSelected != null || disabledReason != null);
}

/// The picker sheet: showKitSheet + KitChoiceList.single(actsOnTap: true).
/// Returns the chosen value, or null when dismissed. Choosing the current
/// value returns it too.
Future<T?> showKitChoiceSheet<T>(
  BuildContext context, {
  required String title,
  required List<KitChoice<T>> choices,
  T? selected,
  String? subtitle,
  Key? sheetKey,
});
```

- **Current.** `KitChoiceList.single` marks the `selected` row as `current` only when `actsOnTap && !sends`, as in a picker or a setting. A question's options never say "Current"; they show the receipt once answered.
- **One tap, one callback (G9).**
  - A tap calls `onSelected` or `onChanged` exactly once, and nested gesture detectors never double-fire.
  - With `sends: true`, taps after the first are ignored until the host rebuilds with a new `receipt` or `selected`, so a double tap never sends twice.
  - Without `sends`, a tap on the already-selected row still calls `onSelected`, so a picker sheet closes on it.
- **Kit copy** (ARB, `kit` prefix): `kitChoiceCurrent` "Current", `kitChoiceRecommended` "Recommended", `kitChoiceOther` "Something else", `kitChoiceSelectedCount` "{count, plural, …} selected".

## States

The doc comment declares, for `KitChoiceList`: `default`, `loading` (skeleton rows), `empty` (inline `KitStateView` saying why), `choice-disabled` (reason on its supporting line), `multi` (several checked), `other` (the "Something else" field open), `sending` (the receipt says Sending), `answered` (the receipt says Sent or Answered), and `answered-elsewhere` (the receipt says "Answered on the laptop"). `KitPickerRow` declares `default`, `single-option` and `disabled`.

- **Loading.** `KitSkeletonRows` sized to 3 rows (STATE-4); the host passes `loading: true` while choices are fetched.
- **Empty.** The host's `empty` view says why ("No voices downloaded yet") and offers the step. The kit asserts that `empty` is non-null whenever `choices` is empty and `loading` is false.
- **Error.** The host shows a `KitNotice` above the list with Try again. The list keeps the last choices.
- **Disabled.** Dimmed title (`text3`). The reason is on the supporting line in `text2` (STATE-8). The row is not tappable but is still read.
- **Working.** With `sends`, the chosen row carries the `KitReceipt` in its sending phase. The other rows stay visible and are inert.
- **Answered.** The receipt on the chosen row says "Sent", then "Answered", and escalates to "Not confirmed yet" after 8 s without the server's echo (STATE-10, through `KitReceipt`). It is never just a filled radio.

## Tokens

All of the following exist on `feat/visual-language-v1` unless flagged.

- **ThemeRoles:**
  - `surface1`: the rows' panel;
  - `hairline`: separators, inset to the text start;
  - `accent`: the selected radio dot and the check (the current-selection mark, LOOK-6) and the focus ring;
  - `text2`: the unselected radio ring and the check box, at 3:1 or more against the panel (LOOK-8);
  - `text1`: titles;
  - `text3`: disabled titles, the picker row's value and the chevron.
- **KitText roles:**
  - `rowTitle`: title;
  - `secondary`: the supporting line, "Current", "Recommended" and the disabled reason;
  - `caption`: the multi count.
- **KitTokens:**
  - `rowHeight` (54) and `rowHeightTwoLine` (60) as the basis;
  - `minTarget` (48);
  - `smallIconSize` (20): the mark;
  - `iconTileSize` (30): a leading tile;
  - `space2`–`space4` and `gutter`;
  - `panelCornerRadius` (18): the panel.
- **KitMotion:** `quick` for the mark change, and `KitAnimatedRows` for a list that changes while open.
- **New tokens (pre-wave, `_new-tokens.md`):** `KitTokens.choiceRowMinHeight` = 56. KIT-25 and kit-v2.md §1.7 say choice rows are at least 56 dp, while the VL one-line row is 54, so the choice row needs its own named minimum instead of a literal. It is added by the kit-token seam; until it lands this unit reads `rowHeightTwoLine` (60) as the minimum and records the gap.

## Adaptive

- **Compact.** Rows are full width in a `surface1` panel. `showKitChoiceSheet` is a bottom sheet (content height, up to 90 %), and `KitPickerRow` opens it.
- **Medium.** The sheet is capped at 640 dp and centred (through `showKitSheet`).
- **Expanded and large.** The sheet becomes a centred panel of up to 560 dp (through `showKitSheet`). Rows get a hover highlight, the next surface step above the `surface1` panel (`surface2` in dark, `surface3` in light, through `KitTokens.fillOf`; KitTappable's rule, README.md decision D11), and a focus ring. There is no anchored dropdown (see Non-goals).
- **Short windows (< 480 dp tall).** The list scrolls inside the sheet, and the "Something else" field stays above the keyboard.
- **Keyboard (§8.2, §8.3, G14):**
  - The list is one Tab stop, entered on the selected row (or the first).
  - Arrow Up and Down move focus within the group; they do not select.
  - Space selects the focused row (single: it acts; multi: it toggles), and Enter does the same.
  - Home and End jump to the first and last row.
  - Esc closes the host sheet (returning null).
  - `KitPickerRow`: Enter or Space opens the sheet.
- **Fine pointer.** A hover highlight. A tooltip only repeats a truncated title (A11Y-8).

## Accessibility

- **Row semantics.** Each row is one merged node: `button`, `selected` (single) or `checked` (multi), and `inMutuallyExclusiveGroup` (single), with the title, then the supporting line, then "Current" or the receipt's words. The group has `semanticsLabel`.
- **The mark is a shape.** A filled radio or a check box with a check, never colour alone (STATE-9).
- **Targets.** Rows are at least 56 dp and fully tappable (KIT-25), with no separate small radio target.
- **Announcements.** A sent answer's receipt change is announced once by `KitReceipt`. Loading is not a live region.
- **200 % text.** Choice titles and supporting lines wrap and are never truncated: the choice is the decision, so A11Y-8's optional row-title truncation is not used here. Rows grow. The picker row's value moves under the title instead of truncating.

## RTL

- The mark sits at the start and the chevron at the end, mirrored.
- Titles follow the locale. A title that is data (a server, project or voice name) is isolated with `KitBidi.auto`. Technical supporting text (a path, a host) is isolated with `KitBidi.ltr`.
- Arrow keys are vertical, so they are unaffected.

## Motion and haptics

- The mark changes (the radio dot grows in, the check draws) on `KitMotion.quick`. A list whose choices change while open uses `KitAnimatedRows`.
- The "Something else" field unfolds with `KitReveal`.
- Everything is instant under `KitMotion.reduced`.
- **Haptics.** `KitHaptics.send` only when `sends` is true (the person's words leave, MOT-11). There is nothing on a local choice, a picker or a multi toggle.

## Data safety and honest state

- **Drafts.** The `other` field keeps its text as a draft of the request it answers (`KitDraft`, key `oc.draft.<target>.<profileId>`, DATA-2), so dismissing a question sheet never loses a typed answer.
- **Undo, not confirm (DATA-11).** A local choice is "neither": the choice is its own undo. A sent answer's Undo, where the server allows withdrawal, is `KitReceipt.onUndo` in place, never a confirmation.
- **Honest state.**
  - Once sent, the row shows the receipt's words (Sending, Sent, Answered, Not confirmed yet, Refused) and never only a filled radio.
  - "Sent" is never "Done" (STATE-10).
  - Answering elsewhere shows "Answered on the laptop" (from the host's `RequestRoutes` and the receipt).
  - A disabled choice always states why.
  - A picker with one option is not a picker (AUTO-8).

## Depends on

- **KitField** (kit-KitField): the `other` field and the `QuestionCustomAnswerField` wrapper.
- **KitReceipt** (kit-KitReceipt): the sent and answered states.
- **KitSheet** (existing `showKitSheet`, with the v2 look from kit-KitSheet-v2, C17): `showKitChoiceSheet` and `KitPickerRow`.
- **Existing parts:**
  - `KitRow`, `KitRowIcon`, `KitChevron`, `KitStatusMark` (marks in `leading`);
  - `KitSkeletonRows`, `KitStateView`;
  - `KitAnimatedRows`, `KitReveal`, `KitHaptics`, `KitDraft`;
  - `KitText`/`ThemeRoles`/`KitTokens` (VL).
- **Cut map:** C25 lists ChoiceList = [Field, Receipt], which matches. The KitSheet dependency is on an existing API.
- **Used by:** kit-KitSegmented (its stacked form) and kit-KitRowParts-v2 (the risk step's "for how long" choice), both edges added to the cut (README.md, decision D7); kit-KitRequestCard-v2, kit-KitDiffView (C25), and the answer-the-agent screens.

## Tests required

In `test/kit/kit_choice_list_test.dart` (G9, G14, G37, TEST-15), with `chat_question_card_test.dart` and `activity_requests_test.dart` kept green through the wrappers:

1. **Single, one tap, one callback.**
   - A tap on a row calls `onSelected` exactly once, with no double from the mark and the row.
   - With `sends: true`, a rapid double tap calls it once, and a further tap is ignored until `receipt` changes.
   - Without `sends`, a tap on the selected row calls `onSelected` with its value.
2. `actsOnTap: false`: a tap selects (the mark moves), and the host's primary is required to act (no close). With `actsOnTap: true` inside `showKitChoiceSheet`, a tap pops the route with the value.
3. Multi: a tap toggles membership and `onChanged` receives the new set. Semantics say `checked`.
4. Disabled choice:
   - it asserts without a reason;
   - its reason is visible on its supporting line;
   - a tap does nothing.
5. Selection is marked by shape (a radio dot or a check found by semantics or icon) and by semantics `selected` or `checked`. "Current" shows only for a picker or local single choice, and never with `sends`.
6. `sends`: `KitHaptics.send` fires once per sent answer and never with Vibration off. It never fires for local choices.
7. The receipt shows Sending, then Sent, then Answered on the chosen row. After 8 s without an echo it shows "Not confirmed yet" (fake async, through `KitReceipt`).
8. `other`:
   - "Something else" opens a `KitField`;
   - `onSubmitted` sends the text;
   - with a draft, dispose and remount restores the text.
9. `loading` shows skeleton rows. Empty choices with `loading: false` and no `empty` asserts. `empty` renders the given view.
10. `KitPickerRow`:
    - it shows its title and the selected choice's title (or `valueLabel`);
    - a tap opens the sheet, and choosing returns the value and calls `onSelected`;
    - one choice renders no chevron and opens no sheet;
    - a disabled row without a reason asserts.
11. Keyboard:
    - Tab enters on the selected row;
    - Arrow Down moves focus without calling `onSelected`;
    - Space and Enter select;
    - Esc closes `showKitChoiceSheet` with null.
12. RTL: the mark is at the start and the chevron at the end, mirrored.
13. Rows are at least 56 dp and fully tappable (G5 tap-target guideline).
14. The `QuestionOptionRow` wrapper renders a `KitChoiceRow` with the same label, selection and tap behaviour. The `QuestionCustomAnswerField` wrapper renders a multiline `KitField`.
15. Under reduced motion one `pump()` settles. Overflow (G6): at 320–1600 dp and 915×412, text 1.0, 1.3 and 2.0, LTR and RTL, there is no exception.

## Galleries required

`test/goldens/kit/kit_choice_list_golden_test.dart`, at DPR 3 (TEST-9). There are 2 × 11 + 18 = 40 PNGs, under the TEST-20 cap.

- **Every declared state, dark and light, at 412×915:**
  - default (single, in a sheet via `showKitChoiceSheet`);
  - loading;
  - empty;
  - choice-disabled;
  - multi;
  - other (the field open);
  - sending;
  - answered;
  - answered-elsewhere;
  - picker-row (a settings panel with three `KitPickerRow`s, one single-option);
  - picker-disabled.
- **The default state, dark and light, at the other sizes:** 360×800, 915×412, 800×1280, 1280×800 (a centred panel) and 1600×1000.
- **The default state at text 2.0 and in Arabic RTL, dark and light, at 412×915 and 1280×800.**

## Non-goals

- No anchored dropdown menu on PC in v1: the picker is the same sheet everywhere and a panel on wide windows. `KitMenu` is for actions, not values.
- No segmented control: KitSegmented builds its stacked form from `KitChoiceRow`, not the other way round (the C25 correction dropped ChoiceList → Segmented).
- No request card chrome (`KitRequestCard`), no tree or nested choices, no drag-to-reorder, and no search inside the sheet (see KitSearchField's open question).
- No domain types in the kit.
- No migration of call sites (wave 2), apart from the two wrappers in `question_options.dart`.

## Open questions

None. `KitTokens.choiceRowMinHeight` is a pre-wave token (`_new-tokens.md`).
