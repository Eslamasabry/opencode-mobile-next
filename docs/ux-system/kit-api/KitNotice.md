# KitNotice v2 (with KitReportHook) — API freeze (wave 0)

> **Copy (SEC-13, coordinator 2026-09-27):** copies of technical values, logs and details use `KitCopy.copy(context, text)` (redacted). A part that also copies the person's own content passes `redact: false` for that content.

Unit: `kit-KitNotice-v2` (wave 1, tier 1a, kind `kit-change`). Spec: kit-v2.md §2.4, design standard §3; retitled by cut review C26 ("cost line; absorbs NudgeCard, _Notice, _FileStatusNotice, first-run tips; turn-on notice"; also defines `KitReportHook`), with file moves from C23. Rules: KIT-37, STATE-3, STATE-20, SEC-2, SEC-11, LOOK-4, LOOK-5, LOOK-24, KIT-43, COPY-7.

## Purpose

A message about one part of a form, list or section: a verdict, a failed save, a condition, a one-time tip, a turn-on offer, or the cost of an install. It has no card and no filled block, and it is one live region. v2 adds:

- the cost line (`KitNotice.cost`);
- the one-sentence offer (`KitNotice.offer`, which absorbs `NudgeCard`);
- an error variant with the kit's error defaults (`KitNotice.error`);
- the app-wide "Report a problem" hook (`KitReportHook`), which `KitStateView` v2 shares.

## Replaces

- **Map elements, 20 on 17 pages (kit-v2.json assignment → `KitNotice`):**
  - `app-diagnostics` (diagnostics-intro);
  - `attention-overview` (attention-overview-disclaimer);
  - `catalog` (basic-catalog-banner);
  - `chat` (chat-draft-error, chat-first-reply-notify, chat-staged-revert-review);
  - `connection-help` (connection-help-verdict);
  - `credential-management-sheet` (unknown-notice);
  - `embedded-session-inventory-footer` (embedded-session-inventory-footer-reload);
  - `integrations` (legacy-notice);
  - `legacy-drafts` (legacy-intro);
  - `local-agent-page` (battery-warning);
  - `model-picker-sheet` (basic-catalog-notice, picker-unloaded-providers);
  - `notifications-settings` (notifications-footer);
  - `server-capabilities` (capabilities-intro);
  - `system` (undeliverable-snackbar);
  - `terminal-surface` (terminal-surface-error);
  - `termux-setup` (explanation);
  - `workspace` (workspace-nudge-pin-got-it).
- **Absorbed widgets (§2.4, §2.14):**
  - `NudgeCard` (`lib/ui/widgets/nudge_card.dart`, 51 lines, 2 call sites: `workspace_screen.dart:859`, `chat/nudge_slot.dart:116`);
  - the model picker's `_Notice`;
  - `_FileStatusNotice`;
  - the first-run tips;
  - discovery cards ("{server} also runs an AI team. Turn it on?", which becomes `.offer`).

  `nudge_card.dart` joins this unit's write set (C23) and becomes a thin forwarding wrapper over `KitNotice.offer`. `_Notice` and `_FileStatusNotice` are private screen classes, adopted by their wave-2 units.
