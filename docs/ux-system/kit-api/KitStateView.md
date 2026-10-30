# KitStateView v2 — API freeze (wave 0)

> **Copy (SEC-13, coordinator 2026-09-27):** copies of technical values, logs and details use `KitCopy.copy(context, text)` (redacted). A part that also copies the person's own content passes `redact: false` for that content.

Unit: `kit-KitStateView-v2` (wave 1, tier 1c, kind `kit-change`, model opus). Spec: kit-v2.md §2.2, §4.9, design standard §3, with cut review C24 (correction: `product_states.dart` stays in shared-system-1), C25 (dependencies) and C26 (error defaults, `since`/`onSlow`, report within 2 taps). Rules: STATE-1, STATE-2, STATE-3, STATE-5, STATE-12, STATE-13, STATE-20, LOOK-23, KIT-12, KIT-38, KIT-43, SEC-2, SEC-11.

## Purpose

Every moment that is not the normal content: loading a whole screen, empty, error, stopped or offline, blocked, a missing capability. Page or inline, with fixed slots. v2 adds:

- `KitStateView.missing` for the `whenMissing` modes *explains* and *offers-enable*;
- `KitStateView.error` with the kit's error defaults (Copy details, and Report a problem or the network fix);
- the 8 s escalation inside the kit (`since`, `onSlow`);
- the details slot rebuilt on `KitDetailsFold`.

## Replaces

- **Map elements, 23 on 20 pages (kit-v2.json assignment → `KitStateView`):**
  - `bootstrap-gate` (bootstrap-gate-failure);
  - `demo` (demo-empty);
  - `embedded-product-states` (embedded-product-states-empty-action, embedded-product-states-error-retry, embedded-product-states-gated-row);
  - `external-agents` (external-agents-empty);
  - `files` (files-symbols-hint);
  - `files-file-viewer-sheet` (files-file-viewer-sheet-error);
  - `isolated-task-sheet` (isolated-task-sheet-failure);
  - `pairing-scanner` (pairing-scanner-recovery);
  - `prompt-stash-sheet` (prompt-stash-sheet-empty);
  - `provider-quota` (provider-quota-setup);
  - `review-workspace` (review-workspace-empty-error);
  - `running-work-sheet` (running-work-sheet-empty);
  - `saved-permissions` (saved-permissions-error);
  - `session-context` (session-context-states);
  - `session-relations` (session-relations-states);
  - `termux-processes` (termux-procs-empty);
  - `termux-setup-failed` (failure-line);
  - `termux-storage` (termux-storage-intro);
  - `todos-sheet` (todos-sheet-empty, todos-sheet-error);
  - `worktrees` (worktrees-empty-create).
- **Old shared states:** `ProductErrorState`, `ProductEmptyState` and `ProductInlineEmpty` (`lib/ui/widgets/product_states.dart`; G16 there: Container 4, FilledButton 2, FractionallySizedBox 2, GestureDetector 1, Icon 4, ListTile 1, MaterialBanner 1, Opacity 1, SingleChildScrollView 2, SnackBar 2, Text 16, TextButton 3) become thin wrappers over `KitStateView` in **shared-system-1** (C24 correction: the file holds 8 classes and stays in wave 2a). This unit only provides what those wrappers need. Deletion is KIT-38 (whoever brings the count to zero, at the latest slice-P9.10; the `kit.dart` re-export goes in kit-hygiene).
- **Retires:**
  - slice-P7.6 (fake-clock escalation) and slice-P8.3 (error defaults, report within 2 taps) for state views (C41);
  - the private fold (`e7SetupDetails` / `e7SetupHideDetails` labels, `kit_state_view.dart:189-248`), now `KitDetailsFold` with kit-level keys.

## File

- `lib/ui/kit/kit_state_view.dart` (write set: this file only, per work-units.json and C24)
- Tests: `test/kit/kit_state_view_test.dart`
- Gallery: `test/goldens/kit/kit_state_view_golden_test.dart`

