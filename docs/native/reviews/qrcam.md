**Verdict: REQUEST CHANGES — four MAJOR defects and two MINOR findings.**

Reviewed `f1a058b0..f1d06290` read-only. No builds or tests were executed. The committed Gradle configuration excludes `src/camerax` because its required catalog aliases are absent; the host tests therefore do not compile or exercise those adapters. No direct `native/core` signature mismatch was identified in the included sources.

- **MAJOR — A stale frame permanently disables scanning.** [ScanSession.kt:81](/home/eslam/Storage/Code/wt/native-reviews/src-qrcam/native/android/camera/src/main/kotlin/io/github/eslamasabry/oc/android/camera/scan/ScanSession.kt:81)  
  **Problem/evidence:** If `end()` runs during decoding or parsing, `compareAndSet(false, true)` succeeds before `isCurrent()` rejects the stale attempt. The method returns `Ignored` with `latched == true`; subsequent `CameraXScanner.start()` calls return immediately. This violates plan 10 §14.2’s pause/resume behavior. The stale-frame test ends the session *before* analysis, missing this interleaving.  
  **Fix:** Commit attempt validation and latching through one synchronized state transition. Add a deterministic regression whose injected parser calls `end()` before returning a valid value, then verify a fresh attempt can accept.

- **MAJOR — Disposing the scanner cannot detach its lifecycle observer.** [CameraXScanner.kt:68](/home/eslam/Storage/Code/wt/native-reviews/src-qrcam/native/android/camera/src/camerax/kotlin/io/github/eslamasabry/oc/android/camera/camerax/CameraXScanner.kt:68)  
  **Problem/evidence:** The observer is registered permanently; `close()` is private, and neither `stop()` nor `close()` removes it. With an activity-scoped owner, leaving the scanner via Back or Paste and calling `stop()` still allows the next activity resume to restart the hidden scanner. Its callbacks and executor also remain retained. Plan 10 §14.2 explicitly requires disposal to stop scanning.  
  **Fix:** Expose an idempotent disposal operation that removes the observer, invalidates pending callbacks, unbinds owned use cases and shuts down the executor. Reject subsequent starts.

- **MAJOR — Stopping an old camera owner closes another owner’s camera.** [CameraXBinding.kt:52](/home/eslam/Storage/Code/wt/native-reviews/src-qrcam/native/android/camera/src/camerax/kotlin/io/github/eslamasabry/oc/android/camera/camerax/CameraXBinding.kt:52)  
  **Problem/evidence:** Both binding and unbinding call process-wide `unbindAll()`. Sequence: scanner A pauses, scanner/capturer B binds, then A is destroyed and calls `stop()` again. A closes B’s camera, potentially aborting B’s photo capture. The provider is a process singleton, and `unbindAll()` closes every open camera. [AndroidX source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/camera/camera-lifecycle/src/main/java/androidx/camera/lifecycle/ProcessCameraProvider.kt).  
  **Fix:** Track and unbind only this binding’s `Preview` and supplied use cases; clear ownership after release.

- **MAJOR — The decoding region extends outside the visible scan window.** [ScanWindow.kt:31](/home/eslam/Storage/Code/wt/native-reviews/src-qrcam/native/android/camera/src/main/kotlin/io/github/eslamasabry/oc/android/camera/scan/ScanWindow.kt:31)  
  **Problem/evidence:** Dividing by the preview’s short side ignores `FILL_CENTER` cropping. For a rotated 720×1280 buffer filling a 1080×2400 view, a 540-pixel window produces a 360-pixel buffer crop, displayed as **675 pixels**. An unintended pairing QR outside the brackets can therefore latch. The 0.3 minimum enlarges the discrepancy on tablets. This violates “decode only what the person aims at.”  
  **Fix:** Map the actual window rectangle through the preview-to-analysis transform, including crop and rotation; remove the arbitrary minimum expansion. [CameraX coordinate guidance](https://developer.android.com/media/camera/camerax/transform-output).

- **MINOR — The new module violates the ownership gate.** [build.gradle.kts:7](/home/eslam/Storage/Code/wt/native-reviews/src-qrcam/native/android/camera/build.gradle.kts:7)  
  **Problem/evidence:** Settings automatically discovers `:android:camera`, but the reviewed revision has no corresponding `MODULE_OWNERS` row. `ModuleOwnersTest` requires exact equality, so this addition introduces another gate failure.  
  **Fix:** Register the module and owner in the same integration change.

- **MINOR — The cancellation test does not test capture cancellation.** [CaptureFilesTest.kt:45](/home/eslam/Storage/Code/wt/native-reviews/src-qrcam/native/android/camera/src/test/kotlin/io/github/eslamasabry/oc/android/camera/capture/CaptureFilesTest.kt:45)  
  **Problem/evidence:** The test writes a file and directly calls `discard()`. Removing every cancellation/error cleanup from `CameraXPhotoCapturer` leaves it green, despite its cancellation/failure claim.  
  **Fix:** Rename it as a discard-helper test and add capture-boundary tests covering cancellation during writing, delayed callbacks and capture failure.

Three assertion checks were inspected counterfactually: `ScanSessionTest:52` fails if release/notification order reverses; `CameraAccessFlowTest:76` fails if denial returns `Starting`; `ScanWindowTest:36` fails if its supplied crop becomes the full frame. These checks do not exercise the production CameraX adapter.

