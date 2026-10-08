# KitTurn — frozen API (wave 0, 2026-09-26)

> **Copy (SEC-13, coordinator 2026-09-27):** this part copies verbatim: `KitCopy.copy(context, text, redact: false)`. Code, diffs and messages are the person's own content.

Group: chat. Unit `kit-KitTurn` (wave 1, tier 1f, the last chat part; after kit-KitMessage, kit-KitWorkLine, kit-KitToolRow and kit-KitRequestCard-v2, C25, plus the edges in README.md). In wave 2c `lib/ui/kit/chat/kit_turn.dart` joins chain link chat-1 (C03, C38). Spec: kit-v2.md §9.2 (chat parts), §5; STANDARDS STATE-16 (the frozen turn model; Appendix A #49), KIT-41, STATE-15, AUTO-15, AUTO-17; team-conversation-2026-09-26 (a team task is a conversation: the task is the prompt, the lead is the reply). Rules: STATE-16, KIT-41, STATE-5, AUTO-15, KIT-23, KIT-28, LOOK-26, LOOK-27.

## Purpose

One turn of a conversation: the person's prompt and everything the agent did about it until it handed back, laid out in order, with exactly one footer (Copy and More) once the turn has finished and none while it runs. It makes the turn model a layout rule instead of logic every host re-derives, for the chat and for the AI Team conversation alike.

## Replaces

- **Map** (kit-v2.json, module:chat transcript): `chat#embedded-message-view` (kitGap KitTurn), `demo#demo-chat`, `team-agent-output#team-agent-output-text` (3 elements). Also places `embedded-message-view#embedded-message-view-actions-button` (assigned KitIconButton; the footer's Copy and More). Map `statesMissing` fixed here: "a turn interrupted by server loss has no end marker" (`interrupted`), and chat's "first token slow > 8 s: no honest 'Starting the model…' line or way out" (`starting` with `since`). Owner Fix: "quieter per-turn footer (latest reply only)".
- **Code** (chat-1 rewrites `lib/ui/screens/chat/message_view.dart`; chat-4 the team view): the per-turn layout of `_MessageView` (:1738, the meta row and footer, :2010–:2100), `_MessageActionsDisc` (:2190), the footer placement that `_turnActionOwners` (:147) computes (with KitTurn the footer is always after the last block, so the host only groups messages into turns), the transcript of `TeamAgentTranscript` (`team_conversation.dart` → chat-4), and the lead's lines in `team_conversation_view.dart`. Raw widgets counted in `message_view.dart`'s G16 (111) and G1 (SnackBar 1, showSnackBar 1: the copy feedback, now KitCopy) go to zero in chat-1.

## File

`lib/ui/kit/chat/kit_turn.dart` (new).

## Public API

```dart
/// Where a turn stands. The host derives it; the turn only lays it out.
enum KitTurnPhase {
  starting,      // sent; nothing has come back yet
  running,       // the agent is working
  waitingForYou, // a request in this turn waits for the person
  finished,      // the agent handed back
  stopped,       // the person stopped it
  interrupted,   // the server or connection went away before it finished
  failed,        // it ended on an error (the error is a KitNotice block)
}

/// The one footer of a finished turn (STATE-16).
@immutable
class KitTurnFooter {
  const KitTurnFooter({
    required this.copyText,               // the whole turn's reply, read at tap time (KIT-23)
    this.meta,                            // host words: "Sonnet 4.5 · 12k tokens · 10:42" (model name as sent, COPY-2)
    this.menu = const <KitMenuItem>[],    // More: Fork from here, Revert, Read aloud, Details…
  });
  final String Function() copyText;
  final String? meta;
  final List<KitMenuItem> menu;
}

/// One turn: a prompt, then everything until the agent hands back, made of
/// steps whose boundaries are not drawn, and notices that do not end it
/// (STANDARDS STATE-16, KIT-41). A finished turn has one footer after its
/// last block; a running turn has none.
///
/// States: starting, starting (slow), running, waitingForYou, finished,
/// finished (latest), stopped, interrupted, failed, highlighted (KIT-12).
class KitTurn extends StatelessWidget {
  const KitTurn({
    super.key,
    this.prompt,          // KitMessage.prompt; null for a turn with no prompt of its own (an automated first turn)
    required this.blocks, // oldest first: KitMessage (reply, thought, notice, marker), KitWorkLine,
                          //   KitToolRow (a lone step), KitRequestCard, KitNotice (an error)
    required this.phase,
    this.since,           // when the turn began; drives the starting line's 8 s escalation
    this.footer,          // drawn only when phase is finished, stopped, interrupted or failed
    this.latest = false,  // the newest turn: the footer shows its meta words
    this.highlighted = false, // the find-in-conversation current match
    this.live,            // KitTurnLive: the running turn's live line (below)
    this.stall,           // KitTurnStall: with live, why a quiet turn looks stuck (below)
    this.turnKey,
    this.footerKey,
    this.copyKey,         // today's ValueKey('message-copy-<id>')
    this.moreKey,
  });

  final KitMessage? prompt;
  final List<Widget> blocks;
  final KitTurnPhase phase;
  final DateTime? since;
  final KitTurnFooter? footer;
  final bool latest;
  final bool highlighted;
  final Key? turnKey, footerKey, copyKey, moreKey;
}
```

**Frozen behaviour.**

- **Order.** `prompt`, then `blocks` exactly as given, then the phase line (below), then the footer. `space4` between the prompt and the first block, `space3` between blocks, and `KitTokens.sectionGap` (22) after the turn, so the host adds no spacing between turns.
- **Phase line** (a quiet `secondary`/`text2` line at the prose's start edge, never a spinner in the body):
  - `starting` with no blocks: "Starting the model…"; once `since` is `KitMotion.escalateAfter` (8 s, KitSince `isSlow`) old it becomes "Still waiting for the model · {n} s". This phase line is drawn only when the host sets no `live`; with a live line, the slow wait (20 s, `KitTurnLive.slowAfter`) is the only slow-start wording the person sees (STATE-5). With blocks, nothing.
  - `stopped`: "You stopped this reply."
  - `interrupted`: "The connection dropped before this reply finished." (map's missing end marker).
  - `running`, `waitingForYou`, `finished`, `failed`: none (the work line, the request card and the error notice say it).
- **Live line** (`live: KitTurnLive?`; owner decision 2 Oct 2026, 01B moved the status from the composer's edge back into the turn). Drawn under the turn's last block, in place of the phase line, from the moment the prompt is sent until the turn ends: one pulsing dot (12A, the one thing that moves in a chat; Calm pulses at half pace, Off and remove-animations keep it still) and "{status}…" for the first `KitTurnLive.showElapsedAfter` (5 s), then "{status} · {n} s" / "{m} min {n} s" (KitSince, one tick a second), plus an optional `note` ("Sends after this reply"). The words are a live region; the seconds are not announced. **There is no Stop here**: Send in the composer becomes the one Stop (KitComposer.md). `KitTurnActivity`: sending (reads as thinking), waitingForServer "Thinking" (from 20 s: "The server has not answered yet"), waitingForModel "Thinking" (after 20 s: "Waiting for the model's first word"), thinking "Thinking", writing "Writing", working "Working", waitingForYou "Waiting for you". The composer stays idle and keeps its mic while a reply runs, so speaking or typing during a reply queues the message.
- **Footer.** Only for finished, stopped, interrupted and failed, and only when `footer` is non-null. One row after the last block: `meta` in `caption`/`text3` at the start (only when `latest`; older turns keep the row quiet, per the owner Fix), then `KitIconButton.copy(text: footer.copyText, tooltip: "Copy reply")` and a More `KitIconButton` ("More for this reply") that opens `showKitMenu(items: footer.menu)`; More is left out when `menu` is empty. The two buttons are 48 dp with 8 dp between them. There is never a second footer, and no step in `blocks` draws Copy or More.
- **Long-press.** In every phase (running included, where no footer is drawn), long-press and right-click on the turn's replies open `footer.menu` plus a Copy item (`KitMenuItem.copy`), and the same items are the turn's semantic custom actions. A prompt's own long-press opens the prompt's menu (the inner part wins).
- **Highlighted.** The turn sits on a `surface1` band with `panelCornerRadius`, inset by `space2`; no accent, no outline.
- **Kit-only.** `blocks` is `List<Widget>` so the turn needs no import of every block type; G16 keeps the call site kit-only. The doc comment lists the allowed parts.

**Kit copy** (ARB, `kit` prefix, en + ar): `kitTurnStarting` "Starting the model…", `kitTurnStillStarting` "Still waiting for the model · {seconds} s", `kitTurnStopped` "You stopped this reply.", `kitTurnInterrupted` "The connection dropped before this reply finished.", `kitTurnCopy` "Copy reply", `kitTurnMore` "More for this reply", `kitTurnActions` "Reply actions" (the menu's name).

- **Stall line** (`stall: KitTurnStall?`, 2026-10-08, FC3). With `live` only: the running turn has gone quiet long enough for the host's stall watchdog to explain it. Drawn under the live line (never instead of it; the turn still runs): the host's one sentence in the secondary tone, a live region read once, and at most two `actions` as tertiary words under it (the chat gives Stop reply and Details). The host never puts error, exception or server text in `message`; technical facts belong behind Details.

## States

Declared (KIT-12): **starting**, **starting slow** (after 8 s on the phase line; after 20 s on the live line), **running**, **waitingForYou**, **finished**, **finished latest** (meta words), **stopped**, **interrupted**, **failed**, and **highlighted**. No loading (the transcript skeleton is `KitSkeletonTranscript`, the host's), no empty (a turn always has a prompt or a block), no disabled. No working state of its own: the parts inside say what works.

## Tokens

- ThemeRoles: `text2` (phase line), `text3` (meta), `surface1` (highlight band), `accent` (focus ring only).
- KitText roles: `secondary` (phase line), `caption` (meta).
- KitTokens: `space2`, `space3`, `space4`, `sectionGap` (22), `minTarget` (48), `panelCornerRadius` (18, the highlight band), `focusRingWidth(context)` (§0.5 step 2 seam, `_new-tokens.md`).
- No new token. No glass (LOOK-27: the transcript is content), no shadow, no frame.

## Adaptive

The turn fills the width it is given; the host caps the conversation pane at `KitLayout.paneDetailMaxWidth` (700, LAY-5) and centres it in the window's middle pane from expanded.

- **compact / medium:** footer buttons at the end of the footer row; meta wraps above them at 200 % text.
- **expanded / large:** the same turn inside the 700 dp pane; on a PC the More button's tooltip shows its shortcut when the host binds one.
- **Fine pointer:** right-click anywhere on the turn's replies opens its menu at the pointer; Copy and More show tooltips repeating their labels (LAY-11).
- **Keyboard:** Tab order is the prompt (if it has a menu), then each block's controls in reading order, then Copy, then More. Shift+F10 on a focused turn opens its menu.

## Accessibility

- The turn is one semantics container (a list item) whose custom actions are the footer menu and Copy, so a screen reader reaches them without finding the footer.
- Copy announces "Copied" once through KitCopy (KIT-23); there is no SnackBar.
- The phase line is not a live region; the host's status line announces starting, finished and interrupted once (A11Y-3). The slow-start escalation changes the words once.
- 48 dp buttons, 8 dp apart; at 200 % text the meta wraps and the buttons stay on one row at the end; nothing clips at 320 dp.

## RTL

- The footer's meta at the start, buttons at the end, both mirrored (directional insets).
- The prompt's end alignment and the blocks' start alignment are the parts' (they mirror).
- `meta` carries a model name and a time; the host isolates the model name with KitBidi.auto (COPY-30).

## Motion and haptics

- The footer appears at once when the phase becomes finished (no fade, so a finished turn does not flicker while the list recycles). The highlight band cross-fades on `KitMotion.quick` when `highlighted` changes.
- No layout animation (MOT-5). Under `KitMotion.reduced`, nothing moves; one `pump()` settles (G8x).
- Haptics: none here. `KitHaptics.done` for a finished reply is the host's, fired once per turn it watched finish (MOT-11); the turn cannot tell a finish it watched from one rebuilt later.

## Data safety and honest state

- Copy copies the whole turn's reply prose (STATE-16), never tool output or hidden text, read at tap time.
- A running turn never shows a footer or a "done" look; `stopped` and `interrupted` say which happened, and neither is shown as finished.
- A start escalates instead of waiting silently (STATE-5): after 8 s on the phase line, after `KitTurnLive.slowAfter` (20 s) on the live line, which is the path the app takes; it never claims progress it does not have. Failures are said in neutral words (LOOK-5).
- A turn waiting for a request relies on its KitWorkLine saying "Waiting for you" and its KitRequestCard; the turn adds no spinner (AUTO-15).

## Depends on

- **kit-KitMessage**, **kit-KitWorkLine**, **kit-KitToolRow**, **kit-KitRequestCard-v2** (C25). The last three appear only in docs and galleries (blocks are `Widget`), but the edges keep the gallery honest.
- **kit-KitIconButton-v2** (`KitIconButton.copy`, More), **kit-KitMenu** (`showKitMenu`, `KitMenuItem.copy`), **kit-KitSince** (starting escalation): edges added (README.md); all tier 1a, no tier change.
- Existing `KitNotice` (error blocks, in galleries), `KitMotion`, `KitText`.
- Pre-wave seams: `KitCopy.copy`, `KitMotion.escalateAfter`, `KitTokens.focusRingWidth`, `KitLayout.paneDetailMaxWidth`.

## Tests required

`test/kit/kit_turn_test.dart`:

1. Order: prompt, then blocks in the given order, then the footer last (by vertical position).
2. running and waitingForYou: no footer and no Copy/More buttons in the tree, even when `footer` is given; long-press on a reply still opens the footer menu with a Copy item.
3. finished: exactly one Copy and one More; More opens the given items; with an empty menu More is absent.
4. `latest: true` shows `meta`; `latest: false` hides it and keeps Copy and More.
5. Copy calls `copyText` at tap time and sets the clipboard through KitCopy; "Copied" is announced once; no SnackBar in the tree.
6. starting with no blocks shows "Starting the model…"; under fake async after 8 s it shows "Still waiting for the model · 8 s"; with a block it shows nothing.
7. stopped and interrupted show their end lines and a footer; neither shows "Starting…".
8. A prompt's long-press opens the prompt's menu, not the turn's.
9. `highlighted` paints the surface1 band; no accent colour is used.
10. Semantics: one container with custom actions for Copy and each menu item; no live region; buttons ≥ 48×48 and 8 dp apart.
11. Desktop capabilities: Tab order prompt → blocks → Copy → More; right-click on a reply opens the menu at the pointer; Shift+F10 opens it anchored.
12. Reduced motion: one `pump()` settles.
13. 200 % text at 320 dp, LTR and RTL: no overflow (G6).

## Galleries required

`test/goldens/kit/kit_turn_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20). Each scene is a real composition: a KitMessage.prompt, a KitWorkLine, a reply, and where relevant a KitRequestCard or KitNotice:

- States at 412×915, dark and light: `kit_turn_starting`, `kit_turn_starting_slow`, `kit_turn_running`, `kit_turn_waiting_for_you` (work line "Waiting for you" + a permission KitRequestCard), `kit_turn_finished`, `kit_turn_finished_latest`, `kit_turn_stopped`, `kit_turn_interrupted`, `kit_turn_failed` (a KitNotice block), `kit_turn_highlighted`.
- Default (finished latest) at 360×800, 915×412, 800×1280, 1280×800 (700 dp pane centred) and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800, dark.
- About 34 PNGs.

## Non-goals

- Grouping server messages into turns and deriving the phase (the host's turn model: `_isPrompt`, `_endsTurn`, `_turnSteps`; chat-1).
- The transcript list, its virtualisation, the jump pill and the skeleton (the host, KitJumpPill, KitSkeletonTranscript).
- Answering requests (KitRequestCard) and explaining errors (KitNotice).
- Firing the done haptic or announcing a finished turn (the host's status line).

## Open questions

None.
