# Unit S3-MEDIAATT: the attachment pipeline (`:engine:media` attach/office/xml/preview, `:android:media` image/clipboard)

Date: 2026-11-01. Branch: `native/mediaatt-2026-11-01`, created from `claude/native-mediaatt-pipeline-er41kh` at
`7db496b2`. Write set (brief): `native/engine/media/**` attach, office, xml and preview folders and
`native/android/media/**` image and clipboard folders (new modules), plus this report. Nothing outside it was edited.
The two module build files (`native/engine/media/build.gradle.kts`, `native/android/media/build.gradle.kts`) were
created because the modules are new; the media lead owns them from here (plan 10 §3.3).

Inputs read: `native/AGENTS.md`, `docs/native/units/INTEGRATE-W1.md` (the flat `Failure` contract), `S3-COMPE.md`,
plan 01/03 module rules (`ModuleRules.kt`), plan 10 §3, §4, §10 (MV-1a, MV-2), plan 15 row S3-MEDIAATT, the catalog rows
below, and the Flutter sources being ported: `lib/domain/{prompt_attachment,delimited_text,static_svg,office_text}.dart`,
`lib/ui/widgets/file_preview.dart` (`FilePreviewData`), `lib/ui/screens/chat/chat_attachments.dart`,
`android/app/src/main/kotlin/.../ClipboardImage{Bridge,Policy}.kt`, with their tests.

**Result.** The engine side of the attachment pipeline is implemented in pure common Kotlin and green on the desktop JVM
(60 tests); the Android image codec and clipboard reader are implemented and green under Robolectric (12 tests).

