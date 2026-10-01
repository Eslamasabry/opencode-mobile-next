# KitComposer — frozen API (wave 0, 2026-09-26)

Group: chat. Unit `kit-KitComposer` (wave 1, tier 1e; after kit-KitField, kit-KitIconButton-v2 and kit-KitLevelMeter, C16, C25, plus kit-KitComposerChips and kit-KitSegmented, README.md). In wave 2c `lib/ui/kit/chat/kit_composer.dart` joins chain link chat-3 (C03, C38); the voice strip's host file `voice_conversation.dart` is chat-5's, and the team composer is chat-4's. Spec: cut review C16 (the glass pill frame with dim, the multiline KitField slot, send and stop circles at least 8 dp apart and 48 dp, the attach and voice slots, voice mode with KitLevelMeter; P10.3's UI half), kit-v2.md §9.2, §5; visual language §5 (Composer), §6 (glass behind text is dimmed); programmes P10.3, P6.6, P9.5, P4.3; STANDARDS Appendix B9 (Stop and Send side by side, 8 dp apart; Stop is a `text1` circle). Rules: LOOK-26, LOOK-27, LOOK-29, LOOK-30, LOOK-5, LOOK-19, DATA-1, DATA-6, DATA-11(c), STATE-7, STATE-8, AUTO-8, MOT-11, LAY-9, LAY-10.

## Purpose

Where the person writes to the agent, or to the team: a glass pill floating above the conversation that holds the attach button, a multiline field, the model chip, voice, and Send or Stop. It says honestly what Send will do right now (send, send after this reply, add to this turn, send when back online) and becomes the voice mode, where the person talks instead of typing and can hear replies read aloud.

## Replaces

