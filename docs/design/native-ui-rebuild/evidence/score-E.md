# Scoreboard 2026-11-07, group E: `:feature:chat` (order 42)

Branch `native/s17-chat-screen`, from `native/screens-merge22` (`e6a10900e`). Flutter spec at `8302de537`. Every
number below came from this container: `./gradlew :feature:chat:desktopTest --tests '*.rb.o42.*'`, then
`rb_gate.py --order 42 evaluate` (goldens fetched from this repository with `rb_gate.py fetch`, tracker posts off).

## Pass counts

| | goldens | pass | rendered (measured) | not rendered |
|---|---:|---:|---:|---:|
| Scoreboard (89ec49e55) | 280 | 0 | 10 | 270 |
| Before, on screens-merge22 | 280 | 0 | 10 (guard findings baselined) | 270 |
| After, this branch | 280 | **0** | 8 | 272 |

No order-42 golden passes yet. `chat_find_open` is not measured any more: with the composer kept under Find (as
Flutter does), it picks up the kit composer field's small-target finding, which may not be baselined. It renders
again once F2 lands. What changed is how far the measured shots are from Flutter, and that the screen no longer
breaks the layout rules it broke:

| shot (light / dark) | scoreboard box % | after box % | what is left |
|---|---|---|---|
| `chat_loading` | 9.2 / 13.9 | 7.96 / 10.58 | the bar's title size (A8, `:ui:kit`); the dark skeleton fill |
| `chat_load_error` | 16.0 / 18.0 | 10.05 / 10.51 | the title size (A8); the page sits a little higher (the bar is taller) |
| `chat_transcript_turn` | 8.9 / 10.2 | 7.94 / 8.41 | the title size (A8); the composer (group F); the reply's work line, file chip and meta, which the scene does not supply yet |
| `chat_transcript_turn_1280x800` | 4.17 % box, blob 2,842 | box 3.1 / 3.3 (within 4 %); blob 2,137 / 712 | same as the phone shot; the pane itself now matches |

Side by side (Flutter left, native right): [loading](score-E/chat_loading_light.png),
[could not load](score-E/chat_load_error_light.png), [turn](score-E/chat_transcript_turn_light.png),
[turn at 1280](score-E/chat_transcript_turn_1280x800_light.png).

Layout guard baseline `native/config/layout/baseline/feature-chat.tsv`: 46 → 29 lines. Align baseline: 6 → 3.
`tool/qa/layout_baseline.sh` passes for both kinds. The "title at x 60, words at 16" finding is gone from all four
shots.

## Items

- **E1 done.** Transcript, composer and find bar are capped at 700 dp + 2 × gutter, centred
  (`ui/ChatStates.kt` `ChatPane`, `Modifier.chatPane()`).
- **E2 partial.** Find now sits above the request slot and the composer, and the composer stays, as in Flutter.
  Not done: match highlighting and the current reply's band. Both draw inside `:ui:kit-chat` parts this group does
  not own. Flutter: `KitFindMark` (accent at .18, .38 for the current hit) as `KitMarkdown`'s highlighter, and
  `KitTurn.highlighted`'s `_HighlightBand` (surface1, panel radius, painted `space2` outside the turn, cross-fading
  on `KitMotion.quick`; kit_turn.dart:772-800). Requested seams: `KitMarkdown(highlight: String?, activeHit: Int?)`
  and a `highlighted` flag on the turn's rows. `:feature:chat` already has `FindUi.currentKey` to pass.
- **E3 done.** `KitSkeletonTranscript` (two turns at the bottom), no composer.
- **E4 done.** A page state with `StatesUnpluggedScene` (140 dp), title, body, Try again, Report a problem
  (`SettingsRoute.ReportProblem`), and the details fold (the failure's redacted details, else its kind). The page is
  centred and has no composer. It is put together from kit parts because `KitPageState` has no `illustration` or
  `tertiary` slot. Proposal for `:ui:kit`: add both, as in Dart `KitStateView` (kit_state_view.dart:729-735).
- **E5 partial.** The title has no chevron (Flutter has no title tap; Rename stays in ⋯). At a window's root there
  is no Back, as Flutter's `KitTopBarExit.auto`, so the title starts on the 16 dp gutter. A pushed chat keeps Back.
  Two gaps are left, both in `:ui:kit`:
  1. The "Git" chip while the branch is unknown. Native `KitTopBarScope` draws `·` as a word of its own
     (KitTopBarScope.kt:44-46), which the alignment gate rejects ("start-edge `·` at x 78"). Flutter rides the dot
     at the start of the branch's own target (kit_top_bar_scope.dart:164). Fix: draw `"· $branch"` inside the
     branch's `ScopeWord`. After that, `ChatHeaderBar.kt` only needs
     `branch = state.branch ?: onBranch?.let { stringResource(Res.string.gitSheetTitle) }`.
  2. The title size. Flutter uses `headline` for every bar title. Native `TopBarTitle.role` picks `LargeTitle` for a
     title that is not a switcher, so the chat title grew once its chevron went. This is scoreboard A8.
- **E6 not done (`:ui:kit-chat`).** After C-003 the reply's actions sit at the end. `KitReplyFooter`'s reach
  (`replyActionReach`, 16.5 dp) puts More's 48 dp target 0.5 dp past the window's edge ("edge-tap More for this
  reply: x 365..412"). The finding stays baselined for the phone shots.
- **E7 started.** New gate cases: `chat_empty` and `chat_disconnected` (with the find case, `@Ignore`d until F2,
  E6 and the bar overlap land, because a baseline may not grow). They are ready to switch on. The other order-42
  sheets and pages (C03-C40) still have no gate case.

## Tests

- `ChatPaneStatesTest` (7 tests) and `ChatGateTest` (4 rendering, 3 ignored) pass. `:feature:chat:desktopTest
  --tests '*.rb.o42.*'`: 45 tests, 2 failures, 3 skipped. Both failures
  (`ActiveContextScreenTest` C32, `ChatTasksScreenTest` C01) also fail on untouched `screens-merge22`.
- Whole module, `:feature:chat:desktopTest`: 408 tests, 18 failed, 3 skipped. The same suite on untouched
  `screens-merge22` in a scratch worktree: 399 tests, 23 failed. No test fails here that does not fail there. Five
  base-only failures are `ChatGateTest`, whose Flutter goldens were not fetched in that worktree. The other 18 are
  shared, mostly pixel goldens (E20-S11..S14, RequestSlotGoldensTest, ChatCardSlotTest) and C32/C01.

## On-device checks (emulator)

1. Tablet or desktop window at least 1000 dp wide: open a chat. The messages and the composer form one centred
   column about 732 dp wide. On a phone they keep the full width.
2. Open a chat on a slow or stopped server. While it loads you see two grey placeholder turns at the bottom and no
   composer.
3. Stop the server and open a chat that is not cached. You see the cable drawing, "Couldn't open this conversation",
   Try again, Report a problem (opens Settings › Report a problem) and Details (opens the reason). There is no
   composer.
4. In a chat, open ⋯ › Find. The search field sits above the composer, the composer is still there, and Close
   brings back the plain composer with the draft unchanged.
5. The chat's title has no chevron and does nothing when tapped. Rename is in ⋯. Back is at the start of the bar.