Shipping state: implemented, verified locally (module tests only), committed and pushed to the branch. **Not wired**
into any graph, composer screen or `ActivityResults` launcher; not merged; no PR, no CI, no signing, no release. The
plan-15 finish line ("+" tray, chips, photo recovery working, E2E-12 on the release build) is **not** reached: the tray,
chips and composer wiring are in `:feature:chat-composer` (outside this brief's write set) and the platform launchers are
`:android:platform` (S2-PLATFORM).

---

## 1. What was built

Package root `io.github.eslamasabry.oc.engine.media` (`explicitApi()`), Android root `io.github.eslamasabry.oc.android.media`.

| Area | Files | What it does |
|---|---|---|
| `xml/` | `XmlPull.kt` | Pull reader (plan 10 §10.7, M7): a DOCTYPE or any `<!…` declaration fails the read; only `lt gt amp apos quot` and numeric references; depth ≤ 64 and ≤ 2,000,000 elements (`XmlLimits.Office`, configurable); well-formedness (one root, matched tags, unique attributes); namespace resolution for elements and prefixed attributes. Failure is `XmlRefused(XmlProblem)`, never document text. |
| `office/` | `RawInflate.kt`, `ZipCentralDirectory.kt`, `OfficeText.kt` | Pure-Kotlin raw DEFLATE with an output cap (returns null on corrupt/short/bomb input; also the iOS path the plan names). ZIP read from the **central directory** (data-descriptor entries work), ≤ 2,000 entries, ≤ 64 MiB per entry, encrypted → refused, methods 0/8 only, only `.xml`/`.rels` parts inflated. `OfficeText.read(name, bytes): Outcome<OfficeText>` — workbook sheets as CSV under `# Sheet: <name>` (rels targets, `sheet<n>.xml` fallback, shared strings without phonetic runs, inline strings, booleans, far columns kept), Word paragraphs (`w:t`, `w:tab`, `w:br`); 400,000-char cut that never splits a surrogate pair; `OfficeUnreadable` / `OfficeEmpty`. |
| `preview/` | `StrictUtf8.kt`, `MimeMap.kt`, `Truncate.kt`, `DelimitedText.kt`, `StaticSvg.kt`, `FilePreview.kt` | Strict UTF-8 (no overlongs, surrogates, > U+10FFFF); `PreviewData` (port of `FilePreviewData`: text only when strict decode succeeds and no NUL, Save keeps the original bytes, `fromDataUrl` → `AttachmentContentMissing` / `RemoteAttachmentPreview` / `FileNotShowable`, `table()`, `svg()`); `DelimitedText` (CSV/TSV, bounds 256 KiB / 200 rows / 32 columns / 4,096 per field, explicit MIME beats the name); `StaticSvg` (element/attribute allowlists, no dash attributes, local gradient `url(#id)` only, no escapes/imports/comments in values, numeric bounds, ≤ 1,000 elements, depth ≤ 32, size from `viewBox` or width/height); `FilePreview` + `DefaultFilePreview`. |
| `attach/` | `AttachmentLimits.kt`, `DataUrl.kt`, `PromptAttachmentMime.kt` (+ `AttachmentName`), `BoundedRead.kt`, `MagicBytes.kt`, `Seams.kt`, `PhotoRecord.kt`, `AttachmentIntake.kt`, `PendingPhotoRecovery.kt` | Limits (5 files, 10 MiB each, 20 MiB total, clipboard 10 MiB, markup 9 MiB; byte length from base64 length and padding); the MIME gate (exact `text/plain` for every textual type only when the bytes look like text); bounded reads that ask for one byte past the limit and stop; picture sniffing; the platform seams (`ImageCodec`, `ClipboardImages`, `PickedFileReader`, `CaptureFiles`, `AttachmentWords`, `PhotoRules.Prompt`); `AttachmentIntake` (`pickPhotos`, `takePhoto`, `pickFile`, `fromBytes`, `fromClipboard`, `fromInserted`) and `PendingPhotoRecovery.recover(outcome, launchersSettled)`. |
| `:android:media` `image/` | `ImageScale.kt`, `AndroidImageCodec.kt` | Decode with memory caps: source read ≤ 64 MiB, header ≤ 200 MP (decompression bomb), API 28+ `ImageDecoder` with `setTargetSize` (long edge ≤ 2048, aspect kept, never upscaled, software allocator; EXIF applied by the decoder), API 26–27 `BitmapFactory` with power-of-two `inSampleSize` raised until the bitmap is ≤ 32 MiB, exact scale and EXIF rotation. Encode: alpha → PNG, else JPEG 85; a PNG over 10 MiB → JPEG on white; anything still over → `PhotoTooLarge`; GIF/WebP within 10 MiB and 2048 px kept byte for byte; undecodable → `PhotoUnsupported`; OOM → `PhotoTooLarge`. Failures carry only `Redacted.shape`. |
| `:android:media` `clipboard/` | `ClipboardImagePolicy.kt`, `AndroidClipboardImages.kt` | Port of `oc/clipboard`: `hasImage()` reads only the clip description (no Android 12+ toast); `read(maxBytes)` opens the first item's URI, first `image/*` type from the description or `ContentResolver.getType`, one-byte-past cap → `ClipboardImageTooLarge`; `SecurityException`/`IOException` → no picture. |

## 2. Catalog rows covered

Test names start with the row id. "Engine part" means the row also needs UI, the platform launchers or the app graph.

| Row | Status | Tests |
|---|---|---|
| B-MEDIA-001 | covered | `FilePreviewTest` (5) |
| B-MEDIA-006 | covered (fixtures copied byte for byte from `test/fixtures/office/`) | `OfficeTextTest` (8, desktop), `XmlPullTest` (4), `AttachmentIntakeTest` (Office as text) |
| B-MEDIA-007 | engine + Android part: scale before attach, room-limited multi pick, single picker for one slot (`PhotoPickerChoice`), cancel leaves nothing pending, clipboard/keyboard pictures. The launcher contracts themselves are `:android:platform` | `AttachmentIntakeTest`, `ImageScaleTest`, `AndroidImageCodecTest`, `ClipboardImagesTest` |
| B-MEDIA-008 | engine part: record before launch, failed record never launches, denial/cancel leave no blocking photo, pending blocks another picker, fake extension and oversize refused, late result vs removed profile, every §10.5 step-6 rule | `AttachmentIntakeTest`, `PendingPhotoRecoveryTest`, `AndroidImageCodecTest`, `ImageScaleTest` |
| B-CHAT-009 | covered | `PromptAttachmentMimeTest`, `AttachmentIntakeTest` |
| B-CHAT-010 | covered (exact limit, growing stream, stale metadata, oversized in-memory data, limits in order, read bounded by the room left) | `PromptAttachmentMimeTest`, `AttachmentIntakeTest` |
| B-CHAT-021, 022, 023, 024 | covered | `DelimitedTextTest` |
| B-CHAT-107 | covered (plus missing gradient reference and non-stop gradient child) | `StaticSvgTest` |
| B-STATE-088 | engine part: joins its own conversation's draft once, launcher/bootstrap race, bytes already in the vault, origin location kept. Against an in-memory store with the composer's rules, **not** the real `PhotoRecoveryStore` (§5.1) | `PendingPhotoRecoveryTest` |

Red-first evidence: the tests were written against the Flutter tests before the Kotlin was run; the first run was
green, so a mutation pass was made (gate removed in `looksLikeText`, `url()` check in `StaticSvg`, the overflow check in
`BoundedRead`, the cancel rule in `PendingPhotoRecovery`): 5 tests went red across 4 classes; the `BoundedRead` mutation
is also caught by the loop's second guard (same behaviour), so it stayed green. One real expectation bug was found in my
own test (`ImageScaleTest`, §5.3).

## 3. Commands and results

Environment: Android command-line tools `commandlinetools-linux-16111833_latest.zip` into
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `sdkmanager "platform-tools" "platforms;android-37.1"
"build-tools;37.0.0"`; `native/local.properties` (gitignored) `sdk.dir=/opt/android-sdk`; JDK 21 runs Gradle (JDK 17
toolchain via foojay). Container-only (not committed): `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Maven
Central mirror first for plugins and dependencies (Central answers HTTP 429 through the proxy), keeps Google and the
plugin portal, and sets `robolectric.dependency.repo.url`; `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:composer:compileKotlinDesktop :android:platform:compileDebugKotlin` (warm-up) | **FAILED** in `:engine:composer` (`Failure.Storage`/`StorageProblem` unresolved: S3-COMPE predates INTEGRATE-W1). Hence §5.1. |
| 2 | `./gradlew :engine:media:desktopTest` | first run: 60 tests, 0 failures |
| 3 | `./gradlew :engine:media:compileAndroidMain :android:media:testDebugUnitTest` | test compile failed (`androidx.test.core` not on the classpath, no catalog alias) → `RuntimeEnvironment.getApplication()`; then 12 tests, 1 failure (`ImageScaleTest` expectation, §5.3) → fixed |
| 4 | mutation pass on `:engine:media:desktopTest` (4 mutations, reverted) | 60 tests, 5 failed (as intended) |
| 5 | **Final:** `./gradlew --rerun-tasks :engine:media:desktopTest :engine:media:compileAndroidMain :engine:media:compileKotlinDesktop :android:media:testDebugUnitTest` | **BUILD SUCCESSFUL**; `:engine:media` 60 tests / 0 failed (AttachmentIntake 14, PendingPhotoRecovery 10, PromptAttachmentMime 9, OfficeText 8, DelimitedText 6, FilePreview 5, StaticSvg 4, XmlPull 4); `:android:media` 12 / 0 (ClipboardImages 4, AndroidImageCodec 4 with `@GraphicsMode(NATIVE)`, ImageScale 4) |
| 6 | `git diff 7db496b2 \| grep` for RFC 1918 / CGNAT addresses and e-mail addresses | no match (only `noreply` in commit trailers) |

Not run: `:quality:test` (needs the whole tree, which does not compile until INTEGRATE-W2; Konsist K4/K11 checked by
hand: no `Dispatchers.*`, `Clock.System`, `GlobalScope` or `runBlocking` in either module, tests included), `build`,
iOS klibs, any device, E2E-12. No formatter ran: `tool/format-changed.sh` does not exist in this tree.

Commits (each `[skip ci]`): xml + office; preview; attach; `:android:media`; this report. The preview commit uses
`attach/DataUrl` from the next commit, so it does not compile on its own; the branch tip does.

## 4. Library and versions

No catalog change. Used: the `oc.kmp.library` convention set (coroutines, collections-immutable, serialization),
`androidx-core-ktx`, `robolectric` 4.17, `junit4`, `kotlinx-coroutines-test`. EXIF on API 26–27 uses the framework's
`android.media.ExifInterface` (enough for JPEG orientation), not androidx ExifInterface 1.4.2 (§6).

## 5. Deviations and decisions

### 5.1 `:engine:composer` is reached through a port

Plan 10 §10.5 has recovery call the composer's record and draft store. `:engine:composer` does not compile on this tree
(row 1), and the brief says to depend only on modules that compile. So `:engine:media` does **not** depend on it;
`attach/PhotoRecord.kt` defines `PhotoRecord` (pending, begin, complete, markFailed, discard → `Outcome` with
`Failure.Media`) and `DraftPhotoJoin` (`join(): RecoveryResult`). The tests use an in-memory store with the composer's
rules (`FakePhotoStore`). The adapter, once composer compiles (INTEGRATE-W2), is:

```kotlin
class ComposerPhotoRecord(private val store: PhotoRecoveryStore) : PhotoRecord {
    override val pending get() = store.pending?.let { PendingPhotoInfo(it.id, it.profileId, it.sessionId, it.ref != null, it.path, it.name, it.failed) }
    override suspend fun begin(...) = store.begin(...).toOutcome { it.id }
    override suspend fun complete(id, name, bytes) = store.complete(id, name, bytes).toOutcome { it != null }
    override suspend fun markFailed(id) = store.markFailed(id).toOutcome { }
    override suspend fun discard(id) = store.discard(id).toOutcome { }
}
// PhotoResult.Refused(p) → Failure.Media(Pending→PhotoPending, Storage|SensitiveIdentity→PhotoStorage, TooLarge→PhotoTooLarge,
// Unsupported→PhotoUnsupported, Unavailable→PhotoUnavailable); DraftPhotoJoin { DraftPhotoRecovery.recover() mapped 1:1 }
```

### 5.2 Contract shapes that differ from plan 10 §4

1. **Core primitives and types are missing**, so local equivalents exist: `RawInflate` (pure Kotlin, internal, same
   contract as C10-2), `AttachmentLimits` (plan 03 §10.4 wants it in `:core:domain`), `PickedFileReader` (instead of
   `ContentAccess.readBounded`). Core's `PickedFile(uri, displayName, mime, sizeBytes)` and `PhotoTarget(uri)` are used
   as they are (plan 10's `handle`/`captureId` names differ).
2. **`AttachmentIntake` is a class, not an interface + impl**, with an `AttachTarget(conversation, directory,
   workspace)` instead of a bare `ConversationRef` (the record needs the draft location, B-STATE-088), and `current:
   List<PromptAttachment>` instead of `DraftAttachments` (not defined anywhere yet). `pickFile` and the clipboard paths
   take no target (they never touch the record).
3. **`fromKeyboard(InsertedContent)` is `fromInserted(mime, bytes)`**: `InsertedContent`/`ContentAccess.readInserted`
   are not in core; the feature reads through the platform and passes bytes.
4. **`PreviewData` lives in `preview/`**, not `state/` (`state/` is outside this write set). Media lead: move it.
5. **Copy stays out of the engine**: the Office header and truncation lines are attachment *content*, so
   `AttachmentWords` (supplied from `:ui:words`) provides them; composer notes are `IntakeNote` values.
6. `PendingPhotoRecovery.recover` takes `launchersSettled` (the "host reached ON_START once" fact of §10.5 step 6)
   instead of observing the lifecycle itself.
7. Zero sheets in a workbook is `OfficeEmpty` (plan) rather than Flutter's FormatException → unreadable.

### 5.3 Behaviour notes

1. Plan §10.3 says a camera/picked photo with no file **and** `Cancelled` or no outcome is dropped silently; it also says
   "no ref, no path, no outcome → wait until ON_START, then `PhotoUnavailable` once". The second rule wins when no
   outcome arrived (the record's `path` is never written by the composer's `begin`); an empty `Picked` list is a cancel.
2. `ImageScale.MAX_DECODE_BYTES` = 32 MiB applies only to the API 26–27 `BitmapFactory` path: an 8000×6000 JPEG decodes
   at sample 4 (2000×1500) there instead of a 48 MB bitmap at sample 2; API 28+ decodes straight to 2048 px. My first
   test expected sample 2; the rule is the cap.
3. `OfficeText` handles nested `w:p` (text boxes) as one paragraph instead of Flutter's duplicated text.
4. CRCs inside ZIP entries are not checked (Flutter did not either); the inflate cap bounds the work.

## 6. Catalog requests

- `androidx-exifinterface` 1.4.2 (plan 10 §10.3) if the media lead wants the androidx reader on API 26–27 (HEIF/WebP
  orientation); the framework one is used now.
- `androidx-test-core` library alias (Robolectric tests used `RuntimeEnvironment` instead).

## 7. Requests to other owners

- **Coordinator:** `MODULE_OWNERS` rows `:engine:media	S	media-lead	slice-3` and `:android:media	S	media-lead	slice-3`
  (without them `ModuleOwnersTest` in `:quality` fails once the tree compiles).
- **Core owner (C10-1/C10-2/C10-4):** `RawInflate`, `Sha256`, `secureRandomBytes` in `:core:common`;
  `InsertedContent`/`PlatformContentHandle`, `ContentAccess`, `AttachmentLimits` in `:core:domain`/`:core:common`. When they
  land, delete `office/RawInflate.kt`, `attach/AttachmentLimits.kt` and `PickedFileReader`.
- **S3-COMPE / INTEGRATE-W2:** make `:engine:composer` compile on the flat `Failure`, then add the §5.1 adapter (in
  `:engine:media` or the app graph) and run `PendingPhotoRecoveryTest`'s cases against the real store. Also: composer's
  `PhotoSniff` duplicates `MagicBytes`; one of them should go.
- **S2-PLATFORM (`:android:platform`):** `ActivityResults.pickImages(limit)` must use `PhotoPickerChoice.forRoom(limit)`
  (single `PickVisualMedia` for 1); fixed launcher keys `oc.takePhoto`/`oc.pickPhoto`/`oc.pickPhotos` that report
  `LauncherOutcome` to `PendingPhotoRecovery`; an `AndroidContentAccess` implementing `PickedFileReader` (limit + 1 read);
  `CaptureFiles` over `files/camera-capture-v1/` and the FileProvider root of C10-14.
- **`:feature:chat-composer` (attachment files, S3-MEDIAATT's own row but outside this brief):** the "+" tray, chips,
  paste row and pending-photo row on top of `AttachmentIntake`, words for every `IntakeNote` and the `MediaProblem`
  values used here.
- **Media lead:** `MediaBindings.kt` (`@ContributesBinding` for `AndroidImageCodec`, `AndroidClipboardImages`) and the
  desktop `Unavailable*` bindings (`UnavailableClipboardImages` exists).

## 8. Open problems

1. Finish line not reached: no tray, chips, launcher wiring or E2E-12 (§7).
2. B-STATE-088 is proven against a fake of the composer store, not the store itself (§5.1).
3. `ImageDecoder` under Robolectric prints "Failed to create image decoder … unimplemented" for invalid input; the codec
   maps it to `PhotoUnsupported` as intended, but real-device decoding of HEIC, animated WebP and EXIF-rotated JPEGs is
   unverified (needs an instrumented test or a device).
4. Desktop drop (MEDIA-VOICE-31), the markup raster and the Coil image loader are later units.