## Public API

```dart
enum KitStateSize { page, inline } // UNCHANGED

class KitStateView extends StatefulWidget {
  // UNCHANGED parameters, then NEW optional ones.
  const KitStateView({
    super.key,
    required this.icon,
    required this.title,
    this.tone = AppStatusTone.neutral,
    this.body,
    this.progress,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.details,                 // raw text → the fold's mono block (redacted)
    this.detailNotes = const [],  // → the fold's notes
    this.size = KitStateSize.page,
    this.titleKey,
    this.bodyKey,
    this.liveRegion = true,
    this.iconChild,
    this.content,
    this.footer,
    this.detailsChild,            // → the fold's child
    this.padding,
    this.illustration,
    this.illustrationAmbient = false,
    this.illustrationWidth,
    // NEW
    this.detailValues = const [], // List<KitTechnicalValue> → the fold's values
    this.since,                   // the wait started here; escalates after KitMotion.escalateAfter (8 s)
    this.onSlow = const [],       // ≤ 2 ways out shown after the escalation
    this.detailsKey,              // the fold's toggle; default ValueKey('kit-state-details') (kept, TEST-5)
  });

  /// NEW (K2 §2.2). A capability the page needs is missing (map whenMissing
  /// explains / offers-enable). Usually built by KitCapabilityExplainer.state,
  /// which fills title, why and enable from the registry by [capability].
  const KitStateView.missing({
    super.key,
    required String this.capability, // "voice.model": for tests and the explainer
    required this.title,              // "Voice needs a model"
    required String why,              // one sentence: shown as the body
    KitAction? enable,                // offers-enable: "Download voice model" → primary
    List<String> cost = const [],     // shown as KitNotice.cost above the action (KIT-37)
    this.prerequisite = false,        // "finish X first": enable is then required (STATE-13, asserted)
    this.icon = AppIconography.locked,
    this.size = KitStateSize.inline,
    this.titleKey,
    this.bodyKey,
    this.enableKey,
  });

  /// NEW (K2 §2.2, C26). An error with the kit's defaults.
  const KitStateView.error({
    super.key,
    required this.title,              // "Couldn't load conversations"
    this.body,                        // what to do, ≤ 2 sentences (agentErrorWords at the call site)
    this.error,                       // classified by KitErrorKind.of; never shown raw
    this.errorKind,                   // overrides the classifier
    this.details,                     // raw technical text: the fold, Copy details and Report (all redacted)
    this.detailNotes = const [],
    this.detailValues = const [],
    this.retry,                       // "Try again" → primary
    this.switchServer,                // network only → secondary
    this.reportSource,                // "saved-permissions"
    this.icon = AppIconography.error,
    this.size = KitStateSize.page,
    this.since,
    this.onSlow = const [],
    this.titleKey,
    this.bodyKey,
    this.copyDetailsKey,
    this.reportKey,
    this.detailsKey,
  });

  // NEW fields
  final List<KitTechnicalValue> detailValues;
  final DateTime? since;
  final List<KitAction> onSlow;
  final String? capability;
  final bool prerequisite;
  final Object? error;
  final KitErrorKind? errorKind;
  final KitAction? retry;
  final KitAction? switchServer;
  final String? reportSource;
  final Key? detailsKey, enableKey, copyDetailsKey, reportKey;
}
```

Notes:

- **The default constructor keeps today's behaviour.** It does not add the error defaults to existing `tone: failure` callers. That would change 23+ call sites' actions and goldens inside a kit change, against the kit-change finish line ("every call site still compiles with unchanged behaviour"; KIT-43). Wave-2 units move error states to `.error`. This amends K2 §2.2's "automatically" to "through `KitStateView.error`".
- **`onSlow` is a list of at most 2** (asserted). K2 has `KitAction? onSlow`; C26 names three possible ways out ("Try again / Restart / Leave it running"). "Leave it running" is not a kit button. It is what happens when the person does nothing, and a caller that can move the wait to the background passes it as a real action. COPY-7 makes the word "Try again", never "Retry".
- **`.missing` maps onto the fixed slots:**
  - `why` → body;
  - `enable` → primary;
  - `cost` → `KitNotice.cost` in `content`;
  - tone neutral.

  It does not look anything up. The registry lives in KitCapabilityExplainer, which depends on this part (C25), so this part must not import it. This is the dependency-safe reading of C20's "KitStateView.missing looks it up by capability id".
