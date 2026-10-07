# Native agent questions: backend handoff, 2026-10-07

Branch: `feat/agent-native-q`, based on `feat/genui-fe` at `07a081c2`.

Finish line: native Claude/Pi/OMP questions and Claude plan approval use the
existing typed question flow, send the correct native answer, and participate
in conversation attention. No new question UI or browser implementation is
part of this backend change.

## Behavior and wire contract

Paseo `kind: question` requests become `PendingQuestion`, not a generic tool
permission. Claude `AskUserQuestion` uses full question-text answer keys;
Pi/OMP use their original header keys. Values are strings; multiple selections
and custom text join with `, `, matching Paseo's own question form. Empty optional
answers are `""`. The response is `agent_permission_response` with
`response: {behavior: "allow", updatedInput: {answers: {...}}}`. Reject sends
`behavior: "deny"`. No persistent permission is granted.

Claude `kind: plan`, `name: ExitPlanMode` becomes one required, closed-choice
prompt containing the plan text. `Approve` sends `behavior: allow` and
`selectedActionId: implement`; Paseo switches to `acceptEdits` for implementation.
`Keep planning` (or rejecting the question) sends `behavior: deny` and
`selectedActionId: reject`. The `implement_resume` action, which could restore
bypass permissions, is never selected.

The gateway emits `question.v2.asked/replied/rejected`, supports the existing
`QuestionGateway` methods, and advertises `legacyQuestionRequests: true` (the
existing capability name for this UI). Snapshot recovery and live events share
one bounded question inventory. Session removal, disconnect, location changes
and disposal retire pending state. Question changes update the feed's Needs you
projection without polling per token. Invalid question/plan requests never fall
back to generic Allow/Reject permissions.

## UI API

Use the owning `ConnectionController`, exactly as for OpenCode questions:

```dart
await backend.refreshPendingQuestions();
final question = backend.questionForSession(sessionID);
// Capture this when opening the form, not again when submitting.
final identity = backend.questionIdentity(question!);
await backend.answerQuestion(question.id, answers, expectedRequest: identity);
// Or:
await backend.rejectQuestion(question.id, expectedRequest: identity);
```

`answers` is `List<List<String>>`, in prompt order, containing selected labels
and optional custom text. `QuestionPrompt.optional` is the only added public
model property; it defaults to `false`, preserving existing OpenCode behavior.
For optional prompts the form must permit `[]`. All other required/custom/multiple
semantics remain those of `QuestionPrompt` and `QuestionChoice`. Plan prompts use
these same types; their choice descriptions disclose implementation with edit
permissions. Their full plan text remains available in `prompt.question`.

For a Conversations row, first resolve its source using the main controller:

```dart
final route = await connection.openChatFeedItem(row);
final backend = connection.backendForConversation(route.sessionID) ?? connection;
```

This prepares the correct backend; UI navigation is the caller's decision.
Then refresh/read the question and open the existing question sheet using that
backend. Preserve the returned route and request identity. A bare session ID
is insufficient to choose between OpenCode and a phone agent. The existing
backend-change hook refreshes the owning list after an answer/run transition.

No `lib/ui/` files were changed. The coordinator owns the optional-field form
adjustment and any Conversations list question slot. Phone-agent questions use
the existing per-conversation question/alert path; this change does not add a
new cross-profile attention aggregator.

## Evidence

Fixtures are sanitized projections of the coordinator's pulled Paseo 0.9.2
source, not a claimed fresh device capture. Exact provenance and normalization
references are in [the fixture notes](../../test/fixtures/paseo/README.md).
The natural-prompt emulator result supplied by the coordinator establishes the
incoming request kind; this patch still needs an owner-built APK for end-to-end
UI/device qualification.

Verification on 2026-10-07: **180 focused tests passed**, serial under
`tool/qa/machine_lock.sh`, using the pinned Flutter SDK and `--no-pub`. Files:

- `test/paseo_native_questions_test.dart` (20 protocol/security tests)
- `test/phone_agents_controller_test.dart` (28 controller/routing tests)
- `test/paseo_gateway_test.dart`
- `test/paseo_chat_feed_source_test.dart`
- `test/connection_v2_test.dart`
- `test/chat_question_card_test.dart`
- `test/kit_ratchet_test.dart`

Red runs preceded implementation: the initial 11 protocol tests and two new
controller tests failed on missing question mapping/answer routing and stale
optionality identity. Additional red tests covered `isOther`, revised prompt
replacement, and invalidating a malformed replacement. All now pass. One
intermediate mixed unit/widget run stalled on source disposal; removing an
unnecessary await of local broadcast cancellation fixed it, and the complete
controller file passes in the combined run.

All 11 changed/new Dart files pass pinned `dart format --language-version=3.10`
verification. This is focused coverage, not a full repository suite.
Repository-wide `flutter analyze --no-pub` passed with no issues after removing
one unnecessary test import. `git diff --check` is clean.
`COMMIT_MSG.txt` at the worktree root carries the coordinator's commit handoff.
No commit, push, APK build, device mutation, native change or release was performed.
