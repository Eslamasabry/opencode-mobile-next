# BB full-suite follow-up at cd5b97c4

Branch: `sol/bb-suite-fix`, based on `feat/genui-fe` at `cd5b97c4d`.

All eleven reported failures were test-double omissions. Each failed only the
widget-test pending-timer invariant: the two fake Linux objects inherited
BB5's real `observePhoneAgentWork` implementation, which creates an eight-second
native-call timeout. There were no failed behavior expectations in either
isolated red run. The setup finisher lifetime, explicit Stop, launch start,
reconnect and backoff assertions all remain unchanged.

Both fakes now complete the observation locally, as the existing healing tests
do. Each override has a one-line explanation. No production or UI code changed;
native idle admission and its bounded heartbeat remain intact.

## Isolated checks

The pinned Flutter was used. Each file ran separately through
`OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test`, with `--no-pub --concurrency=1
--reporter expanded` and the owned-process memory watchdog.

| File | Before | After |
| --- | --- | --- |
| `test/builtin_server_autostart_test.dart` | [10 timer failures](autostart-red.txt) | [10 pass](autostart-green.txt) |
| `test/builtin/setup_finisher_lifetime_test.dart` | [1 timer failure](finisher-red.txt) | [1 pass](finisher-green.txt) |

The failing runs were captured on the unchanged candidate before adding either
override. Formatting and `git diff --check` pass. Targeted analysis of both files
passed with no issues, recorded in [analyze.txt](analyze.txt).

No full-suite, APK, emulator, signing, push or release operation was run.
The unrelated untracked `docs/design/BB8-contract.md` remains untouched.
