**Verdict: CHANGES REQUIRED — privacy, lifecycle, and network-contract defects remain.**

Static, read-only review of `6b9a2b21..747651a1`. No builds or tests executed.

1. **MAJOR — Sanitizing the default crash handler does not prevent Android from logging secrets.**  
   **Location:** [NativeCrashStore.kt:48](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/NativeCrashStore.kt:48)  
   **Problem/evidence:** Android installs a separate crash *pre-handler*. It logs the original throwable, including messages, causes, stack and thread name, before this default handler receives it. An uncaught `IOException("Bearer …")` therefore reaches the crash log despite the replacement throwable at line 55. This undermines SI-2 and the claimed B-SEC-043 protection. The test at `NativeCrashStoreTest.kt:179` invokes the handler directly, bypassing actual uncaught-exception dispatch. [Android’s RuntimeInit source](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/com/android/internal/os/RuntimeInit.java) establishes both the pre-handler registration and original-throwable logging.  
   **Concrete fix:** Sanitize failures before they escape application execution boundaries; replacing the default handler alone cannot provide this guarantee. Add an isolated emulator crash test that scans the crash log for credential, path and address canaries.

2. **MAJOR — Visibility bookkeeping can remain true after every activity stops.**  
   **Location:** [AndroidDeviceFacts.kt:48](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/AndroidDeviceFacts.kt:48)  
   **Problem/evidence:** One Boolean represents all pending configuration replacements. Two started activities followed by two configuration stops leave `started == 2` and only one replacement credit. Starting both replacements raises the count to three; stopping both leaves it at one. The existing multi-activity test exercises only one configuration stop. Independently, `close()` retains the counter while unregistering callbacks: closing while visible, stopping the activity while closed, then restarting tracking retains an unmatched start. `appVisible` consequently misclassifies a hidden app, affecting the lifecycle and hidden-work policies in plan 13.  
   **Concrete fix:** Use the `ProcessLifecycleOwner` source specified by the domain contract and plan 13 §11.1. If retaining manual tracking, track activity identities and replacement lifetimes, and reconcile state across stop/start. Cover both sequences above.

3. **MAJOR — Asynchronous registration can miss the activity whose visibility must be reported.**  
   **Location:** [AndroidDeviceFacts.kt:120](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/AndroidDeviceFacts.kt:120)  
   **Problem/evidence:** `start()` always posts registration, even on the main thread. Calling it during an activity’s `onCreate` allows that activity’s `onStart` to finish before registration runs. Activity lifecycle callbacks do not replay the missed start, so `appVisible` remains false throughout the visible screen. Tests explicitly drain the looper before starting their activity and miss this ordering.  
   **Concrete fix:** Observe an authoritative lifecycle state that can be read on attachment. At minimum, register synchronously when already on the main looper and ensure initialization precedes activity launch; test startup without the artificial intermediate `idle()`.

4. **MAJOR — The network reading discards the Wi-Fi fact required under VPN.**  
   **Location:** [AndroidNetworkStatus.kt:36](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/AndroidNetworkStatus.kt:36)  
   **Problem/evidence:** VPN forces `transport = Unknown`, and the returned shared state has no independent `onWifi` value. Tailscale over Wi-Fi and Tailscale over cellular therefore lose the distinction required by plan 04 §6.4 and plan 11 §10.4’s Wi-Fi-only policy. Unit-report deviation D7 acknowledges the missing core field; it leaves the required behavior unimplemented.  
   **Concrete fix:** Coordinate the shared-model extension and preserve `caps.wifi` independently of transport classification. Add `WifiOnlyUnderVpnTest` cases for VPN over Wi-Fi, VPN over cellular, and unknown capabilities.

5. **MINOR — Repeated network `start()` replaces the registration and resets observable state.**  
   **Location:** [AndroidNetworkStatus.kt:127](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/AndroidNetworkStatus.kt:127)  
   **Problem/evidence:** Every start invokes `stopOnMain()`, publishing `Unknown`, unregistering the current callback, and registering another. This contradicts the advertised idempotence and can appear to consumers as a default-network change, triggering unnecessary reconnects. The test at `AndroidPlatformRobolectricTest.kt:54` checks only the final callback count; unregister/re-register still leaves exactly one callback.  
   **Concrete fix:** Return immediately when already registered. Test callback identity, registration count, and state stability when starting an already-online monitor.

6. **MINOR — Queued thermal callbacks can mutate state after close.**  
   **Location:** [AndroidDeviceFacts.kt:169](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/AndroidDeviceFacts.kt:169)  
   **Problem/evidence:** The listener writes unconditionally. Android queues delivery through an executor; removing a listener does not cancel an already-queued runnable. A callback queued behind `stopOnMain()` can therefore update the closed instance. The test removes the listener before generating another event, so it does not exercise pending delivery. [PowerManager’s implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/os/PowerManager.java) shows the queued listener invocation.  
   **Concrete fix:** Guard delivery with the active listener identity or generation, invalidated before removal. Queue an event, close, then deliver the captured callback and assert no state change.

7. **MINOR — The RAM test does not prove that physical RAM is read.**  
   **Location:** [AndroidPlatformRobolectricTest.kt:130](/home/eslam/Storage/Code/wt/native-reviews/src-plat0/native/android/platform/src/test/kotlin/io/github/eslamasabry/oc/android/platform/AndroidPlatformRobolectricTest.kt:130)  
   **Problem/evidence:** `totalRamBytes >= 0L` passes if the implementation always returns zero. It also fails to distinguish physical RAM from `memoryClass`, despite that distinction controlling voice-pack eligibility.  
   **Concrete fix:** Configure a known `MemoryInfo.totalMem` different from the memory-class value and assert exact equality. Check unavailable-service fallback separately.

Assertion sensitivity checked by inspection:

- `NativeCrashStoreTest.kt:102`: reading the hostile exception’s message or `toString()` fails the `touched` assertion.
- `NetworkTrackerTest.kt:46`: removing the old-network `onLost` guard fails the retained-mobile-state assertion.
- `AndroidPlatformRobolectricTest.kt:116`: ignoring the power-save broadcast fails the final `assertTrue`.

The adapters match the current `:core:domain` signatures. Composition with `core.net.gate.NetworkGate` remains blocked by its unrelated local `NetworkStatus` type; that mismatch predates this range and is already recorded in `docs/native/reviews/w1-core-net.md`, finding 11.