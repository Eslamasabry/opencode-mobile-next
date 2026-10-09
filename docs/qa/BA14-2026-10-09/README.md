# BA14 — quiet reconnect

Branch `sol/ba-quiet-reconnect`, from `feat/genui-fe` at
`8ac793bfc0ee1eb3fbf71f54487a19ed2968c5eb` (includes BA12 and BD13).

Finish line: a short established-link drop keeps the turn running and the
composer busy while the gateway reconnects. A persistent outage becomes visible
after 15 seconds. On recovery, current status/history remains authoritative.
No prompt replay, transport rewrite, native change, or new automatic action.

## Contract and implementation

[Three-line UI contract](../../design/BA14-contract.md).
The shared controller owns the 15-second grace and cancels it on retirement,
disposal, or successful session reconciliation. Retry churn cannot extend it;
initial connection and credential failures stay visible. The snapshot preserves
actual transport reachability while suppressing transient connection presentation.
The chat reads the shared grace; its existing busy state and Stop remain intact.
Paseo, OpenCode 1 and OpenCode 2 retain their existing exponential backoff and
refetch paths. No missed event/prompt is replayed by this change.

Details reads BD13's narrow helper observation through the owning phone-agent
controller, bounded to two seconds and fenced against retired profiles. The
latest unexpected exit timestamp is retained across recovery and missing/old
APK diagnostics. Intentional exits do not replace it. The existing folded
Details shows timestamp/code and a possible cause only; a socket drop is never
asserted to be an Android kill. No credentials or raw native payload are added.

## Verification

All Flutter tests use the pinned 3.47.1 binary and the single machine slot:

```sh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- \
  /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --concurrency 1 <files> --reporter expanded
```

Initial failing-first evidence: [Paseo](red.txt), [state and Details](red-status.txt).
Before implementation, the four short/long-drop chat cases and bounded state
case failed because interruption/connection UI appeared immediately; Details
had no helper-exit record. A follow-up fresh-connect regression prevents the
grace from accidentally hiding an explicit initial connection.

The fake Paseo gateway stays offline for three seconds (retries at 1 and 3),
or remains down through 30 seconds (next successful retry at 31). Tests assert
running turn, no interruption text, busy composer, and hidden connection notice
during grace; the long outage shows a notice, and recovery reporting running
removes it. Existing running/idle reconnect tests retain authoritative completion.
OpenCode 2 gateway tests verify the same grace and busy/idle status refetch.
BD13 child-backend tests cover delegation, retention, intentional exits, and
late disposal. No wall-clock sleeps are used in these regressions.

Final results and any independently reproduced base failures are recorded below.
No full-suite, Gradle, emulator, build, signing, push, or live-agent/device proof.

### Final candidate

[verified-tests.txt](verified-tests.txt): **93 PASS**, zero failures/skips, one
locked process with these eight files:

| File | Passed |
| --- | ---: |
| `paseo_reconnect_state_test.dart` | 8 |
| `connection_status_test.dart` | 10 |
| `connection_status_presentation_test.dart` | 4 |
| `connection_v2_gateway_test.dart` | 7 |
| `connection_v2_requests_test.dart` | 18 |
| `reconnect_hardening_test.dart` | 6 |
| `file_size_ratchet_test.dart` | 2 |
| `kit_ratchet_test.dart` | 38 |

The BD13 child-backend case is run separately with `--plain-name
'BA14 child backend keeps scoped BD13 exit evidence after recovery'` in
`phone_agents_controller_test.dart`: [helper-test.txt](helper-test.txt).
The source/test manifest is [candidate.sha256](candidate.sha256).
Scoped analyzer output is [analyzer.txt](analyzer.txt). Pinned Dart formatting
uses language version 3.10; whitespace and contract-link checks also run.

### Revert proof and existing failures

[negative-control.txt](negative-control.txt): restoring all tracked production
changes to base reproduces **five** quiet-window failures (four chat/drop cases
and the state grace case); 13 other cases pass. Restoring BA14 makes them pass.
[fresh-connect-red.txt](fresh-connect-red.txt) records the follow-up regression
before narrowing the grace to actual drop/reconnect transitions.

The broader discovery batch, [final-tests.txt](final-tests.txt), had 206 PASS
and six failures. Three reconnect widget fixtures disposed their controllers
only in `addTearDown`, after Flutter checks pending timers; they now dispose in
the test body and all six reconnect-hardening cases pass above.

The other **three existing failures remain outside BA14**:

- `a resumed session is one row, with the title it had`: the backend title is null.
- `deletion closes auth, setup, host and feeds before ProfileStore`: 30-second timeout.
- `clearing saved sign-ins closes clients and asks for a restart`: 30-second timeout.

All three reproduce with **all tracked production changes and the phone-agent
test file restored to base**: [base-failures.txt](base-failures.txt),
[base.sha256](base.sha256). The command selects those three names with
`--name 'a resumed session is one row|deletion closes auth|clearing saved sign-ins'`.
The BA14 patch was restored afterward. The entire phone-agent controller file
and the full repository suite are **not claimed green**; no baseline/ignore or
production workaround was added for those failures.

Final result: **94 focused tests passed** (93 above plus the isolated BD13 case).
The analyzer is clean. Its only requested cleanup after the tests was adding
braces around an unchanged early return; the delivered manifest includes that
format-only cleanup. No ignores or baseline changes were introduced.
