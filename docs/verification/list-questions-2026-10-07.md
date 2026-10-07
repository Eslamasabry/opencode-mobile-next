# List questions API — 2026-10-07

Base: `4007ca48`; branch: `feat/list-questions`. Connection/Paseo changes only;
frontend integration remains with its owner. No chat is opened or resumed by
these APIs. No build, installation, commit, push or release was performed.

## Frontend contract

Read and listen on the **main** controller, including for Claude/Pi rows:

```dart
final question = connection.questionForFeedItem(item);
if (question != null) {
  final request = connection.questionIdentityForFeedItem(item, question);
  // Retain this identity with the displayed question, including across Undo.
  final pending = connection.isRequestPending(request);
  await connection.answerQuestionForFeedItem(
    item,
    answers,
    expectedRequest: request,
  );
  // Or: connection.rejectQuestionForFeedItem(item, expectedRequest: request).
}
```

`questionForFeedItem` returns a detached display snapshot or null when no live,
matching inventory exists. It performs no network read. Capture identity from
the exact returned object, not a reconstructed question or a fresh lookup at
send time. Use the same controller for identity, pending checks and replies.

Existing `questionIdentity(question)` and `answerQuestion`/`rejectQuestion`
also support these snapshots when passed `expectedRequest`. This lets the
existing question sheet consume the main controller and the captured request.
A mismatched row/request is an argument error; a resolved, replaced or stale
request is a no-op. Validation/transport failures for a current request still
surface to the caller.

**Frontend integration detail:** the list card's `_sendNow` in
`lib/ui/screens/chat/attention_card.dart` currently checks
`conn.questions.containsKey(id)`. Replace that check for list questions with
`conn.isRequestPending(capturedRequest)`. Phone questions deliberately remain
in the feed gateway inventory rather than the main controller's OpenCode map.
Use `item.identity` plus request ID for list draft/Undo/widget identity, so two
sources reusing an ID do not share UI state. An older identity must not be
silently replaced at send time. No `lib/ui/` files changed here.

## Routing and fences

- Phone rows use the existing directory-scoped feed gateway. Provider, directory
  and session must match a live feed row. The route captures the owning profile,
  source/gateway instance and opaque native request revision.
- Native revision identity includes the complete parsed wire request and answer
  mapping. Equivalent refreshes retain it; replacement, resolution, disconnect,
  deletion and source recreation invalidate it. Display copies cannot mutate
  the gateway's question/answer mapping.
- Main and side OpenCode rows use their existing controller only if its selected
  directory matches the row. An unprepared location returns null; this API does
  not silently retarget a connection. Replies use its existing transport and
  original pending-request checks without action-transport wake/resume.
- Concurrent decisions share a pending-reply slot keyed by source as well as
  request. A reply to one source cannot resolve another source's colliding ID.
- A native answer/rejection updates main-controller listeners and requests a
  fresh owning-feed read after delivery, without reading a timeline.

## Verification

Failing first: four new routing tests reached `questionForFeedItem` and failed
with `UnimplementedError` before its implementation. Subsequent coverage adds
ID collision, source closure, equivalent hydration/resolution, and OpenCode
routing. The eight new tests pass under the pinned SDK, serial machine lock.

Final checks, on this list-question candidate:

- Pinned `dart format --language-version=3.10`: changed Dart files formatted.
- `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <pinned-flutter> test
  --no-pub --concurrency=1 test/phone_agents_controller_test.dart
  test/paseo_native_questions_test.dart test/permission_edge_cases_test.dart
  test/connection_v2_test.dart`: **94 passed**, including all eight new tests.
- `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze
  --no-pub`: **No issues found**.
- `git diff --check`: clean. Every changed Dart file is below 1,500 lines.

This is focused regression coverage, not a full repository suite or device
qualification. Raw local logs: `/tmp/oc-list-questions-red.log`,
`/tmp/oc-list-questions-green.log`, `/tmp/oc-list-questions-regression.log`,
`/tmp/oc-list-questions-analyze.log`.
