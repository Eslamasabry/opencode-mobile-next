# KitRequestSheet — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitRequestSheet` (wave 1, tier 1e, kind `kit-part`; C25: after kit-KitRequestCard-v2, kit-KitRowParts-v2, kit-KitDiffView). Spec: kit-v2.md §2.1 (`showKitRequestSheet`), §1.1 (honest state: "closes through RequestRoutes and leaves a KitReceipt where it was opened"), §2.6, §4.7, §8.2; cut review C15 ("showKitRequestSheet with RequestRoutes; the permission, question, form and gate variants; 'Always allow' as a risk KitSwitchRow"), C41 (absorbs P4.1a's sheet half), C45 (slice-P4.1c: the gate sheet becomes "the Details variant only"). Rules: AUTO-12, AUTO-17, KIT-11, KIT-16, KIT-17, KIT-30, KIT-33, SEC-9, DATA-1, DATA-2, DATA-3, DATA-11, STATE-10, COPY-2, COPY-15, LOOK-24, MOT-11, A11Y-3, LAY-10.

## Purpose

The one details sheet for a request that the card cannot hold in place: the full command, the change as a diff, every option, a long form, a gate's plan, a note sent with the answer, and "Always allow" as a risk switch. Answering in it sends through the card's answers and closes it. When the request is answered on another device or expires, the sheet closes itself through `RequestRoutes`, and the card it came from keeps the receipt ("Answered on the laptop").

## Replaces

