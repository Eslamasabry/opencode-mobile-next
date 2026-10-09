# 2203 FQ3 and FQ9 failure investigation

Finish line: identify the failing protocol stage for the red OC1 and upgrade
rows, fix a demonstrated harness defect or give the app owner exact evidence.
Non-goals: a new build, full final-pass certification, account/provider changes,
or downgrading the normal 2203 app.

This branch starts from `feat/genui-fe` at `9c99e609a`. The investigated APK is
2203, source `ec778efa3de1730402f096fc126e16fd2e83264e`, SHA-256
`82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`.
See the [original final pass](../final-pass-2026-10-09/README.md); its failures
remain failures. Diagnostic harness changes are committed as `d4e658748`; the FQ9 generation guard is `47c387047`.

## What the old receipts establish

The [2196 OC1 checks](../FQ3b-2026-10-08/fq3-20261008b-cert.json) for model/permission/cards used `zai-coding-plan/glm-5.3`; the
[2203 checks](../FQ3e-2026-10-09/fq3-final-d9342b7e395e.json) used `opencode/big-pickle`. Both used owned servers and OC1 1.18.32.
They are not a controlled before/after app comparison. FQ3 talks directly to
its owned runtime through its own HTTP/SSE client. It bypasses app connection
recovery, cold-start model display, connector-card widgets and step timeline.
The app still supplies the runtime and its shared configuration; those remain
possible indirect dependencies.

The old `oc1_prompt_error` discarded the assistant error category/status and
model-switch stage. Permission `timeout` discarded whether inference failed,
the tool was never requested, or an expected event was missing. Added safe
facts retain those distinctions and the actual model pair. They never export
messages, HTTP bodies/headers, commands, credentials or raw exception text.
Pass criteria and failure codes are unchanged; there is no retry/fallback model.

FQ9 failed before `historySeeded`, fixture creation or candidate installation.
Its pre-fixture calls are OC1 health and session status. The generic
`protocol_response_invalid` hid HTTP errors, non-JSON responses and transport
failures. Added allowlisted stage/kind/status metadata exposes those distinctions
without copying response contents. Fixture backing remains `files/projects`.

## Focused checks

All checks use `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- ...` and the
pinned Shorebird Flutter/Dart toolchain.

- `flutter test --no-pub --concurrency=1 test/fq3_oc1_failure_facts_test.dart`: 6 passed.
- `flutter test --no-pub --concurrency=1 test/fq3_oc1_test.dart test/fq3_wire_test.dart`: 29 passed.
- Final `python3 -m unittest tool.qa.fq9.test_protocol_failures tool.qa.fq9.test_background tool.qa.fq9.test_ports tool.qa.fq9.test_run tool.qa.fq9.test_fixture`: 100 passed. The wrong-generation regression [failed against the pre-fix runtime](fq9-red.txt), then passed after restoration of the fix.
- `dart analyze tool/qa/fq3/oc1.dart tool/qa/fq3_certify.dart test/fq3_oc1_failure_facts_test.dart`: clean.
- Pinned Dart formatting and `git diff --check`: clean.

## Device diagnosis on normal 2203

Every device session used the shared exclusive emulator flock. Only
`emulator-5554` was accessed. No APK was installed, no app data was cleared, and
no setting/provider/account was changed. FQ3 started and removed only its owned
protocol servers and synthetic sessions. Its cleanup receipts report no error.

| OC1 phase | Exact observed failure | Receipt |
| --- | --- | --- |
| Model switch | Initial Big Pickle reply succeeded. The catalog-selected alternative `opencode/ling-3.0-flash-fin-free` failed with `APIError`, HTTP 400, non-retryable, at `alternative_reply`. | [model](../FQ3e-2026-10-09/fq3-bc2203-diagnosis-a-opencode-model.json) |
| Permission allow | `APIError`, HTTP 403, non-retryable before `permission.asked`; the timeout reflected assistant inference failure. | [allow](../FQ3e-2026-10-09/fq3-bc2203-diagnosis-a-opencode-allow.json) |
| Permission deny | Same HTTP 403 before `permission.asked`. | [deny](../FQ3e-2026-10-09/fq3-bc2203-diagnosis-a-opencode-deny.json) |
| Cards | Big Pickle `APIError`, HTTP 403, non-retryable at `cards_tool_reply`, before tool/card assertions. | [cards](../FQ3e-2026-10-09/fq3-bc2203-diagnosis-a-opencode-cards.json) |

These reproduce upstream inference failures through the standalone protocol
harness; no app UI/reconnect regression has been demonstrated. HTTP status alone
does not identify the provider's detailed policy or request rejection reason.
The model-switch harness also chooses its alternative from catalog order;
"connected" does not establish that every advertised model can execute.
The failures remain red, and these observations do not certify app UI behavior.

The [initial read-only FQ9 probe](fq9-readonly.json) verified the installed 2203
hash/signer and reproduced invalid OC1 responses while `/api/info` identified
OC2 2.0.10. The [guard check](fq9-generation-guard.json) then confirmed the
original response shape: OC1 health returned HTTP 200 with invalid JSON. The
new guard identified `expected: opencode1`, `observed: opencode2` and returned
`app_managed_engine_unavailable` before creating a project/session. It does not
switch/start a runtime, use an owned replacement, or seed the wrong engine.
Auth/transport failures are not converted into generation mismatches.

This is a reproduced explanation for the generic FQ9 failure, not retrospective
proof of the precise response in the original 2202 attempt, which discarded it.
For the next real upgrade pass, the *already installed reviewed baseline* must
have its app-managed **OC1** server running and idle before FQ9 seeds its fixture.
Arrange that through the app before the reservation; do not downgrade normal
2203 to manufacture an upgrade baseline. Retention and manual Keystore/history
checks remain unqualified until an actual suitable baseline/candidate run.

The optional same-APK GLM-5.3 comparison was queued but cancelled while still
waiting behind the next lane's reservation. The waiter and its parent were
stopped and verified to have no device child before termination; it made no
device/model calls. Historical GLM results are not current qualification.

## Reproduction and handoff

The four diagnostic phases used this command from the branch; the shell held
one reservation across all four phases. Each phase records its harness revision.

```bash
exec 9>/home/eslam/Storage/tmp/oc-emulator.lock
flock -w 3600 9
export OC_TEST_SLOTS=2
for phase in model allow deny cards; do
  /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart \
    run tool/qa/fq3_certify.dart --phase --engine opencode --case "$phase" \
    --run-id fq3-bc2203-diagnosis-a --model opencode/big-pickle \
    --inherited-emulator-lock-fd 9
done
```

Use a new run ID for a future attempt; the command documents this completed
attempt, not permission to overwrite it. Final FQ9 read-only guard verification
confirmed the same normal 2203 hash and signer after all four FQ3 phases.
There are no remaining BC device waiters.

Coordinator action: choose reviewed working inference models (including an
actual executable alternate) for the next FQ3 pass; do not infer support from
catalog membership. For FQ9, prepare the correct managed OC1 baseline runtime
before the future upgrade reservation. No app-source fix is proposed from
these observations, no final-pass row was promoted, and no build/push occurred.