- **Retired API:** `KitNotice.card` (VL branch `kit_notice.dart:40-52`). STANDARDS LOOK-24 (owner-approved slice P4.2a; Appendix A #74) makes the needs-you look belong only to `KitRequestCard` (in the conversation) and `KitNeedsYou` (the pointing row). This settles the C02 freeze item "KitNotice.card versus the KitRequestCard attention styling": `KitNotice.card` keeps compiling (R11, KIT-43), its doc says `/// Retired by kit-KitNotice-v2: use KitRequestCard in the conversation or KitNeedsYou.row in lists (LOOK-24)`, it gains a G2 ratchet pattern `KitNotice.card(`, and kit-hygiene (2d) deletes it. Its only use today is the integrator-owned `test/goldens/kit/kit_foundation_golden_test.dart:129`.

## File

- `lib/ui/kit/kit_notice.dart` (`KitNotice`, and `KitReportHook`, `KitReport`, `KitErrorKind` in the same file: the unit's write set has no second kit file, and kit-KitStateView-v2 already imports this one)
- `lib/ui/widgets/nudge_card.dart`, forwarding wrapper (C23)
- Tests: `test/kit/kit_notice_test.dart` (a `KitReportHook` group included)
- Gallery: `test/goldens/kit/kit_notice_golden_test.dart`

## Public API

```dart
class KitNotice extends StatelessWidget {
  // UNCHANGED signature, plus two optional keys/labels
  const KitNotice({
    super.key,
    required this.message,
    this.title,
    this.tone = AppStatusTone.neutral,
    this.icon,
    this.notes = const [],
    this.actions = const [],      // small tonal buttons, at most 2 shown
    this.onDismiss,               // only when dismissing changes nothing real
    this.messageKey,
    this.liveRegion = true,
    this.dismissKey,              // NEW (default ValueKey('kit-notice-dismiss'), as today)
    this.dismissLabel,            // NEW (default: the existing kitSheetDismiss "Dismiss"; today workspaceDismissNotice)
  });

  /// NEW. The cost of an install or turn-on, stated before its primary
  /// (KIT-37): "About 208 MB · about 4 min · uses battery while it installs".
  /// Items come from the component registry or the caller, each already in
  /// words; the kit joins them with " · ". Not a live region: it is static
  /// text beside a button.
  const KitNotice.cost(
    List<String> items, {
    super.key,
    this.title,                   // optional, e.g. "Before you start"
    this.messageKey,
  });

  /// NEW. One sentence, one action, one close: a one-time tip or a turn-on
  /// offer (NudgeCard; "{server} also runs an AI team. Turn it on?"). The
  /// sentence wraps whole; when it needs more than one line, the action and
  /// the close share the row under it (NudgeCard's controlsTogether rule).
  const KitNotice.offer({
    super.key,
    required this.message,
    required KitAction action,     // one tertiary action
    required VoidCallback this.onDismiss, // "Not now" / close
    this.icon,                     // default AppIconography.idea
    this.messageKey,
    this.dismissKey,
    this.dismissLabel,
  });

  /// NEW. A failure about one part (STATE-20: "a sheet that fetches: an
  /// inline KitNotice error with Try again"). The kit adds its error
  /// defaults (see States): Copy details (always, when [details] is given),
  /// Report a problem (non-network, when KitReportHook.handler is set),
  /// and the network fix (Switch server).
  const KitNotice.error({
    super.key,
    required this.message,         // what failed, in words (agentErrorWords at the call site)
    this.title,
    this.error,                    // classified by KitErrorKind.of; never shown raw
    this.errorKind,                // overrides the classifier
    this.details,                  // raw technical text: copied and reported, redacted
    this.retry,                    // "Try again"
    this.switchServer,             // network errors only
    this.reportSource,             // "files-viewer": where the report says it came from
    this.messageKey,
    this.copyDetailsKey,
    this.reportKey,
  });

  // … existing fields, plus:
  final List<String>? costItems;   // NEW (cost)
  final Object? error;             // NEW
  final KitErrorKind? errorKind;   // NEW
  final String? details;           // NEW
  final KitAction? retry;          // NEW
  final KitAction? switchServer;   // NEW
  final String? reportSource;      // NEW
  final Key? dismissKey, copyDetailsKey, reportKey; // NEW
  final String? dismissLabel;      // NEW

  // RETIRED (kept compiling until kit-hygiene): const KitNotice.card({...})
}
```

```dart
// lib/ui/kit/kit_notice.dart, continued (NEW classes)

/// Network or not: decides between "Report a problem" and the network fix.
enum KitErrorKind {
  network,  // unreachable, timed out, refused, reset, DNS
  other;

  /// Pure Dart (no dart:io import, so web builds keep compiling):
  /// TimeoutException → network; otherwise the runtime type name is matched
  /// against SocketException, HandshakeException, HttpException,
  /// ClientException, WebSocketException, WebSocketChannelException →
  /// network; anything else → other. Null → other.
  static KitErrorKind of(Object? error);
}

/// What "Report a problem" sends to the app's report flow. Built by the kit
/// from a state view or notice; [details] is already redacted.
class KitReport {
  const KitReport({required this.title, this.details, this.source, this.errorType});
  final String title;        // the state's title: "Couldn't load files"
  final String? details;     // redacted technical text
  final String? source;      // the page or part id: "files-viewer"
  final String? errorType;   // error.runtimeType.toString(), no message text
}

typedef KitReportHandler = Future<void> Function(BuildContext context, KitReport report);

/// The one app-wide seam for "Report a problem" (C26). The kit imports no
/// feedback code; the app sets [handler] at start-up (coord-main, C32:
/// a handler over lib/feedback/bug_report.dart that previews exactly what
/// is sent and offers Copy and Share first, SEC-11).
abstract final class KitReportHook {
  static KitReportHandler? handler;
  static bool get available => handler != null;

  /// Calls [handler] with [report]; a no-op when none is set.
  static Future<void> report(BuildContext context, KitReport report);

  /// The one redaction the kit applies before reporting details: the same
  /// redactor the pre-wave KitCopy.copy applies (KitIconButton.md,
  /// KitAction.md: "KitCopy.copy, which applies the redactor") and
  /// KitDetailsFold refuses values by (SEC-2, SEC-4, G12).
  static String redact(String text);
}
```

- **The `NudgeCard` wrapper** keeps its public constructor and its keys (`nudge-<id>`, `nudge-<id>-action`, `nudge-<id>-dismiss`; TEST-5). It forwards to `KitNotice.offer(key: ValueKey('nudge-${id.wire}'), message:, action: KitAction(key: …-action, …), onDismiss:, dismissKey: …-dismiss, dismissLabel: dismissTooltip, icon:)`. Its doc gets `/// Retired by kit-KitNotice-v2: use KitNotice.offer`. The look moves from a status line to the notice. The shared goldens that change (workspace, chat nudge) are listed under `sharedTestsBroken` (R07, R08).
- **`KitReportHook.redact`** is a seam only. It forwards to the pre-wave `KitRedact.text` (`_new-tokens.md`; patterns frozen in KitDetailsFold.md), the same function `KitCopy.copy` and the details fold use, so copy, report and the fold redact alike. Copy details itself needs no extra call, because `KitCopy.copy` already redacts; the explicit `redact(details)` in the calls below is harmless and keeps the report path identical.
- **Kit copy** (ARB, `kit` prefix, en and ar): `kitCopyDetails` "Copy details", `kitReportProblem` "Report a problem" (STATE-3, SEC-11), and the offer's default dismiss, the existing `kitSheetDismiss`. KitStateView v2 reuses the first two (COPY-18). `Try again` and `Switch server` are the caller's `KitAction`s.

## States

| Constructor | States | Actions shown (small tonal buttons, at most 2; the offer's one stays a tertiary link in its line) |
|---|---|---|
| default | neutral, progress, ok, attention*, failure | the caller's `actions` (first 2) and an optional dismiss |
| `.cost` | static | none |
| `.offer` | open; folded is the caller's (AUTO-18: after "Not now" the caller shows a `KitCapabilityExplainer.row` or nothing) | the one `action` and the dismiss |
| `.error`, network | error | Try again (`retry`), Switch server (`switchServer`); plus a trailing icon-only Copy details when `details` is set |
| `.error`, other | error | Try again (`retry`), Report a problem (only when `KitReportHook.available`); plus a trailing icon-only Copy details when `details` is set |

- **Copy details** is a trailing icon control: the existing v1 `KitIconButton` (label "Copy details"; it passes `label:` because kit-KitIconButton-v2 is built in the same tier, and it switches to `tooltip:` when both are merged, since KitIconButton.md adds a G2 ratchet on `label:`) calling the pre-wave `KitCopy.copy(context, KitReportHook.redact(details))`. That keeps the notice's two-word-action limit (DS §3, LAY-13) while "Copy details" is always offered (C26). When kit-KitIconButton-v2 lands, kit-hygiene may swap it to `KitIconButton.copy`. No dependency edge is added.
- **Report a problem** calls `KitReportHook.report(context, KitReport(title: title ?? message, details: redact(details), source: reportSource, errorType: error?.runtimeType.toString()))`. That is one tap in the notice, and the handler's preview is the second (C41: "report within 2 taps").
- **\* attention:** LOOK-4 reserves the attention roles for "needs you". A plain notice with `tone: attention` is drawn with the warning glyph in `text1`, never in amber. It is kept for compatibility (KIT-43), and its doc points needs-you uses to KitNeedsYou and KitRequestCard.
- **failure:** the error glyph in `text1`, not `danger` (LOOK-5, interim B2).
- **loading, empty and disabled:** not applicable. A notice is itself a state message, and a disabled action inside it follows `KitAction.disabledReason` (kit-KitAction-v2).

KIT-12 doc comment: "States: neutral, working, ok, warning, error, cost, offer".

## Tokens

- **ThemeRoles:**
  - icon: `text2` (neutral), `accent` (progress), `success` (ok), `text1` (warning and failure, LOOK-4/5);
  - title: `text1`;
  - message: `text1` without a title, `text2` under one;
  - notes: `text2`;
  - cost line: `text2`.
- **KitText:** `body` (message alone), `rowTitle` weight for the title (`headline` is only for card titles), `secondary` (message under a title, notes, cost), `button` via `KitButton`.
- **KitTokens:** `smallIconSize` (20; grows by `iconSize(context, 20)` within `maxIconScale`), `space1–space3`, `minTarget` (dismiss and copy).
- **Tone map:** the pre-wave `KitTokens.toneFor` / `toneColor(AppStatusTone)` (`_new-tokens.md`; README.md decision D12), which already maps attention and failure to `text1`. The existing literal paddings (`EdgeInsets.symmetric(vertical: 4)`, `SizedBox(width: 12)`) move to `space1` and `space3`.

## Adaptive

- **All classes:** on the host's rails, full width of its section, wrapping. From medium up, the actions may sit on the message's last line when they fit (measured as in `KitStatusLine._stacks`). Otherwise they sit under it, start-aligned.
- **expanded / large:** capped by the host (`readingWidth`).
- **Pointer and keyboard:**
  - actions, Copy details and dismiss are in Tab order after the message;
  - Enter and Space activate;
  - with a fine pointer, hover shows the Copy details and dismiss tooltips (a label the semantics already carry, LAY-11);
  - Esc does nothing (not modal).

## Accessibility

- **Live region:** one per notice (except `.cost`), announced once per change of tone or message (A11Y-3).
- **Labels:** every icon-only control is labelled ("Copy details", "Dismiss"). All targets are 48 dp, with 8 dp between the copy and dismiss targets (LAY-9).
- **200 % text:** the message, notes and cost wrap and are never truncated. The actions wrap to their own line.
- **"Copied":** Copy details announces "Copied" once (`KitCopy`, KIT-23).

## RTL

- The icon sits at the start, and dismiss and copy at the end. Padding is directional (`EdgeInsetsDirectional`, G7).
- Cost items with sizes and units are wrapped by the caller in `KitBidi.ltr` (COPY-30). The kit joins them with " · ".
- `details` is never shown in the notice itself, so it needs no isolation there. The report preview is the handler's.

## Motion and haptics

- **Entrance:** `KitEntrance` (fade and rise on `KitMotion.standard`) when the notice appears or its tone changes. Folding away is the host's `KitReveal`.
- **Reduced motion:** instant.
- **Haptics:** none (MOT-11: nothing on failures).

## Data safety and honest state

- Copy details and Report both pass `details` through `KitReportHook.redact` first (SEC-2, SEC-4). Provider keys and bearer tokens never reach the clipboard or the report.
- `KitReport` never carries `error.toString()` (it may hold secrets). It carries only the redacted `details` the caller chose and the error's type name.
- Report appears only when a handler is registered, so it is never a dead button (STATE-13).
- The cost line is required before any install or turn-on primary (KIT-37). This is a reviewer rule. The part makes it one line to write.

## Depends on

- **None in wave 1** (tier 1a; C25 lists no edges).
- **Pre-wave seams:** `KitCopy.copy` (KIT-23) and `KitBidi` (COPY-30).
- **Redaction:** the pre-wave `KitRedact.text` (`_new-tokens.md`).
- **Uses existing v1 parts:** `KitButton`, `KitAction`, `KitIconButton`, `KitEntrance`.

Depended on by: KitStateView v2 (error defaults, `KitReportHook`, `KitErrorKind`), KitChecklist (`.cost`), KitCapabilityExplainer (`.offer`).

**App wiring, not in this unit:** coord-main sets `KitReportHook.handler` (C26 and C32 corrections). C26 names `openReportProblem`, which does not exist; the current entry point is `openBugReport` in `lib/feedback/bug_report.dart:88`, and SEC-11 requires a preview, with Copy and Share first.

## Tests required

In `test/kit/kit_notice_test.dart`:

1. `KitNotice.cost(['About 208 MB', 'about 4 min'])` renders "About 208 MB · about 4 min" and is not a live region.
2. `KitNotice.offer`: one action and one dismiss. The keys are passed through. The action and dismiss share the row when the message wraps at 2.0 text.
3. `NudgeCard(...)` still finds by `nudge-<id>`, `-action` and `-dismiss`. Tapping them calls `onAction` and `onDismiss` once. This covers the existing `test/nudge_moments_test.dart` expectations by behaviour.
4. `.error` network (`TimeoutException`, and a fake class named `SocketException`): shows Try again and Switch server, no Report. Copy details is present when `details` is given.
5. `.error` other with a handler registered: Report a problem is shown. One tap calls the handler once with a `KitReport` whose `details` is redacted (a fake `sk-…` key and a `Bearer …` token are absent) and whose `errorType` is set. With no handler, Report is absent.
6. Copy details writes the redacted text through `KitCopy`, announces "Copied" once, and shows no SnackBar.
7. `KitErrorKind.of` over `TimeoutException`, named IO-type fakes, `FormatException` and null.
8. `tone: attention` on a plain notice paints `text1` (LOOK-4), and `failure` paints `text1` (LOOK-5).
9. The live region announces once per tone or message change (semantics event count).
10. Under reduced motion it settles after one `pump()` (G8).

## Galleries required

`test/goldens/kit/kit_notice_golden_test.dart`, DPR 3, Android platform, on a `surface1` section.

- **Declared states × dark and light at 412×915:**
  - neutral with title and notes;
  - ok;
  - warning;
  - error-network;
  - error-other (handler set);
  - cost;
  - offer;
  - offer at 2 lines.
- **Default** × dark and light at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **Text 2.0 and Arabic** (default, error-other, offer) at 412×915 and 1280×800.
- **Names:** `kit_notice_<state>…png`. Keep the set under 60 PNGs.

## Non-goals

- No card look: the needs-you card is KitRequestCard's (LOOK-24).
- No persistence of "Not now" or "shown once" (COPY-20). That memory belongs to the caller (`NudgeController` and the capability offer memory).
- No redactor implementation.
- No screen adoption.

## Open questions

None. The redaction entry is `KitRedact.text(String)` in `lib/ui/kit/kit_redact.dart`, a pre-wave seam (`_new-tokens.md`) with the display-safe patterns frozen in KitDetailsFold.md. KitCopy, KitDetailsFold and `KitReportHook.redact` share it. Until it exists, this is a PROC-32 dependency, not a local substitute (R13).
