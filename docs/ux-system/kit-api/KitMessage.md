# KitMessage — frozen API (wave 0, 2026-09-26)

Group: chat. Unit `kit-KitMessage` (wave 1, tier 1d; after kit-KitMarkdown, C25, plus the edges in README.md). In wave 2c `lib/ui/kit/chat/kit_message.dart` joins chain link chat-1 (C03, C38). Spec: kit-v2.md §9.2 (chat parts), §5; visual language §5 (Transcript); STANDARDS STATE-16, KIT-41, LOOK-26, Appendix A #47 (the prompt is a bubble, not a ruled line). Rules: STATE-16, KIT-41, LOOK-26, LOOK-5, KIT-28, A11Y-5, COPY-2, KIT-32.

## Purpose

One piece of a transcript that is words: the person's prompt (an end-aligned `surface2` bubble with radii 20/20/6/20), the agent's reply (plain body text, no frame), a thought (folded reasoning), a notice (something that happened during a turn and that nobody typed), or a quiet marker (a model or project switch). It draws the words; the turn around it draws the controls.

## Replaces

- **Map** (kit-v2.json, module:chat transcript): `embedded-message-view#embedded-message-view-prompt` (kitGap KitTurn; the bubble is this part, the turn is KitTurn's), `team-conversation#team-conversation-prompt`, `embedded-message-view#embedded-message-view-reasoning-toggle`, `active-context-message#active-context-message-parts` (4 elements). Owner Fix on active-context: "drop the id above Details, disclaimer as one muted line at the end" (the host's words; this part gives it a notice line). `embedded-message-view-v2-rows` (OC2 notice rows, today KitAnimatedRows) render as `KitMessage.notice`.
- **Code** (chat-1 rewrites `message_view.dart`; chat-4 `team_conversation_view.dart`): `_UserMessageContent` (:2536) and the prompt branch of `_MessageView` (:1738, the "ruled line" prompt that Appendix A #47 replaces), `_AttachmentPart` (:2566, via KitComposerChips), `_Reasoning` (:2673), `TranscriptNotice` (:390) and `TranscriptMarker` (:210) (public in the chat library; chat-1 turns them into forwarding wrappers under R11), the assistant prose branch of `_AssistantMessagePart` (:1606). Their raw widgets are part of `message_view.dart`'s G16 count of 111 that chat-1 brings to zero.

## File

`lib/ui/kit/chat/kit_message.dart` (new).

## Public API

```dart
enum KitMessageKind { prompt, reply, thought, notice, marker }

/// A transcript piece that is words (STANDARDS STATE-16, KIT-41): a prompt
/// has no control row and opens its menu on long-press; a reply has no
/// frame; step boundaries are never drawn.
///
/// States: prompt (with or without attachments), reply, thought (working,
/// folded, open), notice (quiet, failed, with action, open), marker
/// (still, working) (KIT-12).
class KitMessage extends StatelessWidget {
  /// The person's words: an end-aligned surface2 bubble, radii 20/20/6/20
  /// (the small corner at the bottom end). No control row: long-press,
  /// right-click, Shift+F10 and the semantic custom actions open [menu].
  const KitMessage.prompt({
    super.key,
    required KitMarkdown this.body,           // the prompt text; the host passes selectable: false
    this.attachments = const <KitAttachment>[], // read-only chips under the text (KitComposerChips)
    this.time,                                // shown as a caption under the bubble when non-null
    this.menu = const <KitMenuItem>[],        // Copy message, Edit and resend, Revert to here…
    this.bubbleKey,                           // today's ValueKey('user-prompt-<id>')
  });

  /// The agent's prose: plain body text on the prose's start edge.
  const KitMessage.reply({
    super.key,
    required KitMarkdown this.body,
    this.bodyKey,
  });

  /// Folded reasoning. Title: [heading] when the agent wrote one, else
  /// "Thinking…" while [working], "Thought for {took}" or "Thought".
  const KitMessage.thought({
    super.key,
    required KitMarkdown this.body,           // role: secondary
    this.heading,
    this.working = false,                     // the kit's one word for "in progress" (README.md, decision D16)
    this.took,
    this.expanded,                            // non-null: controlled
    this.onExpansionChanged,
    this.thoughtKey,
  });

  /// Something that happened during a turn that nobody typed ("Context
  /// added", "Instructions updated", a compaction summary). It does not end
  /// the turn. One line; opens to [detail] when given.
  const KitMessage.notice({
    super.key,
    required String this.text,
    this.icon,                                // an AppIconography glyph; null: the info glyph
    this.technical,                           // appended in mono, LTR-isolated (a skill name)
    this.detail,                              // KitMarkdown shown when opened
    this.action,                              // one tertiary action on the line ("Compact again")
    this.failed = false,                      // text1 + neutral error glyph (LOOK-5 interim), never danger
    this.expanded,
    this.onExpansionChanged,
    this.noticeKey,
  });

  /// A quiet divider row for a switch (model, agent, project) or a running
  /// compaction: hairline, centred words, hairline.
  const KitMessage.marker({
    super.key,
    required String this.text,
    this.icon,
    this.working = false,                     // a KitStatusMark(working) before the words
    this.markerKey,
  });

  final KitMessageKind kind;
  final KitMarkdown? body;
  final List<KitAttachment> attachments;
  final DateTime? time;
  final List<KitMenuItem> menu;
  final String? text;
  final String? heading;
  final Duration? took;
  final bool? expanded;
  final ValueChanged<bool>? onExpansionChanged;
  final IconData? icon;
  final String? technical;
  final KitMarkdown? detail;
  final KitAction? action;
  final bool failed;
  final bool working;
  final Key? bubbleKey, bodyKey, thoughtKey, noticeKey, markerKey;
}
```

**Frozen behaviour.**

- **Prompt bubble.** Aligned to the end edge; wide by default (owner decision, 1.1.0): `bubbleWidth: KitBubbleWidth.auto` hugs its words and may grow to the available width minus `KitLayout.bubbleStartInset` (48), with no 85 % cap (`kit_message.dart:30-43`, `:285-294`); `compact` (the appearance option) caps it at `KitLayout.bubbleMaxShare` (0.85) and `full` fills the width; padding `space3` vertical, `space4` horizontal; fill `surface2`; corners `bubbleRadius` (20) except the bottom-end corner, `bubbleTailRadius` (6) (pre-wave tokens; VL §5 "20/20/6/20", mirrored under RTL). No border, shadow or glass (LOOK-20, LOOK-27). Attachments sit under the text inside the bubble as `KitComposerChips.attachments(items:, onRemove: null)` (read-only). `time` is a `caption` in `text3` under the bubble at the end edge, formatted with `MaterialLocalizations.formatTimeOfDay` (digits from the locale, COPY-30).
- **Prompt menu.** The bubble has no visible control (STATE-16). Long-press (touch) and right-click (pointer) open `showKitMenu(position: <gesture point>)`; Shift+F10 and the Menu key open it anchored; every item is also a `CustomSemanticsAction` (KIT-28, A11Y-5). An empty `menu` makes the bubble inert.
- **Reply.** `body` on the prose's start edge, full available width, no fill, no frame.
- **Thought.** A fold row: a 20 dp thought glyph, the title in `secondary`, and a chevron; opened, the body sits under it in `secondary`/`text2`, indented `space3` behind a one-pixel `hairline` stroke at the start edge (the work line's indent).
- **Notice.** One line at the prose's start edge: glyph (20 dp, `text2`), `text` in `secondary`, `technical` in mono, and `action` as a tertiary `KitButton` at the end of the line (it wraps under at 200 % text). With `detail` the line is a fold (same chevron as the thought).
- **Marker.** Hairline, centred icon and words in `caption`/`text2`, hairline. With `working`, a `KitStatusMark(state: working)` replaces the icon.

**Kit copy** (ARB, `kit` prefix, en + ar): `kitMessageYou` "You said" (prompt semantics prefix), `kitMessageThinking` "Thinking…", `kitMessageThought` "Thought", `kitMessageThoughtForSeconds` "Thought for {count, plural, =1{1 second} other{{count} seconds}}", `kitMessageThoughtForMinutes` "Thought for {count, plural, =1{1 minute} other{{count} minutes}}", `kitMessageActions` "Message actions" (the prompt menu's name), `kitMessageNoticeFailed` "Failed" (failed notice semantics prefix).

## States

Declared (KIT-12): **prompt** (plain; with attachments; with time), **reply**, **thought** working / folded / open, **notice** quiet / failed / with action / open, **marker** still / working. No loading, empty or error of its own: the host passes words it has (a reply still streaming is simply a growing `body`). No disabled. No working other than `thought.running` and `marker.working`, which describe the agent, not a tap.

## Tokens

- ThemeRoles: `surface2` (bubble), `text1` (prompt and reply text, failed notice), `text2` (thought, notice, marker words), `text3` (prompt time), `hairline` (marker rules, thought stroke), `accent` (only the working mark, via KitStatusMark).
- KitText roles: `body` (prompt, reply, via KitMarkdown), `secondary` (thought body and title, notice), `caption` (marker, time), `mono` (notice technical).
- KitTokens: `space1`–`space4`, `minTarget` (fold rows and the notice action), `smallIconSize` (20: glyphs, chevron), `hairlineWidth(context)` (§0.5 step 2 seam).
- **New (pre-wave, `_new-tokens.md`):** `KitTokens.bubbleRadius` = 20 and `KitTokens.bubbleTailRadius` = 6 (VL §5; LOOK-19 has no name for them; shared with KitQueuedMessage); `KitLayout.bubbleMaxShare` = 0.85 (only the `compact` appearance option) and `KitLayout.bubbleStartInset` = 48 (the default's free margin) (LAY-2 names layout widths only in KitLayout).

## Adaptive

- **compact:** by default the bubble hugs its words up to the turn width minus 48 dp (`auto`); with the Bubbles appearance option (`compact`) it takes up to 85 %; replies use the full width.
- **medium / expanded / large:** the same shares inside the conversation pane, which the host caps at `KitLayout.paneDetailMaxWidth` (700, LAY-5). Nothing changes by window class.
- **Fine pointer:** right-click on a prompt opens its menu at the pointer; the thought and notice folds show a hover step (surface step, KitTappable rule); text selection by drag through the host's `KitSelectable(mode: finePointer)`.
- **Keyboard:** a prompt with a menu is one Tab stop (focus ring on the bubble's shape, `accent`, `focusRingWidth`); Shift+F10 or the Menu key opens its menu. Folds are one Tab stop each; Enter and Space toggle. Replies are not Tab stops (links inside them are).

## Accessibility

- A prompt is one semantics container labelled "You said, {text}" plus attachment names, with the menu items as custom actions. A reply reads as its Markdown (headings, links).
- Folds are buttons with `expanded` semantics; the chevron is excluded.
- A failed notice's label starts with "Failed," (the word, not a colour).
- Nothing here is a live region (A11Y-3).
- 48 dp targets for folds and the notice action; the bubble is at least 48 dp tall when it has a menu.
- At 200 % text the bubble grows and wraps (never clipped); the notice action drops under the words; the marker's words wrap between its rules.

## RTL

- The bubble sits at the end edge, which is the left under Arabic, and its small corner mirrors to the bottom-left (`BorderRadiusDirectional`).
- Prompt and reply text direction follows each paragraph's first strong character (KitMarkdown), so an English prompt in the Arabic app reads LTR inside a left-aligned bubble.
- `technical` is LTR-isolated (KitText.mono). Chevrons are vertical and not mirrored; directional glyphs mirror through AppIcons.

## Motion and haptics

- A new prompt or reply appears without animation (the list owns arrivals; `KitAnimatedRows` is the host's choice for notices).
- Folds: the chevron turns (`KitSpin.chevron`, `KitMotion.standard`); the opened body appears at once and fades in over `KitMotion.quick`; no size animation in the scrolling list (MOT-5).
- The working mark is KitStatusMark's. Under `KitMotion.reduced` nothing moves and one `pump()` settles (G8x).
- Haptics: none (MOT-11; the send haptic belongs to the composer).

## Data safety and honest state

- The prompt shows exactly what was sent, including its attachments by name; nothing is summarised or hidden.
- A notice never ends a turn (STATE-16) and is never styled as the person's words.
- A failed notice says "Failed" in words, in `text1`, with a neutral glyph (LOOK-5, B2 interim); the raw error text goes behind the host's Details (`showKitTechnicalDetails`), not into the notice.
- Technical values (skill names, paths) are isolated LTR and are never translated (COPY-2).

## Depends on

- **kit-KitMarkdown** (C25).
- **kit-KitComposerChips** (tier 1c: read-only attachment chips and the `KitAttachment` value), **kit-KitMenu** (tier 1a: `showKitMenu`, `KitMenuItem`) and **kit-KitMotionParts** (tier 1a: `KitSpin.chevron`): edges added (README.md), no tier change.
- Existing: `KitStatusMark`, `KitButton.tertiary`, `KitAction`, `KitText`, `KitMotion`.
- Pre-wave seams: `KitTokens.hairlineWidth`, `focusRingWidth`, `KitLayout.paneDetailMaxWidth`.

## Tests required

`test/kit/kit_message_test.dart`:

1. Prompt: the bubble sits at the end edge (right under LTR, left under RTL); its decoration is `surface2` with bottom-end radius 6 and the others 20 (read from the render object); by default its width is at most 400 − 48 = 352 dp in a 400 dp host and a short prompt stays small; with `KitBubbleWidth.compact` at most 85 % (340 dp).
2. Prompt has no button in its subtree (no control row); long-press opens the menu with the given items; right-click (desktop capabilities) opens the same items; the semantics node exposes them as custom actions; an empty menu opens nothing.
3. Prompt with two attachments shows both names, without a remove control.
4. Reply: no decoration, full width, the KitMarkdown is present.
5. Thought: title rules (heading, "Thinking…", "Thought for 12 seconds", "Thought"); toggling shows and hides the body; controlled mode never toggles on its own.
6. Notice: text, technical in mono LTR, the action calls back once; `failed: true` prefixes semantics with "Failed," and paints no `danger` role.
7. Marker: two rules and centred words; `working: true` shows a working mark.
8. No `AnimatedSize` or size tween; under reduced motion one `pump()` settles.
9. Semantics: prompt label starts with "You said"; folds are buttons with `expanded`; no live regions; targets ≥ 48×48.
10. Desktop capabilities: Tab reaches the prompt and each fold in order; Shift+F10 opens the prompt menu; focus ring visible.
11. 200 % text at 320 dp, LTR and RTL: no overflow (G6).

## Galleries required

`test/goldens/kit/kit_message_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20):

- States at 412×915, dark and light: `kit_message_prompt`, `kit_message_prompt_attachments`, `kit_message_reply`, `kit_message_thought_folded`, `kit_message_thought_open`, `kit_message_notice`, `kit_message_notice_failed`, `kit_message_marker`.
- Default (a prompt followed by a reply) at 360×800, 915×412, 800×1280, 1280×800 (700 dp pane) and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL (an Arabic prompt and an English reply) at 412×915 and 1280×800, dark.
- About 30 PNGs.

## Non-goals

- The turn's footer, Copy and More (KitTurn).
- Tool calls and the work line (KitToolRow, KitWorkLine), error cards (KitNotice), request cards (KitRequestCard).
- Queued, unsent prompts (KitQueuedMessage).
- Deciding which server messages are prompts or notices (the host's `_isPrompt`, turn model).

## Open questions

None.
