# Issue 116 follow-up: visible Add an agent action

Finish line: New conversation shows Add an agent in the first row of the
agent grid; it opens the existing agent catalog and returns to the same
draft after setup and selection. With no installed phone agents, Add an
agent comes first.

Non-goals: a second installer, new credential handling, automatic sending,
changes to agent qualification, or publishing a replacement build.

## Scope

- `KitAddAgentCard` provides the plus action using kit surface, focus,
  semantics, and text wrapping conventions. Its label is localized in English
  and Arabic.
- The existing `NewChatScreen` places it inside `KitAgentGrid`, in the first
  row with installed agents and first when none are installed or text is large.
- The catalog uses the existing agent install/sign-in paths. Choosing an
  agent returns to the original New conversation draft; it does not open a
  second draft or submit the message.
- `widgetbook/lib/chat/new_chat.dart` renders the real screen over fake
  inventory in installed and empty states. `NewChatScreen` is removed from
  the coverage baseline because those use-cases now cover it.

## Evidence boundary

The owner reports that build 4502 installed Antigravity on their phone and
completed sign-in and chat. That is owner-reported evidence for the prior
integration, not a device run performed for this CTA change. The local
Widgetbook scenes and screenshots use fake state and do not qualify a phone
runtime or an authenticated model response.

This follow-up does not access the owner's phone or credentials.

## Local validation record (2026-11-05)

- Candidate base: `a223bd262`; follow-up is uncommitted in this worktree.
  Staging failed because shared Git metadata is read-only:
  `/home/eslam/Storage/Code/oc_app/.git/worktrees/issue-116/index.lock`.
  A preliminary add staged only `lib/ui/kit/kit_add_agent_card.dart`; the
  subsequent complete staging operation was denied. No commit was created.
  Prepared message: `/tmp/issue-116-add-agent-commit-message.txt` (includes
  `[skip ci]` and the required body/trailer). No alternative Git index or
  repository metadata location was used to bypass this restriction.
- English `Add an agent` / Arabic `إضافة وكيل` added; `gen-l10n` completed
  once after copy settled. Generated output is included.
- The original pinned Flutter cache and shared machine-lock directory are
  read-only in this session. Checks used a writable byte-preserving SDK copy
  at `/tmp/issue-116-pinned-flutter`, verified as Flutter 3.47.1, revision
  `91f8bd7507`, while holding the existing machine slot through a read-only
  descriptor. Shared SDK files and lock files were not modified.
- `flutter test --no-pub --concurrency=1 test/chats_new_chat_test.dart` was
  attempted but BLOCKED before loading any tests: `Failed to create server
  socket ... Operation not permitted`, address `127.0.0.1`, port `0`.
  This environment forbids the localhost socket Flutter's widget runner needs.
  No widget pass, return-flow runtime proof, layout measurement, or real
  screenshot is claimed.
- The kit motion registration `test/kit/kit_add_agent_card_test.dart` is
  included for the new part; it remains unrun under the same socket restriction.
- Golden execution was not attempted again after the same runner prerequisite
  failed. New dark golden target:
  `docs/qa/issue-116-antigravity/new-conversation-add-agent-dark.png`.
  That file has NOT been generated; existing affected start-screen goldens
  still need regeneration and visual inspection in a socket-enabled session.
- Focused widget coverage authored: EN/AR button semantics, no exclusive-choice
  state, target >=48 dp, first viewport, empty inventory, large text, and the
  real setup route returning to the same draft after a simulated install and
  user sign-in. No live accounts are used by these tests.
- Changed-file analysis: 17 changed Dart files passed with no issues. The
  final golden target and added kit motion registration also passed with no
  issues. The final RTL ordering assertions also passed analysis with no issues.
- `git diff --check`: passed. Pinned Dart format: 18 files passed.
  Commit-message check passed; the complete change could not be staged.
- Full preflight/full suite not run, per the requested focused-only boundary.
- Push, GitHub comments, signing, release, deployment and phone access: none.

## Resume checks

In a session that permits the widget runner's localhost socket, use the exact
pinned Flutter with the shared machine lock, then run only:

```sh
flutter test --no-pub --concurrency=1 test/chats_new_chat_test.dart test/kit/kit_add_agent_card_test.dart
flutter test --no-pub --concurrency=1 --update-goldens test/goldens/vision_start_golden_test.dart
flutter test --no-pub --concurrency=1 test/goldens/vision_start_golden_test.dart
```

Inspect every changed golden, including EN/AR and large text. The owner-feedback
dark scene writes the requested documentation screenshot directly. The source
is implemented, but this follow-up remains blocked on these local runtime
checks and the screenshot; it has not been delivered as a build.
