Verdict: **REQUEST CHANGES**. Read-only static review of `97f5db42..f5facaab`; no builds, tests, or mutations executed.

1. **BLOCKER — No production sherpa implementation exists.**  
   **File:** [SherpaRuntime.kt:9](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/speech/SherpaRuntime.kt:9)  
   **Problem/evidence:** Both implementations of `SherpaRuntime` are test fakes. Android STT and natural TTS cannot perform inference. The documented adapter location, `:android:media`, also cannot depend on these `:android:voice` types under `ModuleRules`; declaring sherpa directly here is forbidden by `ExternalRules`. Config DTO tests do not establish compatibility with the real AAR. This leaves plan 10 §6.3/§8.5 unimplemented.  
   **Fix:** Resolve the module boundary, supply the pinned AAR adapter and keep rules, and verify real recognizer/stream/TTS allocation and release.

2. **MAJOR — Failure to select an offline voice can send reply text to the previous network voice.**  
   **File:** [AndroidSystemTts.kt:104](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/tts/AndroidSystemTts.kt:104)  
   **Problem/evidence:** `tts.voice = it` discards `setVoice()`’s result, then `speak()` proceeds. If loading the offline voice fails, Android retains the previous voice parameters, potentially a network default. This violates §8.4’s offline-only privacy boundary. [AOSP updates the voice parameters only on success](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/speech/tts/TextToSpeech.java).  
   **Fix:** Call `setVoice()` explicitly and require `SUCCESS` before submitting any text. Test a network default plus rejected offline selection, asserting zero `speak()` calls.

3. **MAJOR — Cancelling Kokoro can deadlock the shared speech lane.**  
   **Files:** [KokoroSink.kt:15](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/speech/KokoroSink.kt:15), [SherpaSpeech.kt:99](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/speech/SherpaSpeech.kt:99)  
   **Problem/evidence:** After the 16-slot channel fills, `trySendBlocking()` blocks inside JNI’s callback. Cancellation stops the consumer but never cancels this channel. The generator cannot reach its `finally { chunks.close() }`, and structured concurrency waits for that generator forever. Stop, microphone startup, and subsequent inference can hang. [The API explicitly blocks while the channel is full](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/try-send-blocking.html).  
   **Fix:** Cancel the channel in the consumer’s `finally`, before waiting for generator termination. Test cancellation with a full channel and a suspended sink; the current N1 test continuously drains it.

4. **MAJOR — Cancelling TTS initialization permanently strands the engine.**  
   **File:** [AndroidSystemTts.kt:156](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/tts/AndroidSystemTts.kt:156)  
   **Problem/evidence:** Leaving or stopping while `ready.await()` suspends throws cancellation past both `initializing = false` and `created.shutdown()`. Subsequent requests remain busy, while `releaseIfIdle()` refuses cleanup because `initializing` remains true. The engine-level fake in the pending-playback test never exercises this platform initialization.  
   **Fix:** Reset initialization in `finally`; shut down an engine whose initialization did not transfer ownership, including cancellation. Verify cancel-during-init followed by a successful retry.

5. **MAJOR — Cancellation during native model loading leaks the allocated model.**  
   **File:** [SherpaSpeech.kt:35](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/speech/SherpaSpeech.kt:35)  
   **Problem/evidence:** `newRecognizer()` and `newTts()` return native resources through cancellable `withContext`. Cancel while JNI loads: allocation finishes, but dispatch back discards its result, so no session owns or releases it. Repeated startup cancellation can accumulate model allocations. This is the documented [closeable-resource pitfall of `withContext`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html).  
   **Fix:** Retain ownership outside the cancellable return boundary and release on unsuccessful handoff. Test with a blocking constructor that allocates before cancellation and returns afterward.

6. **MAJOR — Warm-session expiry can release a TTS session that a new reading reuses.**  
   **File:** [ReadAloud.kt:250](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/readaloud/ReadAloud.kt:250)  
   **Problem/evidence:** The timer leaves `warm` populated while suspending in `warm.close()`. A concurrent reading only cancels the timer, without joining it, then reuses `warm`. Android close runs non-cancellably, so synthesis can subsequently use a released native handle. Core’s app scope uses a multithreaded dispatcher; the single-thread test scheduler hides this overlap.  
   **Fix:** Serialize warm-session ownership and detach the expiring session before closing it. Test a new reading while expiry is suspended inside close.

