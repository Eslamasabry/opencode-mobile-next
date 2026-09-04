# Composer and navigation upgrade

Date: 2026-09-05. Flutter 3.47.2 / Dart 3.13.2.

This pass reviews the chat, workspace, Activity, files/review, More and Settings
flows. Implementation concentrates on the editor, draft workflows, finding
tools, and controlling eligible running work.

## Twelve concrete improvements

| # | Improvement | Verification |
| --- | --- | --- |
| 1 | Full-width typing area in normal and compact layouts | 360 dp, short-window and 2.5x text layout checks |
| 2 | Stable editor, focus and selection through keyboard resize and run completion | Editor-state identity, selection and input-connection regression tests |
| 3 | Quieter model controls with Expand in the action row | Composer layout, desktop Enter, paste and accessibility checks |
| 4 | Context warning appears from 70%, keeping ordinary drafts uncluttered | Live-chat context-meter regression test |
| 5 | Draft text autosaves after 600 ms without typing | Stored preferences verified before navigation |
| 6 | Clear draft text with Undo that preserves newer typing and attachments | Clear, type again, Undo regression test |
| 7 | Search and reuse up to 30 distinct prompts from the conversation | Search, deduplication, append and large-text/keyboard interaction tests |
| 8 | Local image attachment thumbnails | Thumbnail and independent removal test; image paste tests |
| 9 | Compact grouped navigation in More | Row order/height, destination navigation and large-text tests |
| 10 | Search More by name and related terms | Alias matching, empty results and clear/recovery tests |
| 11 | Catalog model names and explicit “Default for new chats” scope in More | Catalog-name and scope-label test |
| 12 | Contextual Background action, Ctrl+B and 48 dp agent-switch targets | Runtime capability cache/reconnect, no-op handling, scoped HTTP routes and shortcut focus tests |

## Protocol behavior

OpenCode 1 requires a successful runtime capability response with
`backgroundSubagents: true`. Only running foreground `task` parts are eligible;
Bash, shell and PTY activity is excluded. `false` from the promotion endpoint
is a no-op. Discovery is cached until reconnect, repository replacement or a
location change, and unavailable discovery hides the action.

OpenCode 2 uses the session background endpoint for running foreground task
and shell parts. Existing shell metadata survives mapping, so work already
marked background is excluded. A 204 acknowledges a request and may be an idle
no-op; the UI reconciles the transcript and family status instead of claiming
that promotion occurred. Managed shell output/timeout/stop controls from issue
#62 remain separate work; this change does not add a shell-job history.

Ctrl+B is active only when promotion is available and the chat is the current
route. Dialogs and sheets retain keyboard ownership. Server background work is
distinct from Android's foreground notification service and has no promised
survival across a server restart.

## Iteration evidence

- The first composer implementation exposed a run-completion key that could
  remount the editor. The pulse wrapper was removed and editor identity tested.
- A stress test found prompt history overflowing with 2.5x text and an open
  keyboard. Its header, search and results now share a scrollable surface; the
  test scrolls to a result and taps it successfully.
- After refactoring the editor, a regression check verifies that Send and
  slash suggestions update from typing alone, without waiting for a server
  event.
- The installed Android picker initially left excessive space below one
  prompt. It now sizes to short lists and scrolls longer lists within its cap.
- The focused UI/accessibility group passed 72 checks before the final added
  stress/regression cases. The final composer group passed all 16 checks.

## Final local verification

- The complete Windows run executed 1,386 tests: 1,375 passed, with two app
  failures and nine known platform-dependent failures. The app failures were
  an overbroad “no TextButton anywhere” assertion and the localization ratchet.
  The assertion now checks the stopped message alone; new UI strings live in
  `app_en.arb`, without raising the localization baseline.
- All **82 final focused tests passed** after those fixes, including the
  affected chat state, composer, localization, More, background routes,
  shortcuts, draft persistence, accessibility and large-text suites.
- `flutter analyze --no-pub` passed. The final x86_64 debug APK built in 24 s,
  installed successfully on API 36, and was reloaded.
- Final APK SHA-256:
  `AF440CD043EFC10E26859ED79DDEC6B9A8CDEEDF11C981562E48D11B1B1247D4`.
- The nine platform-dependent cases are eight Linux theme goldens and the
  release script's Unix permission check under Windows Git Bash. The same
  cases passed on Linux for the preceding model-picker commit; CI checks this
  commit separately.

On the installed Android app, native computer use verified More's layout and
readable model name, Clear/Undo, prompt reuse, and preservation of the original
draft. The saved Android preferences contained both the original and appended
synthetic prompt before navigation. Both texts were still present after the
final APK update and process restart. The final prompt sheet fits one result
without the earlier empty space.

| Installed Android evidence | Screenshot |
| --- | --- |
| Composer before this pass | [Before](composer/02-chat-before.png) |
| Composer after restart, with restored draft | [Final composer](composer/10-composer-final.png) |
| More navigation and readable default model | [More](composer/06-more-after.png) |
| Draft actions | [Prompt tools](composer/07-draft-tools-after.png) |
| Prompt reuse after the sizing correction | [Final prompt sheet](composer/09-prompt-reuse-final.png) |

Live testing uses an isolated OpenCode 1
instance. OpenCode 2 behavior is checked against the captured contract and
HTTP/widget fixtures, not a live v2 server.
