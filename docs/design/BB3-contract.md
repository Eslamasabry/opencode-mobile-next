# BB3 — native foreground-service restoration

Started 2026-10-08 after merge `cf3e4e86`. Status: required BB3 lifecycle acceptance passed on signed QA2202: OS service/server recovery without Activity, shared retry exhaustion, notification Stop intent, real live timeout callback, and prepared launch death. Focused Dart/native checks and removed-fix regressions pass. Additional stale-identity device fixture remains unqualified; native refusal is JVM-tested. Evidence and limits: [BB3 QA](../qa/BB3-2026-10-07/README.md). Native release remains a later coordinator task.

Finish line: an OS-recreated foreground service restores one canonical wanted server without Activity/Dart, after proven ownership drain and the existing durable retry admission; Stop and Android timeout revoke it.
Non-goals: force-stop/reboot auto-start, agent/engine resurrection, installer rollback (BB9), account authentication, UI/BA edits.

## API frozen for frontend integration

Existing channel `io.github.eslamasabry.opencode_mobile/builtin_linux`, method `startServer`, retains its script/port arguments. The manual authored `startBuiltinServer` path supplies an optional `restoreRecipe` map with exactly `version: 1`, `profileId`, `runtime: openCode1|openCode2`. Dart type: `BuiltinServerRestoreRecipe`. Legacy callers omit it and do not arm cold restoration. Automatic foreground/native retries reuse the already armed native recipe; they do not reset the budget.

The shared healing owner now awaits native migration/binding before an explicit authored Start sends its recipe. Deletion or owner replacement during that wait prevents dispatch. This ordering is needed for the first Start as well as later owner transfers.

Native arms restoration only for the current bound profile, fixed managed port4097 and typed runtime. Unbound/different-profile, disabled-policy or migration-ineligible metadata keeps the existing manual launcher runnable without arming cold restoration; status reports policyDisabled. Unsupported launch capability may fall back only before the gate releases, after its exact owned tree drains and wanted intent still holds. Persisted `oc.builtinRuntimeRecipe.<profileId>` contains schema/profile/runtime/package version/rootfs generation. It never stores script, password, provider key or account state. Native reconstructs the fixed canonical loopback command with the existing private password-file reference; initial authored Dart start writes the existing phone-context stamp, which cold restoration preserves. Unknown fields/schema, mismatched generations or failed storage deny restoration. Existing owned unbind/deletion hooks revoke scoped recipes; no new BA connection edit is needed.

`BuiltinLinuxStatus` adds typed `restorePhase` (`idle`, `waiting`, `restoring`, `unavailable`) and optional `restoreReason`. Older native builds with absent fields read idle/no reason. Unknown phase becomes unavailable; unknown reason maps to ownershipUnknown. No PID, nonce, script, raw error or account details appear in snapshots.

| State/reason | Plain copy and way forward |
| --- | --- |
| idle | Existing server state/copy applies. |
| waiting | The phone server stopped. Trying again soon. |
| restoring | Starting the phone server again. |
| unavailable / stopped | The phone server is stopped. Choose Start. |
| unavailable / policyDisabled | Automatic restart is off. Start the server when you need it. |
| unavailable / budgetExhausted | The phone server stopped after three restart attempts. Start it to try again. |
| unavailable / ownershipUnknown | The previous phone server could not be confirmed stopped. Close and reopen the app, then try Start. |
| unavailable / storageUnavailable | The restart setting could not be saved. Open setup and try again. |
| unavailable / componentRecoveryRequired | A component update needs recovery. Open setup and try again. |
| unavailable / systemTimeout | Android paused the phone server. Open the app to start it again. |

A restoration-aware start failure becomes `BuiltinLinuxException`, code `server_start_unavailable`, plain copy: “The phone server could not start. Open setup and try again.” No raw native exception is forwarded. New copy is a contract for Claude's UI/localization lane, not hardcoded UI changes.

## Launch authority and lifecycle

Native persists a prepared generation/nonce before creating the process. A trusted `setsid` stdin gate blocks before any server workload. Validate its bounded private header and kernel UID/ancestry/session/start-time/boot identity; commit complete launch ownership before releasing the gate. App death before acknowledgement closes stdin and cannot execute the server script. Missing capability or unproven identity cannot arm sticky restoration; its real availability remains a device qualification prerequisite.

Generic process identities accept Android kernel session0; the gated workload leader must still have session and group equal to its own positive PID.

Cold restoration inventories exact kernel identities and drains only proven owned server members, rechecking PID/start-time before each signal; proot is last. Other tracked runtime roots and their recorded/current kernel-proven descendants are excluded without signaling them. Root-dead unrecorded helpers, unknown/unreadable/escaped ownership and pending component recovery fail closed. Empty new-process maps never prove quiescence. Broader installer lifecycle ownership belongs to BB9.

Return START_STICKY only while a valid recipe is armed under wanted/not-person-stopped, owner/profile, durable v2 marker, current saved policy and the existing native budget. OS null-intent restart promotes foreground immediately, validates sticky authority before queueing, then starts one idempotent native restoration worker; no MainActivity/Dart launch. Other service kinds remain nonsticky. Persist generation so a new app process cannot reuse old admission/receipt tokens. Cold recovery reserves from the same three attempts and never counts as healthy manual Start. Stable uptime resets delay only.

Every cold restoration worker captures its supervision generation and schedule. It rechecks that ticket and the absence of a newer live server before selecting ownership, each exact signal, reservation and launch. Stale callbacks cannot clear a newer schedule or overwrite person Stop/Android timeout copy. Runtime identity generation stays separate from this captured supervision ticket.

Stop and timeout immediately revoke scheduled/in-flight release and sticky registration. The old identity snapshot and saved disarm/wanted changes share the short admission lock; delayed cleanup retains that stop revision even if saving fails and cannot overwrite or kill a newer manual Start. A separate persisted drain receipt survives process recreation. Notification Stop/timeout also drain proven recorded helper/engine children; server-only Stop leaves those runtimes alone. Timeout preserves a distinct system reason; it must stopSelf within Android's grace. Explicit Android force-stop keeps the package stopped until the person opens it.

## Required evidence

Pure recipe/ownership/revision tests must exercise malformed schema, credential/script fields, incompatible rootfs/package, missing budgets, generation reuse, gate release-before-save, PID/start/boot mismatch, unknown/escaped children and Stop/timeout races. Each production fix needs red proof with it removed then green restored.

After explicit GO: merged signed release APK on emulator-5554, exclusive emulator lock. Kill captured exact Android app PID with the Activity closed (not force-stop); observe OS-recreated FGS/new app PID, exact old-owned drain, real authenticated OpenCode2 recovery with no Activity and one budget spend. Repeat through exhaustion. Prove prepared gate never executes after app death, post-release orphan drain, stale/reused identity refusal, persisted Stop and actual timeout callback. Preserve app/data and runnable in-app server; BA5 shared-agent owner stays intact. BB6 account round-trip is skipped per coordinator.

Build/test and separate emulator holds were lifted on2026-10-08; all heavy checks and device sessions retain their respective shared locks. No native release, push, tag or PR. [Evidence](../qa/BB3-2026-10-07/README.md) records each completed check separately.

Device QA uses `am instrument --no-restart` only after a normal app process exists, and checks identical kernel PID/start-time plus detached instrumentation before and after each attachment. Preparation finishes before the host SIGKILL. Recovery must be observed before follow-up instrumentation; default instrumentation lifecycle is not accepted as OS process reclamation proof.