7. **MAJOR — Ordinary file errors escape `Outcome` and permanently latch downloads busy.**  
   **Files:** [PackInstaller.kt:155](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/models/PackInstaller.kt:155), [ModelPacks.kt:193](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/models/ModelPacks.kt:193)  
   **Problem/evidence:** Installation catches only cancellation and `StorageFullException`. An `IOException` opening/writing/flushing a part escapes; `runInstall()` then skips clearing `running`. Later download/select/remove calls remain busy, including after `cancelDownload()`. Moreover, `AndroidModelFiles.openAppend()` constructs `FileOutputStream` outside `mapFull`, so creation-time ENOSPC follows this path. This contradicts B-MEDIA-013/026 and core’s typed-failure contract.  
   **Fix:** Translate expected filesystem exceptions and clear operation ownership in a guaranteed, identity-checked finalizer. Inject open/write/flush failures and verify another download can start. `AndroidPlatformTest:146` merely throws and catches `StorageFullException` itself; it does not test platform translation.

8. **MAJOR — Interrupted replacement has no restart recovery for the verified old model.**  
   **File:** [PackInstaller.kt:302](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/models/PackInstaller.kt:302)  
   **Problem/evidence:** Kill the process after renaming a target to `.voice-backup`, before moving its part into place. On restart, `installed()` sees the missing target and invalidates the marker. Nothing restores backups; rollback exists only in the live exception handler. The previously verified installation becomes unusable despite its bytes remaining on disk.  
   **Fix:** Recover interrupted transactions under the pack lock before readiness/install checks, using durable transaction state or a recoverable directory swap. Test restart snapshots at each publication boundary; the existing rollback test only injects an exception in the same process.

9. **MAJOR — Closing setup during a suspended prompt does not revoke the pending download.**  
   **File:** [VoiceSetup.kt:164](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/setup/VoiceSetup.kt:164)  
   **Problem/evidence:** Suspend `host.askNotifications()`, close the run, then complete the prompt. `close()` finds no `ownDownload`; afterward, `consent()` continues into `download()` without checking `closed`. Although state publication is suppressed, a model download still starts. Similarly, closing during suspended `startListening()` can miss cancellation because `listening` is set only afterward. This violates B-MEDIA-019/020/021.  
   **Fix:** Bind effects to a cancellable run/attempt identity and revalidate after suspensions. Test close during notification permission and microphone startup, rather than only after downloading has begun.

10. **MAJOR — Installed natural packs bypass the enforced RAM gate.**  
    **File:** [ModelPacks.kt:154](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/models/ModelPacks.kt:154)  
    **Problem/evidence:** `setNaturalVoice(true)` runs support checks only through the missing-pack download path. An already-installed Kokoro pack is enabled directly. `ReadAloud` also accepts an installed pack with no support check, including a migrated enabled preference. Consequently, a phone below 2000 MB can load Kokoro despite M5. The RAM test covers only an absent pack.  
    **Fix:** Enforce eligibility when enabling and resolving a natural pack for playback. Test preinstalled files and an adopted enabled preference below the threshold.

11. **MAJOR — A brief microphone silencing event discards audio after recording has recovered.**  
    **File:** [VoiceController.kt:254](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/dictation/VoiceController.kt:254)  
    **Problem/evidence:** The first `Silenced` event starts an unconditional two-second timer. Android reports only silenced configurations; there is no recovery event or timer reset when silencing clears. A 100 ms interruption therefore still causes `RecordingInterrupted` two seconds later and drops untranscribed speech. §5 A7 requires two seconds of **continuous** silencing.  
    **Fix:** Propagate both silenced and recovered states, cancelling the timer on recovery. Add silence → recovery → continued recording coverage.

12. **MAJOR — Refused preference writes still change live voice behavior.**  
    **File:** [ModelPacks.kt:137](/home/eslam/Storage/Code/wt/native-reviews/src-voice/native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/models/ModelPacks.kt:137)  
    **Problem/evidence:** Selection, language, auto-send, and natural-voice setters publish state before awaiting persistence and retain it on `Failure.StorageRefused`. For example, enabling auto-send can return failure while leaving `state.autoSend == true`; restart then restores the old value. Core’s `KeyValueStore` deliberately exposes only acknowledged writes, but this state layer bypasses that guarantee.  
    **Fix:** Serialize each change and publish after successful commit, or restore the previous state on refusal. Test a refusing prefs backend and restart.

Static test-sensitivity checks, **not executed mutations**: bypassing checksum rejection would break `PackInstallerTest:73`; accepting an incorrect Content-Range would break `PackInstallerTest:102`; continuing after a failed chunk would break `VoiceControllerTest:83`. These assertions do not cover the cancellation and recovery interleavings above.