- **The icon slot is an icon tile** (LOOK-23: a state icon sits in an icon tile, not a tonal circle). `iconChild` and `illustration` are unchanged.
- **`KitStateView.calm` (vision 2026-10-20, "Both, toned down").** The calm empty or offline state: no icon tile and no drawing, one big calm line (`title`, set by `KitStatusAnswer` at `KitAnswerScale.calm`, 28/34; `compact`, 24/30, inline), one plain next step (`body`), the main action at the bottom.

  ```dart
  const KitStateView.calm({
    Key? key,
    required String title,
    String? body,
    KitAction? primary,                 // page only: solid mint, full width, at the bottom
    List<KitAction> tertiary = const [], // at most two quiet text actions, centred
    Widget? footer,                     // one quiet line after the actions
    Widget? header,                     // page: the screen's notes, top of the same scroll
    KitStateSize size = KitStateSize.page,
    Key? titleKey, Key? bodyKey,
    DateTime? countdownTo,              // body counts down to something really scheduled
    String Function(Duration left)? countdownLine,
    IconData icon = AppIconography.info, // names the state for its arrival; not drawn
  });
  ```

  Page: fills the height it is given; the words sit in the room between `header` and the actions, 10 % of that height above its middle (the optical centre); the actions end the page above the dock and the gesture bar (`KitBottomInset`). Without actions or header the words are centred. Under unbounded height (a list) it stacks. Inline: centred words with the tertiary actions under them. Never amber, never red. Used by Conversations, Files, Projects, Scheduled prompts, Search (`KitSearchNoMatch(calm: true)`) and the AI Team home.

## States

| State | How it is built | What the kit adds |
|---|---|---|
| loading (page) | default, `tone: progress`, `progress: KitProgress.waiting` | escalation after 8 s when `since` is set |
| working with progress | default, `tone: progress`, `progress: known` or `staged` | escalation when `since` is set (the time of the last progress event) |
| empty | default, neutral, `primary` = first step (STATE-2) | none |
| error | `.error` | defaults per kind (below); the fold |
| missing, explains | `.missing` without `enable` | none; body says which host can (via the explainer) |
| missing, offers-enable | `.missing` with `enable` | the cost line above the action |
| prerequisite | `.missing(prerequisite: true, enable: …)` | asserts `enable != null` (STATE-13, G37) |
| slow (escalated) | any with `since` | the body is replaced by KitSince's words ("Still waiting after 8 s"); `onSlow` shows as tertiary; one announcement |
| finished | `tone` from progress to ok | `KitHaptics.done` once (unchanged) |
| disabled | an action with `onPressed: null` | its `disabledReason` line (kit-KitAction-v2); the state itself is never disabled |

**Error defaults** (STATE-3, C26):

- **network** (`KitErrorKind.network`):
  - primary: `retry` ("Try again");
  - secondary: `switchServer` ("Switch server");
  - tertiary: "Copy details" when `details` is set.

  There is no Report: the fix comes first.
- **other:**
  - primary: `retry` when given;
  - tertiary: "Copy details" when `details` is set, and "Report a problem" when `KitReportHook.available`.

  Report is one tap here; the handler's preview makes two (P8.3).
- **Copy details** uses `KitCopy.copy(context, KitReportHook.redact(details))` and announces "Copied". Its words and Report's are KitNotice v2's keys `kitCopyDetails` and `kitReportProblem` (one key per action, COPY-18).
- **The label is "Report a problem"** (STATE-3, SEC-11; K2 §2.2). C26's "Report this" is superseded by STATE-3 (STANDARDS ranks above the cut review).

