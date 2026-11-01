**Verdict: BLOCKED. These sections contain an automatic-resend path, unsafe recovery procedures, and contracts that violate the binding module graph.**

Read-only audit against Flutter revision `65e3f70a326aacadf96bb249c9d163c6544f042c`, plugin sources, cached Ktor 3.6.1/OkHttp 5.5.1 artifacts, and primary documentation. No builds or device tests were run.

1. **BLOCKER — Disabling connection retries does not prevent automatic mutation resends**

   **Where:** [04 §3.2, hardened OkHttp builder](/home/eslam/Storage/Code/wt/native-plan/plan/04-protocols-networking.md:187), §3.5 and P-N5.

   **Problem:** `retryOnConnectionFailure(false)` does not establish the promised “never resend a POST” invariant.

   **Evidence:** Both the cached 5.5.1 bytecode and [OkHttp’s pinned source](https://raw.githubusercontent.com/square/okhttp/parent-5.5.1/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt) show that `503` with `Retry-After: 0` produces a follow-up independently of that setting. The follow-up is suppressed for a one-shot request body, but the proposed builder does not make mutation bodies one-shot. P-N5 tests only a disconnected pooled connection.

   **Concrete fix:** Make every mutation non-replayable at the OkHttp boundary, including bodyless mutations, and explicitly prevent status-driven follow-ups. Add full-stack tests for `503 Retry-After: 0`, connection loss, and applicable `421` handling. Assert exactly one server-observed mutation.

2. **BLOCKER — R2 can automatically repeat already executed prompts and schedules**

   **Where:** [05 §9.4, restore pre-update data](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:1034), §7.9 H2; [04 §13.4, queue flushing](/home/eslam/Storage/Code/wt/native-plan/plan/04-protocols-networking.md:1480).

   **Problem:** Restoring the S0 preferences also restores old dispatch state. External server actions cannot be rolled back with those preferences.

   **Evidence:** A prompt can be unsent in S0, accepted after migration, and then restored as unsent by R2. Reconnection will flush it again. Schedule occurrence claims also live in the restored file: [ScheduledPromptClaims.kt](/home/eslam/Storage/Code/wt/oc_app-batch1/android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ScheduledPromptClaims.kt:7) uses them to distinguish new occurrences from duplicates. Restoring both an old schedule index and old claims can recreate a previously executed occurrence.

   **Concrete fix:** Preserve dispatch identities and occurrence tombstones outside the restorable snapshot. After R2, hold restored outbound work for reconciliation or explicit review; do not automatically flush queues or re-arm executable schedules. Add “send/fire successfully → restore → reconnect/reboot” journeys.

3. **BLOCKER — The published interfaces cannot compile under ADR §6.1**

   **Where:** [04 §11.2, `GatewayEvents`](/home/eslam/Storage/Code/wt/native-plan/plan/04-protocols-networking.md:1244); [05 §7.3, migration interfaces](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:562).

   **Problem:** Required types cross explicitly forbidden dependencies.

   **Evidence:** `GatewayEvents`, in `:core:domain`, exposes `StreamStatus`, defined in `:core:net`. The [ADR dependency table](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:298) forbids domain → net. Likewise, the gate screen directly consumes `MigrationActions` from `:core:data`, while shell → data is forbidden. Section 04 also places `OkHttpWebSockets.connect` below the module containing `WsSpec`, without defining a project-independent lower-level signature.

   **Concrete fix:** Move shared status values to an allowed lower module, expose migration presentation through an allowed engine API, and specify the primitive/external-library WebSocket boundary plus its adapter. Freeze signatures that compile against the actual dependency rules before feature work starts.

4. **MAJOR — The Flutter rollback guard can admit incompatible storage**

   **Where:** [05 §7.11, `breaksFlutterReadability`](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:901), §6.4, and [§9.3, R1 guard](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:1006).

   **Problem:** Native and Flutter consult different compatibility flags, and incompatibility is recorded too late.

   **Evidence:** R1 reads `oc.native.flutterReadable`; v2 and the step interface describe updating the journal’s `rollback.flutterReadable`. No corresponding durable update to the Flutter-readable key is specified. The step interface sets incompatibility **on completion**, allowing a crash during a partially completed format change to leave rollback enabled. The guard also allows a missing flag.

   **Concrete fix:** Define one durable compatibility barrier that both applications understand. Disable rollback and verify that write **before** any incompatible operation. Treat missing or uncertain compatibility as blocked once native migration has begun. Test process death before, during, and after the barrier.

5. **MAJOR — Backup publication and restore are not interruption-safe transactions**

   **Where:** [05 S0 steps 6–7](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:613), [§8.1 Start fresh](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:936), and §9.4.

   **Problem:** Several filesystem transitions have no defined restart behavior.

   **Evidence:** If S0 dies after publishing the final backup directory but before journaling completion, the retry attempts another rename onto an existing nonempty directory. R2 can die after moving live files but before resetting the completed journal, allowing the next launch to skip migration. Start fresh and R2 move `.xml` files without explicitly handling their live `.bak` twins; [AOSP loads `.bak` preferentially](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/app/SharedPreferencesImpl.java).

   **Concrete fix:** Journal backup publication, restore, and reset as resumable operations. Recognize and verify an already published backup; stage and verify replacements; handle authoritative `.bak` files; recover before opening any store. Inject termination between individual filesystem operations.

6. **MAJOR — R2 has no coherent behavior after Room becomes authoritative**

   **Where:** [05 §6.4](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:490), §9.4 and §9.5.

   **Problem:** R2 remains available while the S0 backup exists, including after v2, but restores only preferences and secrets.

   **Evidence:** After v2, engines use `oc.db` and ignore frozen source keys. R2 neither restores/quarantines that database nor defines how v2 completion markers are reset. It can therefore combine pre-update profiles and credentials with post-update database contents, or display restored preferences while retaining different authoritative queue/draft state.

   **Concrete fix:** Make R2 eligibility storage-generation-specific. Either retire it before Room activation or specify a complete recovery transaction covering database state, migration markers, source keys, and outbound-work quarantine.

7. **MAJOR — R1 does not preserve credential changes or deletions correctly**

   **Where:** [05 §9.3, R1 behavior and return](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:1022), M-D8.

   **Problem:** The described “Enter password” behavior is incorrect, and return reconciliation misses deletions.

   **Evidence:** A password changed in native leaves the old FSS value intact. Flutter [loads that old value directly](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/state/profiles.dart:325); it does not know that native changed or cleared it. A native sign-out can therefore reactivate an old credential during rollback. Conversely, `R1.return` enumerates existing FSS entries, so a secret deleted in Flutter cannot delete its native counterpart. Comparing against S0 also cannot reliably distinguish changes from successive rollback periods.

   **Concrete fix:** Record credential revisions and deletion tombstones across transitions. Have the rollback guard invalidate stale legacy entries before normal bootstrap, and reconcile both updates and deletions against a per-transition baseline. Extend J4 with native edits, sign-out, Flutter deletion, and two rollback cycles.

8. **MAJOR — CAS is not specified as atomic against ordinary commits**

   **Where:** [05 §2.2, `FlutterPrefsStore`](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:138).

   **Problem:** The file lock is specified for `compareAndSet`, while ordinary commits and their compensating writes have no equivalent exclusion requirement.

   **Evidence:** A normal writer can change a key between CAS’s comparison and commit unless both paths acquire the same lock. A failed commit’s restoration can likewise overwrite a newer successful write. Per-profile lanes do not serialize shared widget blobs or independently running Android surfaces.

   **Concrete fix:** Require every mutation—including blocking commits, CAS, migration writes, and failure compensation—to participate in one file-wide serialization protocol. Hold it through reading previous values, persistence, and compensation. Test CAS-versus-commit and failed-write-versus-successful-write races.

9. **MAJOR — The migration gate’s 20-second broadcast wait can cause ANRs**

   **Where:** [05 §7.1, component waiting and follow-up worker](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:525).

   **Problem:** Twenty seconds is not a safe common budget for receivers, workers, services, and tiles.

   **Evidence:** Android documents foreground-priority broadcast limits of 10 seconds on Android 13 and lower, and 10–20 seconds on newer versions. Cold startup counts toward that budget; `goAsync()` does not remove it. The proposed wait can exhaust the deadline before deferral. An expedited worker that waits indefinitely also has no defined behavior when migration remains blocked. See [Android’s timeout rules](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs).

   **Concrete fix:** Specify component-specific admission and deadlines. Persist deferred work and finish broadcasts well before the shortest applicable deadline. Define worker stop/retry behavior and immediate foreground-service obligations separately.

10. **MAJOR — SSE buffering permits phone-scale out-of-memory failures**

    **Where:** [04 §6.6, backpressure](/home/eslam/Storage/Code/wt/native-plan/plan/04-protocols-networking.md:822), §6.1.

    **Problem:** A message-count bound is ineffective with 16 MiB frames.

    **Evidence:** A 4,096-element channel can retain approximately **64 GiB of payload**, before decoded JSON and adapter objects. Even a small fraction exceeds an Android heap. There is also no executable suspension boundary specified between the non-suspending parser callback and suspending channel admission.

    **Concrete fix:** Bound queued bytes, use a small frame-count limit, and define cancellation-safe byte-budget acquisition. Specify a suspending parser delivery path. Test maximum-size frames with a deliberately stalled decoder and measure peak memory.

11. **MAJOR — Migration explicitly discards an existing photo-recovery source**

    **Where:** [05 M1 “Strays”](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:634), Appendix D.

    **Problem:** `flutter_image_picker_shared_preference` is ignored and later deleted even though it can hold the only recovered photo path.

    **Evidence:** Flutter’s [bootstrap recovery](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/state/prompt_photos.dart:304) calls `retrieveLostData()` when the app-owned pending record has no completed attachment. The plugin stores result paths in `ImagePickerCache`, independently of `oc.pendingPromptPhoto.path`. Keeping the cache directory does not recover that association.

    **Concrete fix:** Import the legacy picker result into the existing pending-photo record, verify its original conversation scope, and persist the recovered attachment before consuming the plugin cache. Add an upgrade fixture killed after the picker result is cached but before Dart receives it.

12. **MAJOR — Pulling Room into the switch release does not resolve the triggering performance failure**

    **Where:** [05 §6.4, retained source keys](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:490); ADR §9.8.

    **Problem:** The default response to an oversized-preferences startup failure leaves the oversized preferences intact.

    **Evidence:** Section 05 acknowledges that the cold-start gain arrives one release later. Android still loads the entire adopted XML. The escape route requires a coordinator amendment to the binding ADR, rather than providing an already resolved implementation contract.

    **Concrete fix:** Resolve the retention location before fan-out. Retain recoverable source bytes outside the active preferences file, with verified hashes and a journaled cutover, and amend the ADR accordingly. Require the post-migration candidate to pass the same startup threshold that triggered Room.

13. **MAJOR — The shared downloader omits existing redirect behavior**

    **Where:** [04 §3.10, resumable downloads](/home/eslam/Storage/Code/wt/native-plan/plan/04-protocols-networking.md:419), §3.5 and §14.3.

    **Problem:** The specified downloader has no path through HTTPS download redirects, while the transport treats redirects as failures.

    **Evidence:** Current voice URLs use Hugging Face `/resolve/…` endpoints, and [the existing downloader explicitly follows up to five HTTPS redirects](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/voice/model_download.dart:230). This behavior is absent from the replacement contract. The current reader also validates the exact resumed `Content-Range`; “206 appends” alone loses that check.

    **Concrete fix:** Specify bounded manual HTTPS redirects for credential-free artifact downloads, with a permit for each destination, while retaining disabled automatic redirects. Preserve exact range/length validation and final digest verification.

14. **MAJOR — The fixture recipe can target the owner’s connected phone**

    **Where:** [05 §10.4, emulator fixture commands](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:1137).

    **Problem:** Mutating `adb` commands are not bound to an emulator serial.

    **Evidence:** The emulator starts asynchronously, followed immediately by unqualified `adb wait-for-device`, installation, force-stop, and preference replacement. If a phone is already connected before the emulator appears, the initial commands can select it. The shown recipe also does not stop automatically after `adb root` fails.

    **Concrete fix:** Bind every Android and Maestro command to one verified emulator serial, assert the expected AVD before mutation, and stop on command failure. Make that targeting part of the executable recipe.

15. **MINOR — WebSocket handshake timeout is declared but not applied**

    **Where:** [04 §5.2, `WsSpec` and connector algorithm](/home/eslam/Storage/Code/wt/native-plan/plan/04-protocols-networking.md:594).

    **Problem:** `handshakeTimeoutMs` has no specified enforcement.

    **Evidence:** The derived client sets `readTimeout(0)` before receiving the upgrade response, and the algorithm provides no surrounding deadline. The TCP connect timeout does not bound a peer that accepts a connection and never sends HTTP headers.

    **Concrete fix:** Apply a cancellable deadline through receipt and validation of the `101`, cancel the underlying call on timeout, then use the long-lived socket policy. Test a server that accepts TCP and sends nothing.

**The three changes I would make first:**

1. Close every automatic mutation-resend path and prove one server-observed request under adversarial failures.
2. Redesign recovery around crash-safe storage generations and outbound-work tombstones.
3. Repair and compile the shared interfaces against the ADR dependency rules before freezing agent contracts.

