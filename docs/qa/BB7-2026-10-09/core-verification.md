# BB7 core JVM verification — 2026-10-09

Base revision: `ea262e0ca931854a42d296e73ee031b3f8747b68` plus the uncommitted BB7 core implementation.
The [source manifest](core-source-manifest.txt) records the five production helpers
and five test files compiled in the final check. `BuiltinLinux` and Android
service/receiver integration are outside this pure JVM result.

## Results

| Check | Result | Evidence |
| --- | --- | --- |
| Initial default-deny implementation, tests written first | 5 tests, 4 expected failures | [Initial red](core-initial-red.txt) |
| Event/recipe/ownership/idle/budget helpers after implementation | 98 passed | [First complete green](core-guards-green.txt) |
| Remove only the idle-enabled guard | Exact admission test failed | [Idle guard red](core-red-idle.txt) |
| Remove only the active-ticket identity guard | Exact stale/copied-ticket test failed | [Ticket guard red](core-red-ticket.txt) |
| Remove only the cross-boot guard | Exact old-boot ownership test failed | [Boot guard red](core-red-boot.txt) |
| Add null-intent cleanup regression against default-deny helper | 7 event tests, 1 expected failure | [Pending-drain red](core-pending-drain-red.txt) |
| Restore all guards and implement exact pending-drain admission | 99 passed | [Final green](core-final-green.txt) |

Every guard-removal edit was restored before its final green check. The final
pending-drain test accepts only no pending marker or the same owner's identical
server-only receipt. It rejects a foreign owner, missing receipt, helper drain,
changed generation/nonce/boot/process identity and a prepared receipt.

## Check setup and limits

Commands ran with `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test --` and a watchdog
checking `MemAvailable` against 800 MB for each owned child process. No watchdog
fired. Kotlin compilation used `-J-Xmx256m -J-XX:MaxMetaspaceSize=192m`; execution
used `-J-Xmx128m`. Both used
`/home/eslam/.sdkman/candidates/kotlin/current/bin/` executables, JUnit 4.13.2
and Hamcrest 1.3 from the local Gradle cache. No Gradle or Flutter command was
launched for these checks.

The final compilation included each production and test file in the source
manifest, then ran `org.junit.runner.JUnitCore` with:

- `NativeServerRestoreEventTest`
- `NativeServerRecipeTest`
- `NativeRuntimeOwnershipTest`
- `NativeIdleStateTest`
- `NativeRecoveryBudgetTest`

Guard-removal checks used JUnit `Request.method` for only the named regression.
These results establish pure decision behavior and retained parser/ownership
invariants. They do not establish native runtime integration, Android broadcast
eligibility, reboot/update restoration or physical-device behavior. Those need
the separate integration and locked emulator evidence.
