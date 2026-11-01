Verdict: REQUEST CHANGES — 7 MAJOR, 3 MINOR.

1. **MAJOR — Photo recovery is cleared before the draft commits.** [AttachmentIntake.kt:139](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/attach/AttachmentIntake.kt:139)

   **Problem/evidence:** `takePhoto` returns attachments through a `finally` that deletes the capture and pending record. The caller cannot persist the draft until after this cleanup. A crash or failed draft save loses the photo permanently. Gallery intake similarly discards its record at line 114. Neither path calls `photos.complete`. This breaks plan 10 §10.4–10.5 and B-MEDIA-008.
   
   **Fix:** Persist prepared bytes with their recorded destination, and clear recovery state only after acknowledged draft persistence. Coroutine cancellation must transfer ownership to recovery rather than unconditionally deleting it.

2. **MAJOR — Recoverable read failures permanently delete captures.** [PendingPhotoRecovery.kt:75](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/attach/PendingPhotoRecovery.kt:75)

   **Problem/evidence:** Every codec failure calls `unavailable`, which deletes the capture before marking and discarding the record. `AndroidImageCodec.preparePhoto` returns failures for `IOException` and `SecurityException`, so a temporary provider/storage failure destroys an otherwise recoverable photo. Plan 10 §10.5 step 8 requires storage failures to retain recovery state.
   
   **Fix:** Distinguish permanent format/size failures from retryable access/storage failures. Return `RetryRequired` without deleting the source for retryable failures.

3. **MAJOR — Office ZIP limits still permit a memory-exhaustion archive.** [ZipCentralDirectory.kt:67](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/office/ZipCentralDirectory.kt:67)

   **Problem/evidence:** Every `.xml`/`.rels` entry is inflated and retained in `files`; limits apply individually, with no cumulative budget. An in-memory fixture produced a **419,471-byte archive containing 400 MiB of XML**, passing the 10 MiB input, 2,000-entry and 64 MiB-per-entry guards. Unreferenced parts consume memory before document parsing starts.
   
   **Fix:** Enforce an archive-wide inflated-byte budget during inflation and load only referenced parts on demand. Add an aggregate-bomb regression case.

4. **MAJOR — OOM handling excludes source buffering and encoding.** [AndroidImageCodec.kt:46](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/android/media/src/main/kotlin/io/github/eslamasabry/oc/android/media/image/AndroidImageCodec.kt:46)

   **Problem/evidence:** Only `decode` is inside the `OutOfMemoryError` handler. Source buffering can retain a 64 MiB backing array plus its `toByteArray` copy; PNG/JPEG output buffering and the white fallback bitmap also allocate outside that handler. Memory pressure in these stages escapes the `Outcome` contract instead of returning `PhotoTooLarge`.
   
   **Fix:** Cover the complete preparation pipeline with failure translation and reliable bitmap cleanup. Decode ordinary photos directly from the URI as planned, and bound output allocation.

5. **MAJOR — Preview truncation is never applied; its test misses this.** [FilePreview.kt:72](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/preview/FilePreview.kt:72)

   **Problem/evidence:** `DefaultFilePreview.prepare` returns all decoded text with `truncated=false`. Nothing calls `Truncate.cap`, violating plan 10 §11.2’s 200,000-character limit. [FilePreviewTest.kt:59](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/media/preview/FilePreviewTest.kt:59) passes `"x"` through the service, then independently tests the helper; it passes despite the missing production behavior.
   
   **Fix:** Apply truncation in preview preparation, preserve complete originals for Copy/Save, and pass oversized text—including a boundary surrogate pair—through `prepare` in the test.

6. **MAJOR — Missing worksheets silently produce incomplete attachments.** [OfficeText.kt:81](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/office/OfficeText.kt:81)

   **Problem/evidence:** A missing worksheet executes `continue`. A workbook declaring two sheets but containing only one returns `Outcome.Ok` with partial contents and `sections=2`; intake then attaches that text without warning. This contradicts B-MEDIA-006’s refusal of half-read files and plan 10 §10.7’s damaged-file behavior.
   
   **Fix:** Refuse unresolved/missing worksheet parts as `OfficeUnreadable`. Test a workbook whose second referenced sheet is absent.

7. **MAJOR — The SVG validation result is forgeable.** [StaticSvg.kt:13](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/preview/StaticSvg.kt:13)

   **Problem/evidence:** `StaticSvg` is a public data class. Callers can construct it with unsafe source or call `validated.copy(source = unsafeSvg)`, bypassing every validator check. It cannot serve as the renderer’s trusted input required by plan 10 §11.4 and invariant S5.
   
   **Fix:** Return an immutable `SanitizedSvg` with inaccessible construction and no public source-replacing `copy`; only successful validation may create it.

8. **MINOR — The recovery race test does not exercise concurrency.** [PendingPhotoRecoveryTest.kt:46](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/media/attach/PendingPhotoRecoveryTest.kt:46)

   **Problem/evidence:** Both `async` calls run on the single test scheduler, and every invoked fake completes without suspension. The first recovery finishes before the second starts; removing the mutex would leave these assertions passing.
   
   **Fix:** Introduce controlled suspension barriers in codec/storage operations, start the competing recovery while the first is suspended, and assert that it cannot enter the protected operation.

9. **MINOR — Generic MIME prevents extension-based preview selection.** [FilePreview.kt:24](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/preview/FilePreview.kt:24)

   **Problem/evidence:** `application/octet-stream` survives normalization, so `photo.png` is not a raster image and `notes.txt` has no decoded text when supplied with that common generic MIME. Plan 10 §11.2 explicitly requires extension fallback for generic types.
   
   **Fix:** Treat generic MIME as unspecified before extension resolution; test generic-MIME image, PDF and text inputs.

10. **MINOR — API 26–27 drops mirrored EXIF orientations.** [AndroidImageCodec.kt:119](/home/eslam/Storage/Code/wt/native-reviews/src-mediaatt/native/android/media/src/main/kotlin/io/github/eslamasabry/oc/android/media/image/AndroidImageCodec.kt:119)

    **Problem/evidence:** Only the three rotation orientations are handled. Flip-horizontal, flip-vertical, transpose and transverse fall through to zero rotation, producing incorrectly oriented attachments. Codec tests run only on SDK 34.
    
    **Fix:** Apply all eight EXIF orientation transforms and verify pixel placement with asymmetric fixtures on SDK 26/27.

Static counterfactual checks of three existing tests:

- `FilePreviewTest`’s invalid UTF-8 assertion fails if decoding substitutes replacement characters.
- `PromptAttachmentMimeTest`’s `declared.txt` assertion fails if binary-content validation is removed.
- `StaticSvgTest`’s external paint assertion fails if the `url(...)` guard is removed.

These were assertion/control-flow checks, not executed mutations. Review was read-only; no Gradle or device tests ran. Current core signatures were checked statically; compilation remains unverified.