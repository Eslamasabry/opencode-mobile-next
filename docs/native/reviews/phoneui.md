Verdict: **REQUEST CHANGES** — 4 MAJOR, 2 MINOR findings.

1. **MAJOR — Confirmation uses stale reply state.** [ThisPhoneModel.kt:118](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/ThisPhoneModel.kt:118)  
   **Problem:** An update can dispatch while a reply is running; a restart can interrupt a reply without displaying its warning.  
   **Evidence:** `askUpdate()` checks `replyRunning` only before opening confirmation. `confirm()` dispatches unconditionally. Restart similarly captures `replyRunning` when asked. Open either confirmation while idle, begin a reply, then confirm: the command still runs. The update-refusal test starts with an already-busy server and misses this transition.  
   **Fix:** Recheck current facts immediately before dispatch. Refuse updates with `UpdateBusy`; require renewed restart confirmation when an active reply was absent from the original question. Enforce the update guard in the runtime lane too.

2. **MAJOR — Process confirmation can stop a different process after PID reuse.** [RunningModel.kt:149](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/RunningModel.kt:149)  
   **Problem:** The named process the person confirms is not necessarily the process stopped.  
   **Evidence:** `confirmStop` retains an old `ProcessRowUi`, while refreshes replace `rows` without invalidating that confirmation. Dispatch sends only `row.pid`; `PhoneProcessSource.stop(pid)` carries no process identity. If the original process exits and its PID is reused by another unprotected app-owned process, native ownership checks can accept the replacement. Native termination’s identity check begins with the process present **at dispatch**, not the one originally confirmed.  
   **Fix:** Carry a process identity, including start ticks, through readings, confirmation and stop commands. Validate it natively before signalling; dismiss stale confirmations after refresh.

3. **MAJOR — Older reads can overwrite the post-stop process list.** [RunningModel.kt:102](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/RunningModel.kt:102)  
   **Problem:** The list can resurrect stopped processes and restore obsolete counts or warnings.  
   **Evidence:** Timer refresh, every Retry tap, and the read following `confirmStop()` can call `load()` concurrently. Every completion publishes unconditionally. A slow pre-stop read can finish after the post-stop read and overwrite its newer result. `FakeProcesses.read()` never suspends, so the existing tests cannot expose reversed completion order.  
   **Fix:** Serialize reads and stops through one lane, or use read revisions and invalidate older reads when a stop is admitted. Add deferred reads that complete in reverse order.

4. **MAJOR — Cancelled commands leave controls permanently busy.** [ThisPhoneModel.kt:154](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/ThisPhoneModel.kt:154), [RunningModel.kt:152](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/RunningModel.kt:152)  
   **Problem:** Cancellation of an individual command leaves `working` or `stopping` set while its ViewModel remains alive. Subsequent commands are rejected.  
   **Evidence:** Both fields are cleared only after the suspending command returns normally. Core `attempt` explicitly rethrows `CancellationException`. Cancelling the existing fake server’s deferred gate instead of completing it reproduces the stuck state by inspection; the current test exercises only normal completion.  
   **Fix:** Clear operation state in `finally`, preserving cancellation propagation. Test command cancellation with the surrounding scope still active, then verify another action is accepted.

5. **MINOR — A completed unavailable settings read leaves an endless loading page.** [KeepRunningModel.kt:109](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/KeepRunningModel.kt:109)  
   **Problem:** `KeepAliveSettings.info()` explicitly permits `null`, but that completed result leaves `loaded=false`. The screen consequently shows loading with no explanation or Retry.  
   **Evidence:** `KeepRunningScreen` returns before rendering its body whenever `loaded` is false. `KeepRunningModelTest`’s final test asserts this permanent state instead of distinguishing unavailable data from an outstanding read.  
   **Fix:** Represent loading, unavailable and loaded states separately; finish loading and offer an in-place retry on unavailable data.

6. **MINOR — Three tests do not exercise behavior their names/comments claim.**
   
   - [RunningModelTest.kt:169](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/phone/RunningModelTest.kt:169): The lifted-limit check starts with the card already hidden. `shown(false)` and `shown(true)` occur without yielding, so `StateFlow` can conflate them and perform no returning read. The final null assertion still passes.
   - [RunningModelTest.kt:179](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/phone/RunningModelTest.kt:179): “A protected row leads back” only calls `model.back()`. It never interacts with a protected row.
   - [PhoneScreensTest.kt:149](/home/eslam/Storage/Code/wt/native-reviews/src-phoneui/native/feature/phone/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/phone/PhoneScreensTest.kt:149): “Opens Android’s screen” supplies exclusively non-opening steps and clicks nothing. Removing the screen’s `actions.open(...)` dispatch would leave this test passing.
   
   **Fix:** Begin the lifted-limit check with a visible card and yield between lifecycle transitions. Exercise the actual protected-row navigation affordance. Include an actionable Keep running step, click it, and assert its exact intent.

Static assertion-sensitivity checks also covered three tests: accepting `NeedsPassword` as healthy would fail `PhoneServerStateTest:53`; removing duplicate-command suppression would fail `ThisPhoneModelTest:68`; offering Stop on protected rows would fail `PhoneScreensTest:122`. No tests or builds were run, per plan 01 §10.3’s reviewer restriction. No files were changed.