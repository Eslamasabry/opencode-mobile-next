# BB5 original primary failure — offline evidence limit

Conclusion: **owner rebinding is reproduced on the host, but the original device
primary cannot be uniquely recovered from the retained receipts.** No device or
Gradle/APK work was performed for this investigation. The emulator remains
released to BC/BA; BB5 remains default-off.

| Observation | What it establishes | What it does not establish |
| --- | --- | --- |
| Saved first-stage fixture has four fields, no `members` | The scenario passed baseline admission and saved its restoration state; it did not complete runtime preparation or enter the actual background wait | Which earlier operation threw |
| Newly created QA budget, recipe and ownership keys remained until scoped restoration | Staging/binding and the QA native gate reached the durable recipe write in `commitServerGate`; generic restoration preserves new QA-idle keys and does not create them | Successful permit flush, workload health or stable ownership after that write |
| QA helper home absent, including before its explicit deletion could occur | Helper startup did not leave a prepared home; this supports failure before helper process creation | Whether health, owner validation, helper admission or home preparation refused |
| Capabilities afterwards report a memory recipe, matched enabled binding, idle restore phase and no restore reason | A coherent native binding existed at the later probe | Whether that binding belonged to the fixture when the primary threw; capabilities do not expose its profile identity |
| Writer ticket absent at the later probe | No durable writer ticket remained then | Absence of a transient writer during startup |
| Native cleanup reports only `bb5_cleanup_fixture_invalid` | The original runner discarded the useful distinction between path/size/schema checks and could replace the primary exception in `finally` | The original primary code or the exact invalid cleanup predicate |

The foreground Flutter controller continues to own the saved phone profile while
the private fixture binds `qa_bb5_idle`. `BuiltinServerRecovery.check` binds that
saved profile on each native-authority check **before the health probe**, even
when the server is healthy. `PhoneServerHealing` can expedite its ordinary45s
checks to5s when stopping the real server unsettles the connection. The fixture
then calls `requireOwner` after its own health wait. A check in that interval
can produce `bb5_fixture_owner_changed`; a later check can instead stale the
helper's admission ticket. The saved evidence has neither the timing nor the
primary result to distinguish these from a20s health timeout or another startup
failure. No timer defect is established because the background wait was not
entered.

The new focused test exercises the **actual Dart controller** with a native
bridge double: start healthy with the saved profile bound and a spent budget;
inject the fixture's native-only owner switch; perform a foreground check. It
rebinds the saved profile, retains readiness and the budget, and issues no restart
or manual reset. Removing the real controller's bind and merely reading the
budget fails at `Expected: 'phone'; Actual: 'qa_bb5_idle'`; the production source
is restored byte-for-byte. This protects legitimate product ownership instead
of weakening it to admit a private fixture.

Verification:15 affected Dart tests pass after restoration; the focused binding
mutation fails at an assertion; focused analyzer is clean. Logs: [restored](owner-rebind-restored.txt),
[red](owner-rebind-red.txt). The runner repair at `ab2d16d81` will preserve the
primary fixed code and separate cleanup code on the next run; its Android build
and the post-BC/BA locked device rerun remain pending the coordinator's window.
