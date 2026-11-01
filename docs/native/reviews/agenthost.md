Verdict: REQUEST CHANGES — 7 MAJOR, 1 MINOR.

1. **MAJOR — A dead launcher is treated as a drained process tree.** [AgentProcesses.kt:258](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/AgentProcesses.kt:258)

   `stopAgentProcess` returns immediately when the root has exited. PRoot can die while its tracees survive; the existing runtime explicitly documents that scenario. `blockAgentProfile` consequently reports drainage complete, allowing `deleteHome` to erase account files while surviving agents still run. Later launches also purge dead-root entries from the ownership map. This violates plan 17 §1.2 and B-STATE-056.

   **Fix:** retain descendant kernel identities throughout each agent launch and confirm their drainage independently of root liveness. Refuse deletion when ownership or drainage cannot be proven; never recover ownership from a potentially reused root PID.

2. **MAJOR — An older Stop can kill its replacement helper.** [PhoneAgentHost.kt:181](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/PhoneAgentHost.kt:181)

   Stop removes `children[profile]` under the monitor, releases it, drains the captured child, then calls `stopService` **by name**. During that drain, another start can admit and register a new helper under `agent-host.<profile>`. The older Stop then targets the new helper. Incrementing `generations` does not protect the name-based stop.

   **Fix:** make service removal conditional on the captured process identity/generation, or retain a stopping admission until the complete stop finishes. Add a latch-controlled stop/restart race test.

3. **MAJOR — Readiness can return success after Stop invalidates the start.** [AndroidAgentHost.kt:72](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/AndroidAgentHost.kt:72)

   The lifecycle ticket protects `host.start`, but nothing revalidates it after the suspending health checks. A health request can begin, Stop can drain the helper, and the delayed successful response can then produce `Outcome.Ok`. A replacement listening on the same port can likewise satisfy the old request, which returns the old version/owner facts. This breaks the pending-read fencing required by B-LINUX-137 and plan 03 §17.5.

   **Fix:** carry the exact helper generation/process identity through readiness and revalidate it after every suspension and immediately before returning success.

4. **MAJOR — Helper exits never update the advertised status flow.** [AndroidAgentHost.kt:47](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/AndroidAgentHost.kt:47)

   `state` changes only during `ensureRunning` and explicit refreshes after commands. The service watcher calls `services.exited`, but has no connection to this flow. After Android kills the helper, observers can indefinitely retain `running=true`. Plan 17 §1.2 requires status pushed on every native change.

   **Fix:** publish identity-scoped service-exit and runtime-stop events into the host’s state owner. Test that terminating the helper changes the flow without another host command.

5. **MAJOR — Platform availability incorrectly depends on installation.** [AndroidAgentHost.kt:44](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/AndroidAgentHost.kt:44)

   The property claims to equal `PlatformCapabilities.builtinLinux`, but returns `processes.installed`. Core’s Android capability is true independently of rootfs installation. A fresh supported phone therefore advertises an unavailable platform instead of an available installation path, violating the seam’s explicit contract.

   **Fix:** derive availability from platform support and expose installation separately. Cover a supported Android host with `installedNow=false`.

6. **MAJOR — The health check can exceed its budget indefinitely.** [PaseoHealth.kt:27](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/PaseoHealth.kt:27)

   The response body is drained without a byte cap or total deadline. `readTimeout` bounds an individual blocked read; a response supplying bytes regularly can keep this loop alive indefinitely. `ensureRunning` checks its 30-second deadline only after `answers` returns, and coroutine cancellation does not interrupt this blocking loop. The Flutter implementation being replaced separately bounded body drainage.

   **Fix:** enforce a total monotonic deadline, bound or omit body drainage, and disconnect the request on cancellation. Exercise the production health implementation with an unfinished/trickling response; the current tests replace it with a Boolean function.

7. **MAJOR — Stale-lock cleanup follows symlinks and deletes their targets’ contents.** [AgentProcesses.kt:125](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/main/kotlin/io/github/eslamasabry/oc/android/agenthost/AgentProcesses.kt:125)

   Checking only that the canonical parent is named `claude` does not establish ownership. For example, `.oauth_refresh.lock -> backup` inside a Claude directory passes this condition; `listFiles().forEach { it.delete() }` deletes files inside `backup`, then removes the link. A linked Claude ancestor can similarly redirect cleanup into another profile.

   **Fix:** validate the complete managed path without following links and remove only a verified lock directory. Preserve unexpected contents. Add sentinel tests for linked locks and linked ancestors; the stricter `PhoneAgentAuthLock` already demonstrates the intended boundary.

8. **MINOR — The deletion regression test never exercises failed drainage.** [AndroidAgentHostTest.kt:163](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/test/kotlin/io/github/eslamasabry/oc/android/agenthost/AndroidAgentHostTest.kt:163)

   The test injects `forgetCommits=false`. That failure occurs **after** `erase(target)`, and the test never checks file preservation after the first failed call. It therefore passes while account files have already been removed and does not prove its named B-STATE-056 behavior.

   **Fix:** inject a process-drain failure, seed credential sentinel files, assert that the first failure preserves them, then permit drainage and verify successful retry.

Three existing tests were checked statically for mutation sensitivity:

- [AgentProcessesTest.kt:51](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/test/kotlin/io/github/eslamasabry/oc/android/agenthost/AgentProcessesTest.kt:51): removing Codex-home preparation makes the child exit `9`, failing the expected `0`.
- [AgentProcessesTest.kt:92](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/test/kotlin/io/github/eslamasabry/oc/android/agenthost/AgentProcessesTest.kt:92): accepting a paused/stale ticket defeats the expected exception and empty-launch assertions.
- [AndroidAgentHostTest.kt:62](/home/eslam/Storage/Code/wt/native-reviews/src-agenthost/native/android/agenthost/src/test/kotlin/io/github/eslamasabry/oc/android/agenthost/AndroidAgentHostTest.kt:62): bypassing readiness polling fails both the three-probe assertion and the elapsed-time assertion.

Reviewed `997da66b..4e145e29`. Core contract usages were inspected statically. No files changed; no builds, tests, or mutation runs were executed.