- **The four request sheets, merged into one** (map proposals all `fix`):
  - **`showPermissionSheet`** (`lib/ui/screens/chat/permission_sheet.dart:41`, page permission-sheet). Its "Always allow" dialog (permission-sheet-always-dialog) becomes the risk switch here.
    - G1: {AlertDialog( 1, showDialog( 1, showModalBottomSheet( 1};
    - G16: {ActionChip 1, AlertDialog 1, AnimatedSize 1, Container 2, Icon 8, IconButton 2, Material 2, SelectableText 1, SingleChildScrollView 4, Text 17, TextButton 1, TextField 1}.
  - **`showQuestionSheet`** (`lib/ui/screens/activity_screen.dart:973`, page question-sheet). Its dismiss dialog (question-sheet-dismiss-dialog, `merge-into:confirm-sheet`) is owned by screen-shell-1 or slice-P3.11a (C38 correction), not the chat chain.
  - **`showGateSheet`** (`lib/ui/screens/team/gate_sheet.dart:78`, page gate-sheet). Its `_Receipt` (:1076) is deleted by slice-P4.1c (C45). Its gate-sheet-confirm-sheet (`keep`) becomes an in-place confirmation (KIT-16).
    - G1: {showConfirmSheet( 1, showModalBottomSheet( 1};
    - G16: {ActionChip 1, ExpansionTile 1, Icon 6, InkWell 1, SingleChildScrollView 1, Text 22, TextField 1, Theme 1}.
  - **`presentForm`** (`lib/ui/widgets/form_renderer.dart:33`, page form-sheet). Its field renderer stays in shared-chat-3 and arranges kit fields inside this sheet's `body`.
    - G1: {showConfirmSheet( 1, showModalBottomSheet( 1};
    - G16 in total: Text 26, TextField 5, RadioListTile 2, and others.
- **Where these pages' elements are assigned in kit-v2.json today.** No `assignment` names KitRequestSheet, which is new with C15. This part is where they meet:
  - their frames → `KitSheet` (permission-sheet, question-sheet#question-sheet-frame, form-sheet#form-sheet and #form-sheet-submit);
  - their confirmations → `KitConfirmSheet` (permission-sheet-always-dialog, question-sheet-dismiss-dialog, gate-sheet-confirm-sheet);
  - their options → `KitChoiceList` (question-sheet#question-sheet-option and #question-sheet-send, gate-sheet#gate-options);
  - their technical lines → `KitDetailsFold` (gate-sheet#gate-details and #gate-kicker);
  - their typed notes → `KitField` (permission-sheet reject-message, question-sheet custom answer).
- **Who adopts it** (not this unit):
  - chain link chat-5 (permission_sheet, form_flow; P4.1b "Details = showKitRequestSheet");
  - screen-shell-1 or slice-P3.11a (the question sheet in activity_screen);
  - slice-P4.1c (the gate sheet);
  - shared-chat-3 (form_renderer's frame).

## File

- **Part:** `lib/ui/kit/kit_request_sheet.dart`.
- **Tests:** `test/kit/kit_request_sheet_test.dart`.
- **Gallery:** `test/goldens/kit/kit_request_sheet_golden_test.dart`.
- **Imports:** `RequestRoutes` from `lib/ui/widgets/request_routes.dart`, unchanged, as `kit_sheet.dart` already does.

## Public API

```dart
/// How a request sheet closed. Returned by showKitRequestSheet.
enum KitRequestSheetOutcome {
  answered,           // the person answered here; the card now shows the receipt
  answeredElsewhere,  // RequestRoutes closed it (answered on another device, or expired)
  dismissed,          // back, swipe, Esc, Close, tap outside: the request still waits
}

/// "Always allow" as a risky switch (K2 §2.1, §2.6; KIT-30, SEC-9). Never
/// a primary. Turning it on first unfolds its scope in place; choosing a
/// duration answers the request with "always" and closes the sheet.
@immutable
class KitRequestAlwaysAllow {
  const KitRequestAlwaysAllow({
    required this.title,          // "Always allow flutter test"
    required this.scope,          // "In this conversation, on laptop"
    required this.onLabel,        // status-line words while a saved rule is on
    required this.onAllowAlways,  // ValueChanged<KitUntil>: sends the "always" answer
    this.until = const [KitUntil.conversation], // what the server supports (1–3)
    this.switchKey,
  });
  final String title;
  final String scope;
  final String onLabel;
  final ValueChanged<KitUntil> onAllowAlways;
  final List<KitUntil> until;
  final Key? switchKey;
}

/// A note sent with the answer: a reason with Reject (permission), notes
/// with Send back (gate). Kept in a draft (DATA-1).
@immutable
class KitRequestMessage {
  const KitRequestMessage({
    required this.fieldLabel,     // "Tell the agent why (optional)"
    required this.draft,          // target `request.<requestId>.note`
    this.helper,
    this.fieldKey,
  });
  final String fieldLabel;
  final KitDraft draft;
  final String? helper;
  final Key? fieldKey;
}

/// Opens the details of [card] (a KitRequestCard.ask). The header is the
/// card's: its icon on the attention tile, its title, and "{who}[ on
/// {server}] · waiting 4 min" as the subtitle. The pinned answers come
/// from `card.answers`. Returns how it closed.
Future<KitRequestSheetOutcome> showKitRequestSheet(
  BuildContext context, {
  required KitRequestCard card,
  required RequestRoutes routes,           // K2 left it implicit; required here
  String? fullText,                        // the whole ask: the full command (permission),
                                           //   the question (question/choice), the plan (gate),
                                           //   the task's message (reply)
  KitDiffView? change,                     // permission on an edit, or a gate's merge: the change
  KitRequestAlwaysAllow? alwaysAllow,      // permission only
  KitRequestMessage? message,              // permission (reject note), gate (send-back notes)
  WidgetBuilder? body,                     // form: the fields, arranged from kit parts
  KitAction? submit,                       // form: "Send answers", disabled with its reason until valid
  KitDraft? draft,                         // form: its typed answers (G48)
  ValueListenable<bool>? dirty,            // form input that cannot be a draft
  List<KitTechnicalValue> details = const [], // last, in one KitDetailsFold, collapsed
  Key? sheetKey,
});
```

**Variants.** The variant is chosen by `card.kind`, and each variant has fixed slots in this order in the body:

| Variant (kind) | Body, top to bottom | Pinned actions (from `card.answers`) |
|---|---|---|
| permission | `ifIgnored` line; `fullText` (the full command, path or pattern list) in a `KitCodeBlock`: mono, LTR, copyable, never truncated; `change` (a read-only `KitDiffView`); `alwaysAllow` (a `KitSwitchRow` with `risk`); `message`; `details` | `KitRequestDecide`: primary "Allow once" (allow), secondary "Reject" (reject, sending the note if one was typed) |
| question / choice | `ifIgnored`; `fullText` (the question, `body` role, wraps); every option (`KitChoiceList.single(sends: true)` for `KitRequestChoose`, with `other`; `KitChoiceList.multi` for `KitRequestChooseMany`); `details` | Choose: none (a tap sends). ChooseMany: primary Send, disabled with the reason "Choose at least one answer." |
| form | `ifIgnored`; `body` (the fields); `details` | `submit` |
| gate | `ifIgnored`; `fullText` (the plan); `change` (optional); `message` (notes); `details` | `KitRequestDecide`: primary "Approve", secondary "Send back" |
| reply | `ifIgnored`; `fullText` (the task's message); the reply `KitField` (the card's `KitRequestReply.draft`) | primary Send, disabled with "Type a reply first." when empty |

- **Answering here:**
  - the kit calls the card's own callback (`onAllow`, `onReject`, `onChosen`, `onSend`, `alwaysAllow.onAllowAlways`, or `submit.onPressed`) exactly once;
  - it fires `KitHaptics.send` once;
  - it closes the sheet with `answered`.
  - The receipt appears on the card, where the request lives. There is one receipt per request and no duplicate in a closing sheet.
- **A confirmation inside it** (the gate's merge confirm, the form's discard) goes through `showKitConfirm` from the body. It swaps in place and adds no route (KIT-16).
- **Kit copy** (ARB `kit` prefix, en and ar):
  - `kitRequestChooseOneReason` "Choose at least one answer.";
  - `kitRequestSendAnswers` "Send answers" (the default `submit` label a form may use);
  - reused from KitRequestCard.md: `kitRequestAllowOnce`, `kitRequestReject`, `kitRequestApprove`, `kitRequestSendBack`, `kitRequestSend`, `kitRequestReplyEmptyReason`, `kitRequestAge`;
  - reused from KitRowParts.md (`kitRiskTurnOn`, `kitRiskNotNow`, `kitUntil*`).

## States

The doc comment declares: `permission`, `permission-always` (the risk scope unfolded), `permission-change` (with the diff), `question`, `question-many` (Send disabled with its reason), `form` (submit disabled with its reason), `form-discard` (the question in place), `gate`, `gate-confirm` (the in-place confirmation), `reply`, `closed-elsewhere` (a behaviour, with no picture).

- **Loading.** None of its own. The sheet opens from a card whose request is already known. A `change` that is still being fetched is the `KitDiffView`'s own loading state (skeleton lines).
- **Empty.** `KitDiffView` "No changes" (inline `KitStateView`), when the edit is empty.
- **Error.** A diff that failed to load is the diff's inline `KitNotice` with Try again. An answer that fails is not shown here: the sheet has closed, and the card's receipt says "Not accepted" or "Not confirmed yet" (STATE-10).
- **Disabled.** Pinned answers with their reason: `KitRequestDecide.disabledReason`, ChooseMany's "Choose at least one answer.", the form's `submit.disabledReason`, and reply's "Type a reply first." (STATE-8).
- **Working.** None in the sheet: answering closes it at once. The card's `sending` phase is the working state.
- **Answered elsewhere.** The route is removed a frame after `routes` stops pending, and the future completes with `answeredElsewhere`. Nothing is sent, and the risk step, if open, is dropped.

## Tokens

All exist on `feat/visual-language-v1` unless flagged. The frame's tokens are KitSheet v2's.

- **ThemeRoles:**
  - `attention` (the header tile glyph and tint), through `showKitSheet(tone: KitSheetTone.attention)`. LOOK-24 allows it because this sheet is the card's own details;
  - `text1` (`fullText`), `text2` (`ifIgnored`);
  - `ground` (the code block and diff come from their parts);
  - `surface1`/`hairline` (the switch row's panel, via KitRow).

  No `accent` except the primary answer's fill. No `danger`: Reject is not destructive (LOOK-5).
- **KitText:** `body` (`fullText` for a question or plan), `secondary` (`ifIgnored`, the subtitle), `mono` (through `KitCodeBlock` and `KitTechnicalValue`).
- **KitTokens:** `space2`–`space5` (between the body slots), `sectionGap` (22, before `details`), `rail`.
- **KitLayout (existing):** as `showKitSheet`. The height rule is `KitSheetHeight.full` when `change` or `body` is present, otherwise `content`.
- **New tokens:** none.

## Adaptive

| Class | permission / question / gate / reply (content height) | with `change` or a form (`full`) |
|---|---|---|
| compact | bottom sheet, up to 90 % tall | bottom sheet at `sheetFullHeight` |
| medium | bottom sheet capped at 640 | the same |
| expanded / large | centred panel of 560 | end-side sheet of 400–480 beside the conversation, so the card stays visible; the diff is unified at that width (KitDiffView splits only when its own box is expanded, 840 dp or wider) |
| short | bottom sheet | bottom sheet |

- **Pinned answers** stack on compact and sit in one end-aligned row from medium (KitActionBlock, kit-KitAction-v2).
- **Keyboard (G14, LAY-10):**
  - Esc closes with `dismissed`, and obeys `draft` and `dirty` (DATA-3);
  - Tab follows reading order: Close, the body's controls (code copy, diff navigator, the switch, choices, fields), then the pinned answers;
  - the card's shortcuts also work here while focus is not in a text field: A allow, D reject, 1–9 choose;
  - Enter never answers a permission or a gate (allowing a command is not a neutral confirmation, LAY-10). It activates only the focused button.
- **Fine pointer:** hover as KitSheet. The diff gets mouse text selection (KitDiffView).

## Accessibility

- **Focus.** The route is named by the card's title, and TalkBack's first focus is the title. On close, focus returns to the card.
- **Announcements (A11Y-3).**
  - When the sheet closes itself, the sheet announces nothing. The card's receipt announces "Answered on the laptop" once, so the person hears exactly one message.
  - When the person answers here, the card's receipt announces the transition once.
- **The risk switch.** Its label is `title`. Unfolding moves focus to the scope sentence (KitSwitchRow.risk), and the duration choices are a `KitChoiceList.single` group (KitRowParts.md; arrow keys move, Space and Enter choose).
- **Targets:** 48 dp or more everywhere, with 8 dp between the pinned answers.
- **200 % text:**
  - `fullText` and the header wrap and never truncate;
  - the body scrolls;
  - the pinned answers stay pinned, also with the keyboard open (KIT-17);
  - the code block and diff scroll horizontally inside their own boxes.

## RTL

- The frame is as KitSheet: tile and title at the start, Close at the end. The pinned row mirrors.
- The command, paths and diff are LTR blocks (KitCodeBlock, KitTechnicalValue, KitDiffView).
- The question text and the task's message are shown as sent (COPY-2), laid out with first-strong direction, and `who` and `server` in the subtitle are wrapped with `KitBidi.auto` (COPY-30).
- The A and D shortcuts use physical keys, as on the card.

## Motion and haptics

- **The route is KitSheet's:** a slide on `standard`, or a cross-fade for the panel. The risk step unfolds with `KitReveal` inside the sheet body (not a scrolling list item, so MOT-5 allows it).
- **An in-place confirmation** swaps with `KitReveal`. The closing on answeredElsewhere uses the route's own exit on `standard`, and is instant under reduced motion.
- **Haptics:** `KitHaptics.send` exactly once per answer given here, including Always allow. There is none on dismissal, on answeredElsewhere, on the risk step unfolding, or with Vibration off. A gate's in-place merge confirmation of kind `stop` or `destructive` plays `commit` from `showKitConfirm`, not from here.
- **Reduced motion:** every state settles after one `pump()` (G8).

## Data safety and honest state

- **Typed input survives (DATA-1, DATA-2):**
  - the reject note and the gate notes are in `message.draft`, the reply in the card's reply draft, and a form's answers in `draft`;
  - back, swipe, Esc and Close keep them silently, and reopening restores them;
  - with only `dirty`, the discard question is asked in place;
  - the host clears each draft once the answer is confirmed, or when the request is answered elsewhere or expires. The sheet never clears one.
- **Closing by itself (AUTO-12).**
  - `routes` removes the route when the request stops pending.
  - A draft is kept for the host to clear.
  - Typed text held only under `dirty` is lost with the request, because the request no longer accepts an answer. The card's receipt says why ("Answered on the laptop"), so the sheet never disappears without a word (K2 §1.1).
  - If `routes` is already not pending at call time, nothing opens and the call returns `answeredElsewhere`.
- **One answer, once.** The first answer closes the route and every later tap is ignored. A shortcut and a tap arriving together send once.
- **Always allow (KIT-30, SEC-9; the card's own button, 13B, asks a one-step confirm with the same scope words):**
  - it is never the primary, and never preselected;
  - its scope and duration are shown before anything is sent;
  - choosing a duration sends the "always" answer through `onAllowAlways(until)` exactly once;
  - "Not now" folds the step and sends nothing;
  - the indicator while a saved rule is on is the host's status line and the saved-permissions page. The sheet has closed by then.
- **Nothing contradicts the card.** The sheet shows the card's `phase`. If the card leaves `waiting` while the sheet is open (answered here via a shortcut on the card, or answered elsewhere), the sheet closes.

## Depends on

- **kit-KitRequestCard-v2:** the card, its answers types, `iconFor`, and the kit copy.
- **kit-KitRowParts-v2:** `KitSwitchRow(risk:)`, `KitRisk`, `KitUntil`.
- **kit-KitDiffView:** `change`.

This matches C25: RequestSheet = [RequestCard-v2, RowParts-v2, DiffView].

It also uses these parts from earlier tiers, which have already merged by tier 1e. They are recorded as edges for bookkeeping (README.md); no tier moves:

- kit-KitSheet-v2 (`tone: attention`, the frame);
- kit-KitDetailsFold (`details`);
- kit-KitCodeBlock (`fullText` for a command);
- kit-KitChoiceList (`.multi` for ChooseMany);
- kit-KitField (`message`, reply).

Existing parts: `RequestRoutes`, `showKitConfirm`, `KitHaptics`, `KitReveal`, VL tokens.

**Depended on by:** chat-5, shared-chat-3, screen-shell-1 or slice-P3.11a, and slice-P4.1c.

## Tests required

In `test/kit/kit_request_sheet_test.dart`:

1. **The header is the card's:**
   - the title, the attention tile and the subtitle "{who} on {server} · waiting 4 min";
   - `routes.own` holds the sheet's route.
2. **Answered here:**
   - tapping "Allow once" calls `onAllow` once and fires `send` once;
   - the sheet is gone after `pumpAndSettle`;
   - the future completes with `answered`.
3. **Answered elsewhere:**
   - flipping `isPending` to false while open removes the route after one frame;
   - the future completes with `answeredElsewhere`, with no callback, no haptic and no announcement from the sheet;
   - with `routes` already not pending, no route is pushed and the call returns `answeredElsewhere`.
4. **Dismissed:**
   - back, swipe down, Esc, Close and a tap outside each return `dismissed`;
   - the typed reject note is restored on reopen from `oc.draft.request.<id>.note.<profileId>`.
5. **Always allow:**
   - tapping the switch unfolds the scope and calls nothing;
   - choosing "For this conversation" calls `onAllowAlways(KitUntil.conversation)` once and closes with `answered`;
   - "Not now" folds and sends nothing;
   - no pinned action is labelled Always allow.
6. **Question:**
   - every option is listed;
   - a tap calls `onChosen` once and closes;
   - Something else keeps its draft.
7. **ChooseMany:** Send is disabled with the visible reason "Choose at least one answer." until one is chosen, then calls `onSend` with the set.
8. **Form:**
   - `submit` is disabled with its reason until valid;
   - with `draft`, dismissal keeps the typed text;
   - with `dirty` only, back asks the discard question in place and adds no route.
9. **Gate:**
   - Approve and Send back are pinned, and the notes are sent with Send back;
   - an in-place `showKitConfirm` (merge) adds no route.
10. **Permission with change:**
    - a read-only `KitDiffView` is present;
    - at 1280×800 the sheet is an end-side sheet of 400–480, and the diff is unified.
11. **Details:** `details` render last and collapsed in one `KitDetailsFold`.
12. **Keyboard:**
    - A allows and D rejects while focus is on the sheet, but not while it is in the note field;
    - Enter on the sheet does not allow;
    - Tab reaches Close, the switch, the options and the pinned answers.
13. **200 %:** at `textScaler` 2.0 with `viewInsets.bottom` 300, the pinned answers are fully visible. There is no overflow at 320/412/600/840/1280.
14. **RTL:** in Arabic, `fullText` for a command is laid out LTR, and the pinned row mirrors.
15. **Reduced motion (G8):** opening, the risk unfold and the in-place confirm each settle after one `pump()`.

## Galleries required

`test/goldens/kit/kit_request_sheet_golden_test.dart`, DPR 3.0, Android, VL fonts plus Noto Sans Arabic. Names follow TEST-20.

- **Every declared state × dark and light at 412×915:**
  - permission (full command, Always allow folded, reject note);
  - permission-always (scope unfolded);
  - permission-change;
  - question;
  - question-many;
  - form;
  - form-discard;
  - gate;
  - gate-confirm;
  - reply.

  That is 20 PNGs.
- **permission × dark and light** at 360×800, 915×412, 800×1280, 1280×800 (the 560 panel) and 1600×1000: 10 PNGs.
- **permission-change** at 1280×800 (the end-side sheet), dark and light: 2 PNGs.
- **permission in Arabic and at text 2.0** at 412×915 and 1280×800, dark and light: 8 PNGs.

That is 40 PNGs in all.

## Non-goals

- No screen adoption: the four old sheets are moved by chat-5, shared-chat-3, screen-shell-1 or slice-P3.11a, and slice-P4.1c.
- No in-place answers on the card (KitRequestCard.md).
- No server-wide auto-approve and no saved-permissions list (the session-approvals sheet, chat-5).
- No form field mapping: the OC2 schema to kit fields stays in form_renderer (shared-chat-3).
- No notification quick answers (B7: notifications open the card).
- No `change` (config-change) variant (P2 deferred).
- No second route for the diff or for a confirmation.

## Open questions

None. K2 left three choices open, and STANDARDS settles them:

- **Closing on answer.** A write shows its receipt where the request lives (K2 §4.8, AUTO-17), so the sheet closes on answer rather than holding a second receipt.
- **Enter never allows.** LAY-10 lets Enter confirm only a neutral confirmation.
- **The dirty-only loss on answeredElsewhere.** The request no longer accepts an answer, and the card's receipt says why.
