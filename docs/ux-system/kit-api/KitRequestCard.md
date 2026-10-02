# KitRequestCard v2 — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitRequestCard-v2` (wave 1, tier 1d, kind `kit-change`; C25: after kit-KitReceipt, kit-KitChoiceList, kit-KitField, kit-KitNeedsYou, kit-KitSince, kit-KitAction-v2). Spec: kit-v2.md §2.1, §3 (answer-the-agent), §4.5, §4.8, §4.9, §8.2; visual language §1, §4, §5 as amended by Appendix A #74; the approved render `docs/design/visual-language-2026-09-26/Chat.png`.

Cut review:

- **C15:** the split into this unit and KitRequestSheet.
- **C41:** this unit absorbs slice-P4.1a's acceptance.
- **C02 and C26:** the attention styling belongs here; `KitNotice.card` is retired, see KitNotice.md.
- **C50:** there is no `change` variant, because P2 is deferred.

Rules: LOOK-4, LOOK-6, LOOK-19, LOOK-20, LOOK-21, LOOK-24, AUTO-9, AUTO-10, AUTO-15, AUTO-17, KIT-8, KIT-10, KIT-30, KIT-43, STATE-5, STATE-7, STATE-8, STATE-9, STATE-10, DATA-1, DATA-2, DATA-11, COPY-8, COPY-15, COPY-30, MOT-5, MOT-11, A11Y-3, A11Y-8, LAY-9, LAY-10, G37.

## Purpose

The one answer card, in the conversation, for everything an agent asks the person: OC1 permissions and questions, OC2 forms, ```choices``` blocks, AI Team gates and external-task replies. It says who asks about what and why, holds the common answer in place, opens one details sheet, and after an answer collapses to a one-line row carrying its `KitReceipt`. It is the only part, with `KitNeedsYou`, that draws the needs-you look (LOOK-24).

## Replaces

- **Map elements:**
  - kit-v2.json `changed[KitRequestCard]` replaces 2 elements: `integrations#pending-tiles` and `question-sheet#question-sheet-dismiss`;
  - `existingAdoption` counts 2 more;
  - the map pages it serves are embedded-permission-attention-card and embedded-question-attention-card (both `fix`).
- **Today's uses of the part:** 6 calls of `KitRequestCard(`:
  - `lib/ui/screens/chat/attention_card.dart`, 5 calls at :29, :167, :204, :321 and :348;
  - `lib/ui/screens/team/team_needs_you.dart:180`.
- **Classes its adoption retires** (in the adopting units, not here):
  - `_PermissionAttentionCard` (attention_card.dart:11), `_QuestionAttentionCard` (:56) and `_FormRequestCard` (:334). The file's G16 count is {ActionChip 1, ExpansionTile 2, Icon 1, SingleChildScrollView 1, Text 7}. Adoption is by chain link chat-5, which absorbs slice-P4.1b (C38, C42);
  - `TeamNeedsYouCard` (team_needs_you.dart:77, G16 {Icon 1, Text 3}), adopted by screen-team-1 and slice-P4.1c;
  - `AgentChoicesBlock` (`lib/ui/widgets/agent_blocks.dart:51`), the third choice-row implementation, dispatched from markdown.dart:398. It becomes `KitRequestCard.ask(kind: choice)` in shared-shell-1 (C42 correction);
  - `QuestionOptionRow` (`lib/ui/widgets/question_options.dart:23`, G16 {Container 1, Icon 1, InkWell 1, Text 3, TextField 1}). It goes through kit-KitChoiceList, which this card composes.
- **Kit look it takes over:** `KitNotice.card` (VL branch `kit_notice.dart:40`), retired by kit-KitNotice-v2 (KitNotice.md, "Retired API").

## File