- **Map** (kit-v2.json, module:chat composer and transcript): `chat#embedded-composer` (kitGap KitComposer), `embedded-composer#embedded-composer-field`, `#embedded-composer-send`, `#embedded-composer-stop`, `#embedded-composer-activity`, and `embedded-voice-conversation-controls#embedded-voice-conversation-controls` (assigned here by C16, P10.3) (6 elements). It also hosts `embedded-composer-tools-button` and `-open-editor` (KitIconButton), `-delivery-toggle` (KitSegmented), and replaces `team-conversation#team-conversation-composer` (assigned KitField; the team decision renders the team on the chat's own parts, map: "the glass bar should become KitGlass-based KitComposer shared with the team conversation"). Map `statesMissing` fixed here: "offline: Send label does not say it will queue"; voice: "mic denied inside the mode". Owner Fix: "one trailing control (Send when text, Stop when empty, mic when empty and idle), plain Steer/Queue words ('Add to this turn' / 'Send after'), KitMotion timings". Owner Rethink (voice): "a composer mode: big mic in the send slot, listen→send→speak loop without the sheet, plain 'Read replies aloud'".
- **Code** (chat-3 rewrites `lib/ui/screens/chat/composer.dart`): `_ChatComposer` frame (:21), `_ComposerField` (:681), `_PromptToolsButton` (:768), `_ComposerSubmit` (:1129), `_WorkingMark` (:1272), `_DeliveryControl` (:1326), `_QueueHint` (:1409), `_ComposerActivity` and `_ActivityRingPainter` (:1779, :1834; with `AppTheme.glow`, which LOOK-20 removes). The tools sheet `_PromptToolsSheet` (:826; G1 `showModalBottomSheet` 1) becomes the host's `showKitSheet`, opened from `onTools`. The voice strip in `voice_conversation.dart` (chat-5; G16 CircularProgressIndicator 1, Container 1, Icon 4, Material 1, Scrollbar 1, SingleChildScrollView 1, SwitchListTile 1, Text 8, TextButton 4 = 22). `TeamComposerField` stays a KitField wrapper for the team's other screens (C37).
- **Ratchet:** `composer.dart` G16 = 120 (AnimatedContainer 1, AnimatedSwitcher 1, CircularProgressIndicator 2, ClipRRect 2, ColoredBox 2, CustomPaint 1, DecoratedBox 1, ExpansionTile 2, FractionallySizedBox 1, GestureDetector 1, Icon 29, IconButton 8, Image 1, InkWell 1, LinearProgressIndicator 1, ListTile 13, Material 2, Positioned 1, SegmentedButton 1, SingleChildScrollView 1, Text 40, TextButton 2, TextField 1, Tooltip 5, TweenAnimationBuilder 1), G1 = 1. chat-3 brings it to zero with this part, KitComposerChips and KitSheet.

## File

`lib/ui/kit/chat/kit_composer.dart` (new).

## Public API

```dart
/// What Send does while a reply is being written.
enum KitComposerDelivery {
  afterThisReply, // the default (P6.6 "Queue over Steer"): sent when the reply finishes
  addToThisTurn,  // steer: reaches the agent at its next step (OpenCode 2 only)
}

/// Where voice mode stands (P10.3).
enum KitVoicePhase {
  starting,     // getting the microphone
  listening,    // recording; the level meter moves
  transcribing, // turning speech into text
  waitingReply, // a voice turn was sent; waiting for the reply
  speakingReply,// reading the reply aloud
  replyReady,   // the reply could not be read automatically; "Read it aloud" is offered
  paused,       // a request needs the person; listening waits
  micDenied,    // the app may not use the microphone: explained in the mode, with a fix
  failed,       // the engine failed: the reason, with a fix
}

/// Voice mode's state and actions. Non-null [KitComposer.voice] turns the
/// pill into voice mode.
@immutable
class KitComposerVoice {
  const KitComposerVoice({
    required this.phase,
    required this.onExit,               // leaves voice mode; the draft keeps what was said
    this.conversation = false,          // true: speech is sent and replies loop (voice conversation);
                                        //   false: dictation into the draft
    this.level,                         // ValueListenable<double> 0..1 while listening (KitLevelMeter.listen)
    this.listeningSince,                // elapsed words while listening ("0:42"); no cap (P10.3)
    this.onListen,                      // starts listening (idle, replyReady, after a reply)
    this.onStopListening,               // ends this recording: dictation fills the draft, conversation sends
    this.onStopSpeaking,                // speakingReply: stop reading aloud
    this.onReadReply,                   // replyReady: read the reply by hand
    this.readRepliesAloud = false,
    this.onReadRepliesAloudChanged,     // null: the toggle is not shown
    this.reason,                        // micDenied / failed: the words
    this.fix,                           // micDenied: "Allow microphone"; failed: "Try again"
    this.voiceKey,
  });
  final KitVoicePhase phase;
  final VoidCallback onExit;
  final bool conversation;
  final ValueListenable<double>? level;
  final DateTime? listeningSince;
  final VoidCallback? onListen, onStopListening, onStopSpeaking, onReadReply;
  final bool readRepliesAloud;
  final ValueChanged<bool>? onReadRepliesAloudChanged;
  final String? reason;
  final KitAction? fix;
  final Key? voiceKey;
}

/// The composer (VL §5): a surface2 glass pill (KitGlass, dimmed) holding
/// attach, the field, the model chip, voice, and send or stop. Send is an
/// accent circle; Stop is a danger circle with an on-danger square.
///
/// States: idle empty, idle with text, sending, busy empty, busy with text
/// (stop + send, delivery choice), busy with text that cannot send yet,
/// offline, read-only, voice (every KitVoicePhase) (KIT-12).
class KitComposer extends StatefulWidget {
  const KitComposer({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.hint,                  // "Ask OpenCode…", "Ask {agent}…", "Message the team…"
    required this.onSend,
    this.fieldLabel,                     // the field's accessible name; null: "Message"
    this.busy = false,                   // a reply is being written
    this.onStop,                         // null: no Stop (a host that cannot stop)
    this.stopping = false,               // Stop's own tap is in flight
    this.sending = false,                // Send's own tap is in flight
    this.canSendWhileBusy = false,       // the server accepts a send during a reply
    this.delivery = KitComposerDelivery.afterThisReply,
    this.onDeliveryChanged,              // null: no choice (the words still say "after this reply")
    this.offline = false,                // Send queues: "Send when back online"
    this.readOnlyReason,                 // non-null: no typing; the reason is shown in the pill
    this.note,                           // one muted line at the top of the pill ("Goes to the team's planner")
    this.attachments,                    // KitComposerChips.attachments, above the field
    this.suggestions,                    // KitComposerChips.suggestions, above the field
    this.model,                          // KitComposerChips.model, in the bottom row
    this.onTools,                        // "+": the host's tools sheet (attach, photos, commands…)
    this.toolsDisabledReason,            // non-null: "+" is hidden and this shows in the note line
    this.onVoice,                        // the mic: the host enters voice mode; null: no voice
    this.voice,                          // non-null: voice mode
    this.onOpenEditor,                   // full-screen editor; shown only when there is text
    this.onContentInserted,              // ValueChanged<KeyboardInsertedContent>? (IME images)
    this.composerKey,                    // today's Key('chat-composer-surface')
    this.fieldKey,                       // today's Key('chat-composer-field')
    this.sendKey,                        // today's Key('chat-send-button')
    this.stopKey,                        // today's Key('chat-stop-button')
    this.toolsKey,                       // today's Key('composer-tools-button')
    this.voiceButtonKey,
    this.editorKey,                      // today's Key('prompt-editor-button')
    this.deliveryKey,                    // today's Key('composer-delivery-control')
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final String hint;
  final VoidCallback onSend;
  final String? fieldLabel;
  final bool busy, stopping, sending, canSendWhileBusy, offline;
  final VoidCallback? onStop;
  final KitComposerDelivery delivery;
  final ValueChanged<KitComposerDelivery>? onDeliveryChanged;
  final String? readOnlyReason, note, toolsDisabledReason;
  final KitComposerChips? attachments, suggestions, model;
  final VoidCallback? onTools, onVoice, onOpenEditor;
  final KitComposerVoice? voice;
  final ValueChanged<KeyboardInsertedContent>? onContentInserted;
  final Key? composerKey, fieldKey, sendKey, stopKey, toolsKey, voiceButtonKey, editorKey, deliveryKey;

  /// Lays [composer] over the bottom of [body] (the transcript) as the
  /// floating navigation layer: [body] scrolls under the glass, and the
  /// composer's height is published with KitBottomInset.add so the last
  /// message, KitJumpPill and KitUndo stay clear of it. The keyboard lifts
  /// the composer; nothing else moves. [composer] is the KitComposer or the
  /// host's widget that builds one; [above] is solid content pinned over it
  /// (request cards, notes, find) on the ground, edge to edge, so the body
  /// passes under it unseen and the composer stays the only glass. The
  /// published height counts both; [above] gets the room the composer leaves.
  static Widget layer({Key? key, required Widget body, required Widget composer, Widget? above});
}
```

**The trailing control** (the owner's "one trailing control", with B9's interim for busy OpenCode 2):

| Situation | Trailing | Words (semantics and tooltip) |
|---|---|---|
| idle, no text, `onVoice` set | mic in an `accent` circle | "Talk instead of typing" |
| idle, no text, no voice | Send circle, disabled (`surface3`, `text3` glyph); the hint in the field is its visible reason | "Send" |
| idle, text | Send in an `accent` circle, `onAccent` glyph | "Send" · offline: "Send when back online" |
| sending | the Send circle shows its spinner (STATE-7: this tap only) | "Sending" |
| busy, no text, `onStop` set | Stop: a `dangerFill` circle with an `onDangerFill` square (`kit_composer.dart:1792-1800`) | "Stop the reply" |
| busy, no text, no `onStop` (the app: Stop lives on the edge, below) | the mic stays usable, so a message can be spoken while the reply runs and sent after it (`:466-473`) | "Talk instead of typing" |
| busy, text, `canSendWhileBusy` | One trailing control, Send; Stop leads the row after "+" (48 dp each, never side by side; owner Fix "one trailing control", slice-close-chat 2026-09-28, supersedes B9's side-by-side pair) | Send: "Send after this reply" or "Add to this turn" |
| busy, text, not `canSendWhileBusy` | Stop only; the note line says "You can send when this reply finishes" (STATE-8) | "Stop the reply" |

- **Delivery.** While busy with text, `canSendWhileBusy` and `onDeliveryChanged` set, a two-segment `KitSegmented` ("Send after" · "Add to this turn", default `afterThisReply`, P6.6) sits at the top of the pill. Without `onDeliveryChanged` the note line states "Sends after this reply". The host remembers the choice per server (DATA-6).
- **The living edge** (`rail: KitTurnLive?`, `failure: KitComposerFailure?`, `railNote`; owner decision 2026-09-29/30, `docs/qa/crit-composer-rail-2026-09-29`). Live status and Stop sit on the composer's top border, not in the turn: the border bends into one wide, shallow dip (zero height change) that cradles the words "{status}…" / "{status} · {n} s" in `text2` and a small red Stop square; the whole caption is one "Stop reply" button, and the words are a live region that announces phases, not seconds (`kit_composer.dart:1573-1632`). A soft light travels the outline (decorative, left out of semantics; calm and reduced motion keep the dip and words still). A failed send uses the same edge with neutral words (`text2`), a "Try again" action and a Details icon when there is technical text; no red anywhere in it (LOOK-5). The app sets `rail` and no `onStop` on the composer: the edge owns Stop.
- **Layout.** Inside the pill, top to bottom: `note` or the reason line; the delivery segments; `suggestions`; `attachments`; the field (1 line, growing to 8, then scrolling; never taller than `KitLayout.composerMaxShare` of the window height, pre-wave, `_new-tokens.md`); the bottom row: "+" (`KitIconButton`, "Attach and more"), `model` (flexes and shrinks first), the editor button when there is text, then the trailing control.
- **The field.** A `KitField.composer(...)` (KitField.md) owned by the composer: multiline, with `hint`, `controller`, `focusNode`, and `fieldLabel` as its accessible name but no visible label (VL §5; README.md, decision D18); sentence capitalisation; IME image insertion through `onContentInserted`.
- **Keys.** Ctrl+Enter and Cmd+Enter send. With a fine pointer (`KitLayout.finePointer`), Enter sends and Shift+Enter inserts a newline; on touch, Enter inserts a newline. Esc closes the suggestions if they are open, otherwise leaves voice mode, otherwise unfocuses the field. The text is always kept.
- **Read-only.** With `readOnlyReason` the field is not editable, the reason shows in the note line, and Send, "+", voice and the model chip are hidden (Stop stays if busy).
- **Voice mode.** The pill keeps its frame. The field and chips give way to: an exit button ("Leave voice mode", start), the `KitLevelMeter.listen(listenable: level, active: phase == listening)` with the phase words and elapsed time, the "Read replies aloud" toggle (`KitChip.action(selected:)`) when `onReadRepliesAloudChanged` is set, and the trailing circle: listening → `accent` circle, "Send" (conversation) or "Done" (dictation) calling `onStopListening`; speakingReply → Stop (`dangerFill` circle, `onDangerFill` square), "Stop reading"; replyReady → "Read it aloud" (`onReadReply`) as a tertiary action beside a mic circle (`onListen`); waitingReply, transcribing, starting → no trailing action, the words say what is happening; micDenied and failed → `reason` and `fix` as a tertiary action.
- **Glass.** `KitGlass(borderRadius: composer radius, dim: true, shadow: true)`. Its fallbacks (frosted, Effects off `surface2` at 94 %, solid under high contrast, accessible navigation or remove animations) are KitGlass's (LOOK-29). No glow and no activity ring (LOOK-20): Stop in the trailing slot is the working signal.

**Kit copy** (ARB, `kit` prefix, en + ar): `kitComposerField` "Message", `kitComposerSend` "Send", `kitComposerSending` "Sending", `kitComposerSendOffline` "Send when back online", `kitComposerSendAfter` "Send after this reply", `kitComposerAddToTurn` "Add to this turn", `kitComposerStop` "Stop the reply", `kitComposerSendAfterShort` "Send after", `kitComposerAddToTurnShort` "Add to this turn", `kitComposerDeliveryLabel` "When to send", `kitComposerSendsAfter` "Sends after this reply", `kitComposerCannotSendYet` "You can send when this reply finishes", `kitComposerOffline` "Offline · sends when you're back online", `kitComposerTools` "Attach and more", `kitComposerVoice` "Talk instead of typing", `kitComposerEditor` "Open full-screen editor", `kitVoiceLeave` "Leave voice mode", `kitVoiceStarting` "Getting the microphone ready…", `kitVoiceListening` "Listening…", `kitVoiceTranscribing` "Writing down what you said…", `kitVoiceWaitingReply` "Waiting for the reply…", `kitVoiceSpeaking` "Reading the reply aloud", `kitVoiceReplyReady` "The reply is ready", `kitVoicePaused` "Paused · the agent needs you", `kitVoiceMicDenied` "The microphone is off for this app", `kitVoiceFailed` "Voice stopped", `kitVoiceSend` "Send", `kitVoiceDone` "Done", `kitVoiceStopReading` "Stop reading", `kitVoiceReadReply` "Read it aloud", `kitVoiceListen` "Listen", `kitVoiceReadAloud` "Read replies aloud", `kitVoiceElapsed` "{minutes}:{seconds}".

## States

Declared (KIT-12): **idle empty** (mic, or disabled Send), **idle with text**, **sending**, **busy empty** (Stop), **busy with text** (Stop + Send, with and without the delivery choice), **busy, cannot send yet**, **offline**, **read-only**, **focused**, and voice mode **starting**, **listening**, **transcribing**, **waitingReply**, **speakingReply**, **replyReady**, **paused**, **micDenied**, **failed**. Disabled controls either show a visible reason (the note line, the field's hint) or are hidden (STATE-8). No loading or empty state of its own.

## Tokens

- ThemeRoles: `surface2` (the glass fill, via KitGlass), `surface3` (disabled Send), `accent`/`onAccent` (Send and the mic circle; LOOK-6 primary), `text1` (field text), `danger`/`dangerFill`/`onDangerFill` (Stop only), `text2` (note, icons), `text3` (hint, disabled glyph), `glassRimLight`, `glassRimDark`, `glassShadow` (via KitGlass; on the VL branch).
- KitText roles: `body` (field), `secondary` (note, voice words), `label` (segments, chips).
- KitTokens: `composerRadius` (26, compact and medium), `composerRadiusWide` (18, expanded and large; LOOK-19), `minTarget` (48), `space1`–`space4`, `gutter` (the host's inset from the window edges), `focusRingWidth(context)` (§0.5 step 2 seam).
- **New (pre-wave, `_new-tokens.md`):** `KitTokens.composerActionSize` = 40 (the Send, Stop and mic circles, centred in a 48 dp target); `KitTokens.composerStopSquare` = 14 (Stop's square, corner radius a quarter of its side); `KitLayout.composerMaxShare` = 0.4 (the field's height cap as a share of the window height).

## Adaptive

The composer fills the width it is given; the host centres it in the conversation pane (≤ `KitLayout.paneDetailMaxWidth`, LAY-5) with `gutter` on each side.

| Window | Behaviour |
|---|---|
| compact | radius 26; the model chip shrinks to its glyph first when the bottom row is tight; the field's cap is 40 % of the window height (with the keyboard open, of what remains). |
| medium | radius 26; the same layout in a wider pill. A short landscape phone (height < 480, LAY-3) keeps the compact layout and a 3-line field before scrolling. |
| expanded / large | radius 18; Send's tooltip shows "Enter", Stop's "Esc" when the host binds it; Enter sends. |

- **Fine pointer:** hover steps on every button (KitIconButton, KitTappable); tooltips repeat labels (LAY-11); Enter sends.
- **Keyboard:** Tab order: note action, delivery segments, suggestions, attachments, field, "+", model chip, editor, voice, Stop, Send. Arrow keys move within the segments. Esc as above. Focus rings are `accent` at `focusRingWidth`.
- Targets stay 48 dp on every window (§8.3).

## Accessibility

- The field's accessible name is `fieldLabel` ("Message"), its hint is `hint`; the note line is read before it.
- Every control is labelled by the table above; the delivery segments are a group named "When to send".
- Voice mode: the phase words are a live region, announced once per phase change (A11Y-3); the level meter is excluded from semantics; the elapsed time is not announced.
- The composer does not announce "working"; the conversation's status line does (A11Y-3).
- Glass holding text is dimmed, and labels keep 4.5:1 over any backdrop (LOOK-30, KitGlass).
- 200 % text: the field grows within its cap and scrolls; the bottom row keeps "+" and the trailing control at 48 dp and shrinks the model chip to its glyph; the note and voice words wrap; nothing clips at 320 dp; Stop and Send keep 8 dp between them (P9.5).

## RTL

- "+" at the start, the trailing control at the end, mirrored. The send glyph mirrors (LAY-8); the mic, Stop's square and the level meter do not.
- The field follows the text typed (the first strong character decides), so English typed in the Arabic app reads LTR.
- The hint's agent name is isolated by the host with KitBidi.auto (COPY-30).

## Motion and haptics

- The trailing control swaps (mic ↔ Send ↔ Stop, Stop + Send) with `KitSwap` on `KitMotion.quick`; entering and leaving voice mode cross-fades the pill's content on `KitMotion.standard`. The pill's height follows the text layout without an animation (MOT-5); the delivery segments and suggestions appear at once.
- The level meter is KitLevelMeter's. No ambient loop (the old working mark leaves; MOT-6).
- Reduced motion: instant swaps; one `pump()` settles (G8x).
- Haptics: `KitHaptics.send(context)` when the person activates Send (tap, Ctrl/Cmd+Enter, Enter on a fine pointer) or "Send" in voice conversation, the moment the words leave (MOT-11). Stop fires nothing: stopping the current turn is "neither" (DATA-11(c)), not a confirmed stop.

## Data safety and honest state

- The composer never clears or rewrites the text; the host clears it after a send it accepted. Back, Esc, leaving voice mode, a lost server and a crash keep the draft (DATA-1): the chat host persists it through its DraftStore; a composer inside a sheet uses the sheet's `KitDraft`.
- Send's words always match what will happen: offline queues ("Send when back online"), busy sends after the reply or adds to this turn as chosen, and a server that cannot take a send during a reply says so in words instead of a dead button.
- Stop is red: the `danger` square on the edge, the `dangerFill` circle in the trailing slot (LOOK-5, LOOK-26); it is the one red word of a running chat and needs no confirmation (DATA-11(c)). Failures, including a failed send, are neutral words with a neutral glyph, never red.
- Voice: dictation goes into the draft, never straight to the agent; conversation mode says it sends. There is no 30-second cap in the UI (P10.3); "mic denied" is explained in the mode with its fix.
- `sending` and `stopping` show only for their own tap (STATE-7).

## Depends on

- **kit-KitField** (C25): `KitField.composer`, the multiline field without a visible label.
- **kit-KitIconButton-v2** (C25): "+", voice, editor, exit.
- **kit-KitLevelMeter** (C25): `KitLevelMeter.listen`.
- **kit-KitComposerChips** (tier 1c: the typed slots `attachments`, `suggestions`, `model`) and **kit-KitSegmented** (tier 1d: the delivery choice): edges added (README.md). They move this unit to tier 1e; nothing in wave 1 depends on it.
- **kit-KitChip** (the Read replies aloud toggle), **kit-KitUndo** (`KitBottomInset` for `layer`), **kit-KitMotionParts** (`KitSwap`): edges added, all tier 1a.
- Existing `KitGlass` (VL branch: rim, dim, one shadow), `KitAction`, `KitButton.tertiary`, `KitHaptics`, `KitMotion`, `KitText`.
- Pre-wave seams: `KitTokens.focusRingWidth`, the glass roles, `KitLayout.paneDetailMaxWidth`.

## Tests required

`test/kit/kit_composer_test.dart`:

1. Trailing control per row of the table: the right widget, words and callbacks for each situation.
2. Busy with text and `canSendWhileBusy`: Stop and Send are both present, each ≥ 48×48, with ≥ 8 dp between their 48 dp areas.
3. Stop's circle paints `dangerFill` with an `onDangerFill` square; the edge's Stop square paints `danger`; nothing else in the composer paints `danger`, including the failed-send line (LOOK-5).
4. Delivery: the segments appear only when busy, with text, `canSendWhileBusy` and `onDeliveryChanged`; the default is "Send after"; changing it calls `onDeliveryChanged` once and Send's label follows.
5. Offline: Send reads "Send when back online" and the note says it queues; tapping calls `onSend`.
6. Busy, not `canSendWhileBusy`, with text: no Send; "You can send when this reply finishes" is visible.
7. Send tap, Ctrl+Enter and (desktop capabilities) Enter call `onSend` once and fire `KitHaptics.send` once (mocked); with Vibration off no haptic; on touch Enter inserts a newline; Shift+Enter inserts a newline on desktop.
8. The composer never changes `controller.text` itself (send, Stop, Esc, entering and leaving voice mode).
9. `readOnlyReason` shows the reason, the field is not editable, and Send, "+", voice and the model chip are absent.
10. Voice: each phase shows its words and trailing action; the phase words are a live region announced once per change; `micDenied` shows `reason` and `fix`; `onExit` from the exit button and from Esc.
11. Glass: the pill is a `KitGlass` with `dim: true`; with `disableAnimations` it is solid (KitGlass's rule).
12. `KitComposer.layer`: the body's `KitBottomInset.of` bottom equals the composer's height plus the inherited bottom; the keyboard (viewInsets 300) lifts the composer.
13. Field height: 20 lines of text at 915 dp window height cap the field at 40 % and scroll inside it.
14. Semantics and targets: every control labelled per the table; all targets ≥ 48×48.
15. Desktop capabilities: Tab order as specified; focus rings visible; Esc behaviour order (suggestions, voice mode, unfocus).
16. Reduced motion: one `pump()` settles.
17. 200 % text at 320 dp, LTR and RTL: no overflow; Stop leads the row and Send trails it, never side by side (G6, P9.5; `_stopLeads`, `kit_composer.dart:484-489`, shown only when the composer has an `onStop`). The prompt editor opens from the field's top-end corner, shown while there is text.

## Galleries required

`test/goldens/kit/kit_composer_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20), over a transcript-like backdrop with ambient fields so the glass is visible (glass on; plus one scene with Effects › Glass off):

- States at 412×915, dark and light: `kit_composer_idle_empty` (mic), `kit_composer_idle_text` (model chip, one attachment), `kit_composer_sending`, `kit_composer_busy_empty` (Stop), `kit_composer_busy_text` (Stop + Send + delivery), `kit_composer_busy_cannot_send`, `kit_composer_offline`, `kit_composer_read_only`, `kit_composer_suggestions`, `kit_composer_voice_listening`, `kit_composer_voice_speaking`, `kit_composer_voice_mic_denied`, `kit_composer_glass_off`.
- Default (idle with text) at 360×800, 915×412, 800×1280, 1280×800 (radius 18, 700 dp pane) and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800, dark.
- About 40 PNGs.

## Non-goals

- The tools sheet, the model picker, the prompt editor, history and stash (the host's sheets; chat-3).
- Sending, queueing, steering, persisting drafts, recording, chunking and transcribing audio, and reading aloud (the host, the offline queue, `lib/voice`).
- Queued messages (KitQueuedMessage) and slash-command matching (the host fills `suggestions`).
- The conversation's status line and its "working" announcement.

## Open questions

None. The field's visible label is settled in the cross-check (README.md, decision D18): KitField.md freezes `KitField.composer`, which keeps `label` as the accessible name and draws no visible label, as VL §5 and today's composer do (PROC-20: today's behaviour stands). A G2 pattern allows `KitField.composer(` only in `lib/ui/kit/chat/kit_composer.dart`. The coordinator records the composer as KIT-20's one exception in STANDARDS.