KIT-12 doc comment: "States: loading, working, empty, error, missing, slow, finished (+ disabled actions)".

## Tokens

- **ThemeRoles:**
  - icon tile `surface3`, glyph per the shared tone map (`accent` working, `success` ok, `text1` failure and warning, `text2` neutral; LOOK-4/5/6);
  - title `text1`;
  - body `text2`;
  - `surface1` behind the fold (the fold's own tokens).
- **KitText:** `title` (page title), `headline` (inline title), `body` (page body), `secondary` (inline body).
- **KitTokens:**
  - `markSize` (44, the page icon tile), `markRadius` (12), `iconTileSize` (30, inline), `iconTileRadius` (9), `markIconSize` (22), `smallIconSize` (20, inline glyph);
  - `space2`, `space3`, `space4`, `space5`, `space6` (replacing the literals 8/12/16/20/24/32 in `kit_state_view.dart`);
  - `gutter`.
- **New tokens (pre-wave, `_new-tokens.md`):**
  - `KitLayout.stateMaxWidth` = 440 (the page state's width, today the literal `maxWidth: 440`; LAY-2);
  - `KitTokens.stateVerticalPadding` = 32 (the page's top and bottom, today a literal);
  - the shared tone map `KitTokens.toneFor` / `toneColor` (README.md decision D12).

## Adaptive

- **compact:** page size centred vertically, content start-aligned on the 16 dp gutter, capped at `stateMaxWidth`. Actions stacked full width (KitActionBlock).
- **medium:** the same, with the content block centred horizontally at `stateMaxWidth`. KitActionBlock goes to one end-aligned row (LAY-13, handled by that part).
- **expanded / large:** a page state fills the pane it is in (in `KitScreen.twoPane` the detail pane, not the window), centred in that pane. The illustration keeps its width. Inline states follow their host.
- **Short windows** (under 480 dp tall, landscape phones): the page state keeps the compact stacked layout and scrolls (LAY-3). The illustration drops to the inline width.
- **Pointer and keyboard:**
  - Tab order: title (focusable for reading only when it is the route's first node), actions in block order, the fold toggle, then the footer;
  - Enter and Space activate;
  - with a fine pointer, the fold toggle and the copy control show hover highlights;
  - no shortcuts.

## Accessibility

- One container. It is a live region when `liveRegion` (default true). A new state (icon, tone or illustration change) is announced once. The escalation is announced once ("Still waiting after 8 s"). A caption tick inside progress is not announced.
- Every action is 48 dp. The fold toggle is a 48 dp row with `expanded` semantics (from KitDetailsFold).
- At 200 % text:
  - the title wraps and is never truncated;
  - the body wraps;
  - actions wrap to 2 lines;
  - the page scrolls (it is already a scroll view);
  - the illustration never pushes the primary off a 412×915 screen at 1.0 (LOOK-37). At 2.0 the content scrolls.
- `.missing`: the body says where the capability is available (STATE-12), so a screen reader gets the same words.

## RTL

- The icon and title sit at the start (`AlignmentDirectional.centerStart`, unchanged). Padding is directional (the existing `EdgeInsets.fromLTRB(16, 16, 16, 8)` becomes `EdgeInsetsDirectional`, G7).
- Technical text is only inside the fold, which isolates LTR (`KitTechnicalValue`, `KitBidi`).
- Placeholders in the title and body are wrapped by the caller (COPY-30).

## Motion and haptics

- **Unchanged:** `KitEntrance` on a new state (`KitMotion.standard`), with the fold on `KitReveal`.
- **The escalation swap:** the body cross-fades on `KitMotion.quick`, and the `onSlow` actions unfold with `KitReveal`.
- **Reduced motion:** everything is instant. The illustration shows its finished frame, and ambient only while waiting (MOT-6).
- **Haptics:** `KitHaptics.done` once on progress → ok (unchanged). Nothing on error (MOT-11).

## Data safety and honest state

- **Escalation:** the timer lives in `KitSince`. The part never runs its own `Timer`, so tests use one fake clock (C12). An escalated state keeps its title and never claims failure: "Still waiting" is not "Failed".
- **Redaction:** the fold, Copy details and Report all see redacted text (SEC-2, SEC-4). `error` is never rendered or copied raw.
- **No dead end:** Report shows only when a handler exists, and a prerequisite without an action is a debug assert (STATE-13).
- **Titles:** a title never contradicts progress (DS §3). The kit cannot check wording, so this is a reviewer rule.

## Depends on

- **Wave-1 units (C25):**
  - kit-KitDetailsFold (the details slot);
  - kit-KitNotice-v2 (`KitNotice.cost`, `KitReportHook`, `KitErrorKind`);
  - kit-KitIconButton-v2 (the copy control inside the fold);
  - kit-KitSince (escalation).
- **Existing parts:** `KitActionBlock`, `KitButton`, `KitProgressView`, `KitIllustration`, `KitEntrance`, `KitReveal`, `KitHaptics`, `KitTechnicalValue`.
- **Pre-wave:** `KitCopy.copy`, `KitMotion.escalateAfter`.

Depended on by: KitCapabilityExplainer (C25), and shared-system-1's Product* wrappers.

## Tests required

In `test/kit/kit_state_view_test.dart`, using KitSince's fake clock:

1. **Escalation:** with `since` 7 s ago no change. At 8 s the body reads "Still waiting after 8 s", the `onSlow` actions appear, and the semantics announcement fires exactly once. Without `since`, nothing escalates.
2. `onSlow` with 3 actions throws an `AssertionError`.
3. `.error` with `TimeoutException`: primary "Try again", secondary "Switch server", tertiary "Copy details", no "Report a problem".
4. `.error` with `FormatException` and a registered handler: "Copy details" and "Report a problem" are shown. One tap on Report calls the handler once, with redacted details (a fake provider key and bearer token are absent) and `source` set.
5. `.error` with no handler: no Report. Copy details copies the redacted text, announces "Copied", and shows no SnackBar.
6. `.missing` without `enable`: no primary, and the body is `why`. With `enable`: the primary is the enable action. With `cost`: the cost line renders above it. `prerequisite: true` without `enable` asserts (G37).
7. The default constructor with `tone: failure` renders exactly the given actions (no added defaults), which protects existing callers.
8. The details fold: `details`, `detailNotes`, `detailsChild` and `detailValues` appear when opened. The key `kit-state-details` toggles it, and `kit-state-details-text` still finds the raw text (TEST-5).
9. Progress → ok calls `KitHaptics.done` once, and not with Vibration off.
10. Under reduced motion it settles after one `pump()` (G8).

## Galleries required

`test/goldens/kit/kit_state_view_golden_test.dart`, DPR 3, Android platform.

- **Declared states × dark and light at 412×915, page size:**
  - loading;
  - working (staged progress);
  - empty;
  - error-network;
  - error-other;
  - missing-explains;
  - missing-enable (with cost);
  - slow (escalated).
- **Calm (vision 2026-10-20):** calm page (dark, light), calm inline, calm at 2.0 text.
- **Inline size:** error-other and missing-enable.
- **Default (empty) page** × dark and light at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **Text 2.0 and Arabic** (error-other, missing-enable) at 412×915 and 1280×800.
- **Names:** `kit_state_view_<state>[_inline]…png`. About 44 PNGs, under the 60 cap.

## Non-goals

- No edits to `product_states.dart` (shared-system-1) or any screen.
- No capability registry (KitCapabilityExplainer).
- No classification of error words. `agentErrorWords` stays at the call site (COPY-14).
- No report UI. The handler's preview is the app's (coord-main).

## Open questions

None. The two readings chosen above follow from KIT-43 and from the C25 dependency direction: error defaults are opt-in through `.error`, and `.missing` performs no lookup. The coordinator should reflect both in K2 §2.2.