- **Part:** `lib/ui/kit/kit_request_card.dart`.
- **Tests:** `test/kit/kit_request_card_test.dart`.
- **Gallery:** `test/goldens/kit/kit_request_card_golden_test.dart`.
- **Integrator-owned expectation:** `test/chat_states_standard_test.dart` keeps passing through the legacy constructor. Its "Review is the one full-width primary" test is updated by chat-5 (Appendix A #62), not here.

## Public API

```dart
/// What the agent asks for. No `change` variant (P2 deferred, C50).
enum KitRequestKind { permission, question, form, choice, gate, reply }

/// Where the request is.
enum KitRequestPhase {
  waiting,            // needs an answer: the full card, answers in place
  sending,            // answered here, not yet confirmed: the answer line + receipt
  answered,           // the server echoed the answer: the collapsed row
  answeredElsewhere,  // answered on another device: the collapsed row
  expired,            // the agent stopped waiting: the collapsed row, nothing to press
}

/// The common answer shown in place. Sealed, so the kit owns the labels,
/// the order, the shortcuts, the send haptic and double-send protection.
sealed class KitRequestAnswers {
  const KitRequestAnswers();
}

/// permission and gate: two acts. Shortcuts A and D on a PC.
final class KitRequestDecide extends KitRequestAnswers {
  const KitRequestDecide({
    required this.onAllow,
    required this.onReject,
    this.allowLabel,       // default by kind: permission "Allow once", gate "Approve"
    this.rejectLabel,      // default by kind: permission "Reject", gate "Send back"
    this.disabledReason,   // required when a callback is null (STATE-8)
    this.allowKey,
    this.rejectKey,
    this.alwaysAllow,      // KitRequestAlwaysAllowStep?: null where the server keeps no standing grants
  }) : assert((onAllow != null && onReject != null) || disabledReason != null);
  final VoidCallback? onAllow;
  final VoidCallback? onReject;
  final String? allowLabel;   // a verb (COPY-8): "Run once", "Don't run" (Chat.png)
  final String? rejectLabel;
  final String? disabledReason;
  final Key? allowKey;
  final Key? rejectKey;
  final KitRequestAlwaysAllowStep? alwaysAllow;
}

/// 13B: the quiet third act under Allow once and Reject (permission only).
/// Tapping it asks one `showKitConfirm` (title "Always allow <command or tool>
/// in this project?", body = what the grant covers and where to take it back,
/// Always allow / Cancel); only a confirm calls `onConfirmed`, once. Cancel and
/// dismissal send nothing.
class KitRequestAlwaysAllowStep {
  const KitRequestAlwaysAllowStep({required this.what, required this.covers, required this.onConfirmed, this.buttonKey, this.confirmKey});
}

/// question and choice with one answer: a tap sends (KitChoiceList.single,
/// sends: true). Shortcuts 1–9 on a PC.
final class KitRequestChoose<T> extends KitRequestAnswers {
  const KitRequestChoose({
    required this.choices,        // at most KitRequestCard.maxChoicesInPlace in place
    required this.onChosen,       // called once per answer
    this.chosen,                  // the value sent (sending): its row carries the receipt
    this.other,                   // "Something else": a KitField with a KitDraft
  });
  final List<KitChoice<T>> choices;
  final ValueChanged<T> onChosen;
  final T? chosen;
  final KitChoiceOther? other;
}

/// A question with several answers: the card shows "Answer" (opens the
/// sheet); the sheet shows KitChoiceList.multi and a pinned Send.
final class KitRequestChooseMany<T> extends KitRequestAnswers {
  const KitRequestChooseMany({
    required this.choices,
    required this.onSend,         // ValueChanged<Set<T>>
    this.sendLabel,               // default "Send"
  });
  final List<KitChoice<T>> choices;
  final ValueChanged<Set<T>> onSend;
  final String? sendLabel;
}

/// reply (an external task asks for words): a multiline KitField with a
/// draft and Send in place.
final class KitRequestReply extends KitRequestAnswers {
  const KitRequestReply({
    required this.fieldLabel,     // "Your reply"
    required this.draft,          // DATA-1: survives back, kill, restart
    required this.onSend,         // ValueChanged<String>
    this.sendLabel,               // default "Send"
    this.fieldKey,
    this.sendKey,
  });
  final String fieldLabel;
  final KitDraft draft;
  final ValueChanged<String> onSend;
  final String? sendLabel;
  final Key? fieldKey;
  final Key? sendKey;
}

/// form, or anything too long to answer in place: one primary that opens
/// the sheet (onDetails). Default label "Answer".
final class KitRequestInSheet extends KitRequestAnswers {
  const KitRequestInSheet({this.label, this.key});
  final String? label;
  final Key? key;
}

class KitRequestCard extends StatefulWidget {
  /// NEW, the v2 card. Every request uses this constructor.
  const KitRequestCard.ask({
    super.key,
    required KitRequestKind kind,
    required String title,              // the ask in one line; permissions per COPY-15
    required String who,                // who asks: "fox", "Reviewer"
    required KitNeedsYouReason reason,  // G37; words from KitNeedsYou.reasonWord
    required String ifIgnored,          // G37, AUTO-10: "The agent waits; nothing is lost."
    required String announcement,       // read once when the card appears
    KitRequestPhase phase = KitRequestPhase.waiting,
    String? server,                     // only when it comes from another server: "laptop"
    String? detail,                     // why it asks, one line: "To check the fix"
    String? summary,                    // command, path or pattern: mono, LTR
    KitRequestAnswers? answers,         // null: nothing in place (Details only)
    String? answer,                     // the answer in words once sent: "Run once"
    KitReceipt? receipt,                // required from `sending` on (asserts below)
    VoidCallback? onDetails,            // opens showKitRequestSheet
    DateTime? since,                    // when it was asked: the age words
    List<KitAction> tertiary = const [],// at most one more act ("See the change"); "Always allow" has its own slot in `KitRequestDecide.alwaysAllow`
    IconData? icon,                     // default: KitRequestCard.iconFor(kind)
    Key? titleKey,
    Key? detailsKey,
  });

  /// Retired by kit-KitRequestCard-v2: use [KitRequestCard.ask].
  /// The pre-v2 constructor, kept working (KIT-43, never @Deprecated); its
  /// calls take the v2 frame. `KitRequestCard(` becomes a G2 ratchet
  /// pattern; chat-5, shared-shell-1 and screen-team-1 bring it to zero.
  const KitRequestCard({
    super.key,
    required IconData icon,
    required String title,
    required String announcement,
    AppStatusTone? tone,
    String? summary,
    String? detail,
    Widget? body,
    KitAction? primary,
    KitAction? secondary,
    List<KitAction> tertiary = const [],
    Key? titleKey,
  });

  /// More choices than this are not listed in place: the first
  /// (maxChoicesInPlace − 1) show, then "{n} more answers" opens Details.
  static const int maxChoicesInPlace = 5;

  /// permission: permissions · question: question · form: editNote ·
  /// choice: checks · gate: policy · reply: reply (AppIconography names).
  static IconData iconFor(KitRequestKind kind);
}
```

**Debug asserts on `.ask` (G37, each with a test that expects an `AssertionError`):**

- `ifIgnored` is not empty;
- `phase` in {sending, answered, answeredElsewhere} requires a `receipt`, and `expired` requires none;
- `sending` requires `receipt.since != null`, so the 8 s escalation always runs (STATE-5), and requires `answer`;
- `answered` requires `receipt.state == confirmed` (STATE-10: never "answered" before the echo);
- `answeredElsewhere` requires `receipt.state == answeredElsewhere`;
- `KitRequestInSheet` or `KitRequestChooseMany` requires `onDetails`;
- `KitRequestChoose.choices.length > maxChoicesInPlace` requires `onDetails`;
- `tertiary.length <= 1`, because Details takes the other tertiary slot (LAY-13: at most two).
- **The answers must fit the kind:**
  - `KitRequestDecide` only for permission and gate;
  - `KitRequestChoose` and `KitRequestChooseMany` only for question and choice;
  - `KitRequestReply` only for reply;
  - `KitRequestInSheet` for any kind.

**Notes:**

- **Additions to K2 §2.1:**
  - `reason` and `ifIgnored`: G37 and AUTO-10, matching `KitNeedsYou.row` in KitNeedsYou.md;
  - `server`: multi-server, as in `KitNeedsYou.row`;
  - `answer`: the words on the sending line;
  - `icon`: an override, as the permission glyph by action does today;
  - the keys.

  `who`, `title`, `announcement`, `phase`, `summary`, `detail`, `answers`, `onDetails`, `receipt`, `since` and `tertiary` keep K2's names.
- **A named constructor, not new fields on the old one.** K2's required `kind`, `who`, `reason` and `ifIgnored` cannot be added to the existing constructor without breaking its 6 calls (KIT-43). `.ask` makes them required at compile time. The legacy constructor keeps its exact signature. `StatelessWidget` becomes `StatefulWidget`, which no call site can observe (focus, the tapped-once guard).
- **The legacy constructor's look:**
  - `tone: attention` gets the attention look;
  - every other tone (including null) gets a plain `surface1` card with a `hairline` border and a `surface3` tile with a `text1` glyph. It never gets an accent tint (LOOK-6).
  - It keeps the internal key `kit-request-card`, 16 dp padding and a 1-physical-px border, so `chat_states_standard_test`'s width check (card − 34, ε 1) still holds.
- **Kit copy** (ARB `kit` prefix, en and ar, COPY-3, COPY-22):
  - `kitRequestAllowOnce` "Allow once", `kitRequestReject` "Reject";
  - `kitRequestApprove` "Approve", `kitRequestSendBack` "Send back";
  - `kitRequestAnswer` "Answer", `kitRequestSend` "Send";
  - `kitRequestReplyEmptyReason` "Type a reply first.";
  - `kitRequestMoreAnswers` "{count, plural, one{1 more answer} other{{count} more answers}}";
  - `kitRequestExpired` "Expired · the agent stopped waiting";
  - `kitRequestAge` "waiting {age}" (`{age}` from `KitSince.ageLabel`, rebuilt once a minute by `KitSince(ticks: minutes)`);
  - the existing `kitDetails` "Details".

  `chatUiAllowOnce`, `chatUiReject` and `teamUiGateAnswerApprove` lose their callers as screens adopt the card, and the integrator's key prune removes them (COPY-18: one action, one key).

## States

The doc comment declares: `waiting` (per kind), `waiting-refused` (a refused receipt above the answers), `disabled` (answers with their reason), `sending`, `sending-escalated` (not confirmed yet, Try again), `answered` (collapsed, Undo inside the window), `answered-elsewhere`, `expired`. In KIT-12 terms: working = sending, answered = answered or answeredElsewhere, disabled = the decide answers with `disabledReason`. There is no loading or empty: the card exists only once the request does.

**The waiting card, top to bottom:**

1. **Header row:**
   - the icon tile, with the attention glyph on the attention tint;
   - the caption (`caption` role, `attention`): "{reasonWord} · {who}[ on {server}] · {age}", for example "Needs your decision · fox on laptop · waiting 4 min";
   - the title (`headline`, text1), which wraps;
   - `detail` (`secondary`, text2);
   - `ifIgnored` (`secondary`, text2), on its own line.
2. **`summary`:** on a `ground` inset (radius `codeRadius`), `mono`, LTR. At most 2 lines, then an ellipsis. The full value is in semantics and in the Details sheet (A11Y-8).
3. **`receipt`** in `waiting`: a refused or not-confirmed earlier attempt ("Not accepted: {reason}"), directly above the answers.
4. **Answers:**
   - **Decide:** two buttons, secondary (reject) at the start and primary (allow) at the end, side by side when both labels fit on one line in half the width, as in Chat.png. Otherwise they stack full width with the primary on top.
   - **Choose:** a `KitChoiceList.single(sends: true)` with the `other` row.
   - **Reply:** the `KitField` (multiline, draft) with Send.
   - **InSheet and ChooseMany:** one full-width primary.
5. **The tertiary line, start-aligned:** "Details" (`onDetails`) and at most one `tertiary`.

**Sending:** the answer area is replaced in place by one line: "{answer} · {KitReceipt}" ("Run once · Sending…"). The header and summary stay. After 8 s without an echo, the receipt says "Not confirmed yet · Try again" (KitReceipt with KitSince). For choose, the chosen row carries the receipt, and the other rows are hidden.

**The collapsed row** (answered, answeredElsewhere, expired):

- one line: the kind glyph (`text2`, 20), the title (`secondary`, text2; one line below 1.3× text, two from 1.3×), then the receipt ("Allowed once · 10:42 · Undo", "Answered on the laptop") or the expired words;
- no card, border, ring or attention colour: it no longer needs you (LOOK-4);
- expired offers nothing to press.

## Tokens

All exist on `feat/visual-language-v1` unless flagged.

- **ThemeRoles:**
  - `surface1` blended with `attentionSurface`, the card;
  - `attentionLine`, the 1 px border;
  - `attention`: the caption, the tile glyph and the tile tint at `markTintAlpha`, and the ring;
  - `ground`, the summary inset;
  - `text1`, the title and the summary text;
  - `text2`: detail, `ifIgnored`, the collapsed row, and the legacy neutral glyph;
  - `surface3`, the legacy neutral tile and the secondary button;
  - `accent`/`onAccent`, the primary only (LOOK-6);
  - `hairline`, the legacy neutral border.

  The attention roles are referenced only in this file and in KitNeedsYou (LOOK-24, G17).
- **KitText:** `caption`, `headline` (`cardTitle`), `secondary` (`rowSupporting`), `mono` (`technicalValue`), `button`.
- **KitTokens:**
  - `cardRadius` (22), `space1`–`space4`, `codeRadius` (14; replaces the literal 10);
  - `markTintAlpha`, `smallIconSize` (20), `iconSize(context, …)` with `maxIconScale`;
  - `minTarget` (48), `buttonHeight` (50), `rowHeight` (54, the collapsed row's minimum).
- **KitLayout:** `readingWidth` (720, existing), the card's width cap outside a conversation (a team list on a wide window). It replaces the literal 860. Inside a conversation the host's pane is already at most `paneDetailMaxWidth` (700, a pre-wave name, `_new-tokens.md`), so the cap never binds there.
- **Pre-wave seams (flagged):** `hairlineWidth(context)` for the border, `focusRingWidth(context)` for the focus ring, and `KitBidi.auto`.
- **New tokens (pre-wave, `_new-tokens.md`; the coordinator adds them to `kit_tokens.dart` with the §0.5 seams):**
  - `KitTokens.needsYouRingWidth` (4) and `KitTokens.needsYouRingAlpha` (0.06): the needs-you ring LOOK-20 allows, a 4 dp `attention` ring at 6 % outside the border, with no blur;
  - `KitTokens.requestTileSize` (36) and `KitTokens.requestTileRadius` (10): the card's tile, today computed inline as `markSize − 8` and `markRadius − 2` (a KIT-9 literal);
  - `KitTokens.requestMaxHeightShare` (0.45): the 2.0-text height cap, today a literal.

  If these are not present when the unit starts, it uses `iconTileSize`/`iconTileRadius` for the tile and draws no ring, and lists the gap under NOT proven (§0.1 "Applies from").

## Adaptive

| Class | Layout |
|---|---|
| compact | full width of the transcript column, with the host's gutters |
| medium | the same |
| expanded / large | capped at `KitLayout.readingWidth` inside the conversation column; the PC shortcuts below |
| short (< 480 tall) | as compact, and the height cap below applies |

- **Shortcuts (§8.2), on a fine pointer from expanded, once the card has focus:**
  - **A** = allow and **D** = reject (`KitRequestDecide`);
  - **1–9** = send the nth in-place choice (`KitRequestChoose`); a digit beyond the list does nothing.
  - They are matched by physical key, so they work under an Arabic layout.
  - They are ignored while focus is inside the card's `KitField` (Something else, reply).
  - They obey the same tapped-once guard as taps, and they never act in `sending` or after it.
  - The hints show on the decide buttons through `KitAction.shortcut` ("A", "D"; kit-KitAction-v2). The digits are in the shortcuts help sheet and the choice rows' semantic hints.
- **Focus and keys:**
  - the card is one Tab stop, with a 2 physical px focus ring on its border; Tab moves on into its answers, Details and tertiary;
  - a new card never takes focus (the composer keeps typing);
  - Enter activates only the focused button;
  - Esc is not consumed.
- **A fine pointer:** hover highlights on the answers only. Targets stay 48 dp.

## Accessibility

- **Announcements (A11Y-3):**
  - `announcement` is announced once per request when the card first appears. A rebuild with the same request never re-announces;
  - phase changes are announced by the receipt's live region, once each. The card adds no second announcement;
  - `expired` is announced once ("Expired · the agent stopped waiting").
- **Semantics:** the card is one container: "{title}, {reasonWord}, {who}[ on {server}], waiting 4 min, {detail}, {ifIgnored}". The tile and the ring are excluded. `summary` is a separate node with its full, untruncated value.
- **Targets (LAY-9):** answers are 50 dp tall, at least 48; there is 8 dp or more between the two decide buttons and between the tertiary actions; choice rows are 56 dp (KitChoiceList).
- **Disabled answers** show their `disabledReason` as visible text under them (STATE-8).
- **200 % text:**
  - the caption, title, detail and `ifIgnored` wrap;
  - the decide buttons stack;
  - the card is never taller than `requestMaxHeightShare` (45 %) of the window (`requestMaxHeightShareLarge`, 60 %, from 250 % text, where a decide button wraps to two lines): past that, the words scroll inside the card and the answers stay visible (today's behaviour, kept);
  - no overflow at the LAY-4 widths.
- **Colour is never alone (STATE-9):** "Needs your decision" is the word, amber the tone.

## RTL

- The tile and caption sit at the start. The decide row mirrors: reject at the start, allow at the end.
- `who`, `server` and `title` placeholders inside the caption are wrapped with `KitBidi.auto`; the age comes from `intl` (COPY-30).
- `summary` is an LTR block aligned to the start of the card (COPY-30, KIT-32).
- Server text (a question's words) is shown as sent (COPY-2).
- The A and D shortcuts use physical keys, so they sit in the same place on an Arabic keyboard. Digits are matched on the digit and numpad keys.

## Motion and haptics

- **Arrival:** `KitEntrance` (fade and a small slide on `standard`). Under reduced motion it is instant.
- **No height animation.** The transcript is a scrolling list (MOT-5), so the change from waiting to sending, and from sending to the collapsed row, is a cross-fade on `KitMotion.quick` that keeps the scroll anchor, never a `KitReveal` fold. STANDARDS MOT-5 overrides K2 §2.1's "folds away with KitReveal" (§0.2). The Undo leaving after the window is KitReceipt's fade.
- **Haptics:** `KitHaptics.send` exactly once when the person answers (a decide tap, a choice, Send on a reply, A, D or a digit). There is none:
  - on Details;
  - on Try again (the same answer, resent);
  - on Undo;
  - on answeredElsewhere or expired;
  - with Vibration off (MOT-11).
- **Under `KitMotion.reduced`,** every phase settles after one `pump()` (G8).

## Data safety and honest state

- **One answer, once.** After the first answer (tap, shortcut or Send), every answer control ignores input until the host rebuilds with a new `phase` or `receipt`. A double tap never sends twice (G9).
- **Typed answers:**
  - "Something else" and reply text live in a `KitDraft` with target `request.<requestId>`, key `oc.draft.request.<requestId>.<profileId>` (DATA-1, DATA-4);
  - the host calls `clear()` once the answer is confirmed, or when the request is answered elsewhere or expires;
  - the card never clears a draft itself.
- **Receipts (STATE-10):** every answer shows its receipt. `answered` needs the server's echo (asserted); "Sent" is never "Done".
- **Undo** appears only when the host passes `receipt.onUndo`, which it does only where the server exposes a withdrawal (DATA-11(a)), inside `KitMotion.undoWindow`.
- **Always allow** (owner decision 13B, 2 Oct, supersedes the KIT-30 "never on the card" line) is a tertiary-weight button under Allow once and Reject, drawn only when the host passes `KitRequestDecide.alwaysAllow` (the server keeps standing grants: `ServerCapabilities.persistentPermissionGrants`). It is never a primary, never preselected, and never in `tertiary`. It always asks the one confirm first. The request sheet keeps its risk switch, with the same scope words ("in this project"). A notification action can only send Allow once or Reject, never "always".
- **Expired** says so and offers nothing to press (K2 §2.1).
- **Waiting words:** the conversation's work line says "Waiting for you" while a card waits (AUTO-15). That line belongs to chain link chat-1; this card only exposes `phase`.
- **Pointing surfaces:** Inbox rows, Work lines, notifications and team rows point to this card (`KitNeedsYou.row`) and never answer it (AUTO-17, LOOK-24).

## Depends on

- **kit-KitReceipt:** the receipt, `answeredElsewhere`, Undo and Try again.
- **kit-KitChoiceList:** `KitChoice`, `KitChoiceOther` and `KitChoiceList.single(sends:)`.
- **kit-KitField:** the multiline field with a draft, for reply and Something else.
- **kit-KitNeedsYou:** `KitNeedsYouReason` and `reasonWord`.
- **kit-KitSince:** the age words. The 8 s escalation is KitReceipt's through it.
- **kit-KitAction-v2:** `shortcut` and `disabledReason`.
- **Existing:** `KitButton`/`KitActionBlock`, `KitDraft` (kit_sheet.dart), `KitEntrance`, `KitHaptics`, `KitLayout`, `AppIconography`, VL tokens.
- **Pre-wave seams:** `KitBidi`, `hairlineWidth`, `focusRingWidth`, and the flagged tokens above.

This matches C25: RequestCard-v2 = [Receipt, ChoiceList, Field, NeedsYou, Since, Action-v2].

**Depended on by:**

- kit-KitRequestSheet;
- kit-KitTurn (C25);
- chat-5 (the three chat cards, P4.1b);
- shared-shell-1 (```choices```);
- screen-team-1 and slice-P4.1c (team gates);
- the integrations pending tiles (screen-library).

## Tests required

In `test/kit/kit_request_card_test.dart`, with KitSince's fake clock and `debugPlatformCapabilities` desktop for the shortcut tests:

1. **Every kind (waiting):**
   - the default glyph by kind;
   - the caption with the reason word, who, server and age ("waiting 4 min" at +4 min);
   - the title, detail, `ifIgnored` and summary (LTR);
   - the answers that fit the kind.
2. **Permission (P4.1a):**
   - "Allow once" and "Reject" in place;
   - one tap calls `onAllow` exactly once, and a second tap before the rebuild does nothing;
   - `KitHaptics.send` fires once, and never with Vibration off.
3. **Choice (P4.1a "an option sends with Undo"):**
   - a tap calls `onChosen` once;
   - rebuilt with `sending` and `chosen`, the chosen row shows the receipt;
   - rebuilt `answered` with `receipt.onUndo`, the Undo is visible at +7 s and gone at +8 s.
4. **Something else (P4.1a):**
   - "Something else" opens the `KitField`;
   - the typed text is saved under `oc.draft.request.<id>.<profileId>`, is restored after the card is disposed and pumped again, and is sent by Send.
5. **Reply:** Send is disabled with the visible reason "Type a reply first." while the field is empty.
6. **Phases:**
   - `sending` shows "{answer} · Sending…", and at +8 s "Not confirmed yet" with Try again, with one announcement;
   - `answered` collapses to one row with no attention colour (no `attentionSurface` or `attentionLine` painted);
   - `answeredElsewhere` shows "Answered on the laptop";
   - `expired` shows the expired words and no `KitButton` at all.
7. **Announcements:**
   - appear, then the transitions waiting → sending → answered produce exactly 3 announcements;
   - rebuilding the same state produces none.
8. **Shortcuts:**
   - with the card focused, A calls `onAllow`, D calls `onReject` and 2 sends the second choice;
   - without focus, nothing happens;
   - with focus in the Something else field, typing "a" inserts a letter and answers nothing;
   - in `sending`, A does nothing;
   - under an Arabic layout, physical A still allows.
9. **Targets:** `androidTapTargetGuideline` and `labeledTapTargetGuideline` pass, the two decide buttons are at least 8 dp apart, and at 360 dp wide at 1.0 the decide buttons sit side by side and at 2.0 they stack.
10. **The 200 % cap:** at `textScaler` 2.0 on 360×740 the card's height is at most 45 % of the window, the primary is fully visible, and there is no overflow at 320/360/412/600/840/1280.
11. **Asserts (G37):** each assert listed under Public API fires on its bad configuration.
12. **Legacy (KIT-43):**
    - every pre-v2 call shape compiles;
    - `kit-request-card` is present;
    - `tone: attention` paints the attention look;
    - `tone: null` paints no `accent` tint (LOOK-6);
    - `test/chat_states_standard_test.dart` passes unchanged.
13. **Details:** the "Details" tertiary calls `onDetails` once, and "{n} more answers" appears only when choices exceed `maxChoicesInPlace`.
14. **Reduced motion (G8):** every phase change settles after one `pump()`, with no running ticker.

## Galleries required

`test/goldens/kit/kit_request_card_golden_test.dart`, DPR 3.0, Android, VL fonts, Arabic with Noto Sans Arabic (TEST-8). Cards are shown in a transcript-like column.

- **Every declared state × dark and light at 412×915:**
  - permission-waiting (as Chat.png: "Run once" / "Don't run", command summary);
  - question-waiting (choices plus Something else);
  - form-waiting (Answer);
  - choice-waiting;
  - gate-waiting (Approve / Send back);
  - reply-waiting;
  - waiting-refused;
  - disabled;
  - sending;
  - sending-escalated;
  - answered (with Undo);
  - answered-elsewhere;
  - expired.

  That is 26 PNGs.
- **Every kind in Arabic and at text 2.0,** dark, at 412×915 (P4.1a: "every kind in dark, light, RTL and 200 %"): 12 PNGs.
- **permission-waiting × dark and light** at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000: 10 PNGs.
- **permission-waiting in Arabic and at text 2.0** at 1280×800, dark and light: 4 PNGs.

That is 52 PNGs in all. Names follow TEST-20 (`kit_request_card_<state>[_ar][_text2][_WxH]_<dark|light>.png`).

## Non-goals

- No screen adoption: attention_card, team_needs_you, agent_blocks and the integrations tiles move in their own units (P4.1a's "No screen adoption").
- No details sheet (KitRequestSheet.md), no Always allow and no server-wide auto-approve.
- No `change` variant (P2 deferred, C50, AUTO-20).
- No multi-select answering in place: `KitRequestChooseMany` answers in the sheet.
- No attention source: counting, ordering, clearing everywhere and notifications (AUTO-11, AUTO-12, AUTO-16) belong to the attention source and slice-P6.7.
- No work-line words (AUTO-15, chat-1).
- No visual digit badges on choice rows.

## Open questions

None. The visual choices K2 left open are settled by STANDARDS and the approved render:

- the collapse is a cross-fade (MOT-5);
- the decide buttons sit side by side (Chat.png);
- the legacy look has no accent tint (LOOK-6).

The flagged tokens are requests to the coordinator's pre-wave seam, not open contract questions.
